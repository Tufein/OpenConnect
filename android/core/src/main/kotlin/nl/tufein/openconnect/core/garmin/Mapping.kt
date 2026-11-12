package nl.tufein.openconnect.core.garmin

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import nl.tufein.openconnect.core.DailySummary
import nl.tufein.openconnect.core.SleepSummary
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Turns the answers of Garmin's Personal API into the app's own day summaries.
 *
 * Garmin does not publish the response layouts. The ones the names below come from were observed with a real
 * account on 2026-11-12 (see docs/GARMIN.md), but nothing here depends on how an answer is nested: every object that
 * carries a calendar date (`calendarDate`, or `date`) claims all the plain values below it, and the values are then
 * picked by their own name or by `parent.name` (`heartRate.restingValue`, `steps.value`). A layout that differs from
 * the expected one then gives fewer values instead of a crash, and [JsonShape] tells what the layout was.
 */
internal object DayLeaves {
    private val DATE = Regex("""\d{4}-\d{2}-\d{2}""")

    /** All plain values per calendar day, keyed by their own name and by `parent.name`. */
    fun collect(root: JsonElement?): Map<LocalDate, DayFacts> {
        val days = LinkedHashMap<LocalDate, MutableMap<String, JsonPrimitive>>()

        fun walk(element: JsonElement, inherited: LocalDate?, parentKey: String?) {
            when (element) {
                is JsonObject -> {
                    val date = dateOf(element) ?: inherited
                    for ((key, value) in element) {
                        when (value) {
                            is JsonPrimitive -> if (date != null && value !is JsonNull) {
                                val leaves = days.getOrPut(date) { LinkedHashMap() }
                                leaves.putIfAbsent(key, value)
                                if (parentKey != null) leaves.putIfAbsent("$parentKey.$key", value)
                            }
                            else -> walk(value, date, key)
                        }
                    }
                }
                is JsonArray -> element.forEach { if (it !is JsonPrimitive) walk(it, inherited, parentKey) }
                else -> Unit
            }
        }
        if (root != null) walk(root, null, null)
        return days.mapValues { DayFacts(it.value) }
    }

    /** The day an object is about: its own date, else the date of the one nested object that has one. */
    private fun dateOf(obj: JsonObject): LocalDate? {
        ownDate(obj)?.let { return it }
        return obj.values.filterIsInstance<JsonObject>().firstNotNullOfOrNull { ownDate(it) }
    }

    private fun ownDate(obj: JsonObject): LocalDate? {
        for (key in listOf("calendarDate", "date")) {
            val text = (obj[key] as? JsonPrimitive)?.contentOrNull ?: continue
            val match = DATE.find(text)?.takeIf { it.range.first == 0 } ?: continue
            return try {
                LocalDate.parse(match.value)
            } catch (_: Exception) {
                null
            }
        }
        return null
    }

    /**
     * Body Battery comes as a list of readings per day, each reading `[time, level]`. The day says which position
     * holds the level (`bodyBatteryValueDescriptorDTOList`); without that, the level is the first number after the
     * time that lies between 0 and 100 (this also covers readings with a status text in between). Unmeasured moments
     * are null.
     */
    fun bodyBattery(root: JsonElement?): Map<LocalDate, Pair<Int, Int>> {
        val result = LinkedHashMap<LocalDate, Pair<Int, Int>>()

        fun level(value: JsonElement?): Int? =
            (value as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull?.takeIf { it in 0.0..100.0 }?.roundToInt()

        fun visit(element: JsonElement) {
            when (element) {
                is JsonArray -> element.forEach { if (it !is JsonPrimitive) visit(it) }
                is JsonObject -> {
                    val date = ownDate(element)
                    val readings = element["bodyBatteryValuesArray"] as? JsonArray
                    if (date != null && readings != null) {
                        val position = levelPosition(element)
                        val levels = readings.mapNotNull { reading ->
                            val values = reading as? JsonArray ?: return@mapNotNull null
                            if (position != null) level(values.getOrNull(position)) else values.drop(1).firstNotNullOfOrNull(::level)
                        }
                        if (levels.isNotEmpty()) result[date] = levels.max() to levels.min()
                    } else {
                        element.values.forEach { if (it !is JsonPrimitive) visit(it) }
                    }
                }
                else -> Unit
            }
        }
        if (root != null) visit(root)
        return result
    }

    private fun levelPosition(day: JsonObject): Int? =
        (day["bodyBatteryValueDescriptorDTOList"] as? JsonArray)?.firstNotNullOfOrNull { entry ->
            val descriptor = entry as? JsonObject ?: return@firstNotNullOfOrNull null
            if ((descriptor["bodyBatteryValueDescriptorKey"] as? JsonPrimitive)?.contentOrNull != "bodyBatteryLevel") return@firstNotNullOfOrNull null
            (descriptor["bodyBatteryValueDescriptorIndex"] as? JsonPrimitive)?.intOrNull
        }
}

/** The plain values of one calendar day. */
internal class DayFacts(private val leaves: Map<String, JsonPrimitive>) {
    fun number(vararg keys: String): Double? = keys.firstNotNullOfOrNull { leaves[it]?.doubleOrNull }

    fun int(vararg keys: String): Int? = number(*keys)?.roundToInt()

    /** A value counted from zero: negative numbers are Garmin's "no reading" markers. */
    fun reading(vararg keys: String): Int? = int(*keys)?.takeIf { it >= 0 }
}

/** What one day's answers say; everything is optional because each endpoint knows only part of it. */
internal data class DailyPatch(
    val steps: Int? = null,
    val stepsGoal: Int? = null,
    val distanceM: Double? = null,
    val activeKcal: Int? = null,
    val floors: Int? = null,
    val moderateMin: Int? = null,
    val vigorousMin: Int? = null,
    val restingHr: Int? = null,
    val minHr: Int? = null,
    val maxHr: Int? = null,
    val avgStress: Int? = null,
    val maxStress: Int? = null,
    val bodyBatteryHigh: Int? = null,
    val bodyBatteryLow: Int? = null,
    val hrvMs: Int? = null,
    val spo2Avg: Int? = null,
    val spo2Min: Int? = null,
    val respiration: Double? = null,
) {
    /**
     * True when there is nothing worth storing: no values, or only zero counters and a step goal. Garmin answers for
     * today and for days the watch was not worn with all counters at zero, and those are not worth a row.
     */
    val isEmpty: Boolean
        get() = copy(
            steps = steps?.takeIf { it > 0 },
            distanceM = distanceM?.takeIf { it > 0 },
            activeKcal = activeKcal?.takeIf { it > 0 },
            floors = floors?.takeIf { it > 0 },
            moderateMin = moderateMin?.takeIf { it > 0 },
            vigorousMin = vigorousMin?.takeIf { it > 0 },
            stepsGoal = null,
        ) == DailyPatch()

    /** Combine two patches about the same day: counters take the larger, other values the first one that is known. */
    fun and(other: DailyPatch) = DailyPatch(
        steps = maxOf(steps, other.steps),
        stepsGoal = stepsGoal ?: other.stepsGoal,
        distanceM = maxOf(distanceM, other.distanceM),
        activeKcal = maxOf(activeKcal, other.activeKcal),
        floors = maxOf(floors, other.floors),
        moderateMin = maxOf(moderateMin, other.moderateMin),
        vigorousMin = maxOf(vigorousMin, other.vigorousMin),
        restingHr = restingHr ?: other.restingHr,
        minHr = minHr ?: other.minHr,
        maxHr = maxHr ?: other.maxHr,
        avgStress = avgStress ?: other.avgStress,
        maxStress = maxStress ?: other.maxStress,
        bodyBatteryHigh = bodyBatteryHigh ?: other.bodyBatteryHigh,
        bodyBatteryLow = bodyBatteryLow ?: other.bodyBatteryLow,
        hrvMs = hrvMs ?: other.hrvMs,
        spo2Avg = spo2Avg ?: other.spo2Avg,
        spo2Min = spo2Min ?: other.spo2Min,
        respiration = respiration ?: other.respiration,
    )

    /** Put this patch onto what the app already knows about the day (from the watch, for instance). */
    fun applyTo(date: LocalDate, existing: DailySummary?): DailySummary {
        val base = existing ?: DailySummary(date)
        return base.copy(
            steps = max(base.steps, steps ?: 0),
            stepsGoal = stepsGoal ?: base.stepsGoal,
            distanceM = max(base.distanceM, distanceM ?: 0.0),
            activeKcal = max(base.activeKcal, activeKcal ?: 0),
            floorsClimbed = max(base.floorsClimbed, floors ?: 0),
            moderateIntensityMin = max(base.moderateIntensityMin, moderateMin ?: 0),
            vigorousIntensityMin = max(base.vigorousIntensityMin, vigorousMin ?: 0),
            restingHr = restingHr ?: base.restingHr,
            minHr = minHr ?: base.minHr,
            maxHr = maxHr ?: base.maxHr,
            avgStress = avgStress ?: base.avgStress,
            maxStress = maxStress ?: base.maxStress,
            bodyBatteryHigh = bodyBatteryHigh ?: base.bodyBatteryHigh,
            bodyBatteryLow = bodyBatteryLow ?: base.bodyBatteryLow,
            hrvMs = hrvMs ?: base.hrvMs,
            spo2Avg = spo2Avg ?: base.spo2Avg,
            spo2Min = spo2Min ?: base.spo2Min,
            respirationAvg = respiration ?: base.respirationAvg,
        )
    }

    private fun <T : Comparable<T>> maxOf(a: T?, b: T?): T? = when {
        a == null -> b
        b == null -> a
        else -> if (a >= b) a else b
    }

    companion object {
        /**
         * The names are tried in order: first the ones of the day summary (`movement.steps.value` arrives as
         * `steps.value`), then the ones of the separate step, stress and HRV lists.
         */
        fun from(facts: DayFacts) = DailyPatch(
            steps = facts.reading("steps.value", "totalSteps", "steps"),
            stepsGoal = facts.reading("steps.goal", "stepGoal", "dailyStepGoal", "totalStepGoal")?.takeIf { it > 0 },
            distanceM = facts.number("steps.distanceInMeters", "totalDistance", "totalDistanceMeters")?.takeIf { it >= 0 },
            activeKcal = facts.reading("calories.burnedActive", "activeKilocalories"),
            floors = facts.reading("floorsAscended.value", "floorsAscended", "floorsClimbed"),
            moderateMin = facts.reading("intensityMinutes.moderate", "moderateIntensityMinutes"),
            vigorousMin = facts.reading("intensityMinutes.vigorous", "vigorousIntensityMinutes"),
            restingHr = facts.reading("heartRate.restingValue", "restingHeartRate")?.takeIf { it > 0 },
            minHr = facts.reading("heartRate.minValue", "minHeartRate")?.takeIf { it > 0 },
            maxHr = facts.reading("heartRate.maxValue", "maxHeartRate")?.takeIf { it > 0 },
            avgStress = facts.reading("stress.avgLevel", "overallStressLevel", "averageStressLevel"),
            maxStress = facts.reading("stress.maxLevel", "maxStressLevel"),
            bodyBatteryHigh = facts.reading("bodyBattery.maxValue", "bodyBatteryHighestValue"),
            bodyBatteryLow = facts.reading("bodyBattery.minValue", "bodyBatteryLowestValue"),
            hrvMs = facts.reading("lastNightAvg", "avgOvernightHrv"),
            spo2Avg = facts.reading("pulseOx.avgValue", "averageSpO2", "averageSpo2", "avgSpo2"),
            spo2Min = facts.reading("pulseOx.minValue", "lowestSpO2", "lowestSpo2"),
            respiration = facts.number("respiration.avgValue", "avgWakingRespirationValue")?.takeIf { it > 0 },
        )
    }
}

internal object SleepMapping {
    /** A night, if the day's values hold any sleep: time asleep, the stages, and the score when there is one. */
    fun night(date: LocalDate, facts: DayFacts): SleepSummary? {
        val deep = facts.reading("deepSleepSeconds")
        val light = facts.reading("lightSleepSeconds")
        val rem = facts.reading("remSleepSeconds")
        val staged = listOfNotNull(deep, light, rem).sum()
        val asleep = facts.reading("sleepTimeSeconds") ?: staged
        if (asleep <= 0) return null
        return SleepSummary(
            date = date,
            durationS = asleep,
            deepS = deep ?: 0,
            lightS = light ?: 0,
            remS = rem ?: 0,
            awakeS = facts.reading("awakeSleepSeconds") ?: 0,
            score = facts.reading("overall.value", "overallScore", "sleepScore")?.takeIf { it in 1..100 },
        )
    }
}

/**
 * Describes the layout of a JSON answer without any of its values, so it can be shown or shared safely when the
 * answer was not what the app expected: `{calendarDate: string, values: {overallStressLevel: number}} x 28`.
 */
object JsonShape {
    fun describe(element: JsonElement?, depth: Int = 5): String = when (element) {
        null, JsonNull -> "empty"
        is JsonPrimitive -> when {
            element.isString -> "string"
            element.contentOrNull == "true" || element.contentOrNull == "false" -> "boolean"
            else -> "number"
        }
        is JsonArray -> when {
            element.isEmpty() -> "[]"
            depth <= 0 -> "[...]"
            else -> "${describe(element.first(), depth - 1)} x ${element.size}"
        }
        is JsonObject -> when {
            depth <= 0 -> "{...}"
            else -> element.entries.take(30).joinToString(", ", "{", "}") { (k, v) -> "$k: ${describe(v, depth - 1)}" } +
                if (element.size > 30) " (+${element.size - 30} more)" else ""
        }
    }
}
