package nl.tufein.openconnect.core.garmin

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import nl.tufein.openconnect.core.Activity
import nl.tufein.openconnect.core.Repository
import nl.tufein.openconnect.core.SleepSummary
import nl.tufein.openconnect.core.Sport
import nl.tufein.openconnect.core.formats.FormatError
import nl.tufein.openconnect.core.formats.Formats
import nl.tufein.openconnect.core.formats.sportFromText
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlin.math.roundToInt

data class GarminProgress(val done: Int, val total: Int, val label: String)

enum class EndpointStatus {
    OK,

    /** Garmin answered, there was just nothing in this period. */
    EMPTY,

    /** Garmin answered with data the app could not make sense of; [EndpointResult.detail] has the layout. */
    UNRECOGNISED,
    FAILED,
}

data class EndpointResult(val id: String, val status: EndpointStatus, val records: Int, val detail: String? = null)

data class GarminSyncReport(
    val days: Int,
    val endpoints: List<EndpointResult>,
    val daysUpdated: Int,
    val nightsUpdated: Int,
    val activitiesAdded: Int,
    val activitiesKnown: Int,
    /** Added from the summary only, because the activity file could not be downloaded or read. */
    val activitiesWithoutFile: Int,
    /** Not fetched this time (the limit per sync); syncing again continues. */
    val activitiesPending: Int,
    /** Set when something made the sync stop early: a rejected key, no connection, a rate limit. */
    val stopped: GarminApiException? = null,
    val cancelled: Boolean = false,
) {
    val hasProblems: Boolean
        get() = stopped != null || endpoints.any { it.status == EndpointStatus.FAILED || it.status == EndpointStatus.UNRECOGNISED }
}

/**
 * Pulls the owner's own data from Garmin's Personal API into the app's storage: the all-day numbers of the last
 * [sync] days (steps, stress, Body Battery, sleep, HRV, ...) and the activities with their original files.
 *
 * Each data type is asked for separately, so one that Garmin answers differently than expected, or refuses, costs
 * only that data type: the report says which. Only a rejected key, a rate limit, a server error or no connection
 * ends the sync early. What arrives is merged with what the app already knows about a day (for example from a watch
 * cable): counters take the larger value and other values the newer one, so nothing already stored shrinks.
 */
class GarminSyncService(
    private val client: PersonalApiClient,
    private val repo: Repository,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val clock: () -> Instant = { Instant.now() },
    private val maxNewActivities: Int = 30,
) {
    private enum class Kind { DAY, SLEEP, BODY_BATTERY }

    private class Plan(val id: String, val kind: Kind, val chunkDays: Int, val request: (LocalDate, LocalDate) -> Pair<String, List<Pair<String, String>>>)

    private val plans = listOf(
        Plan("dailySummaries", Kind.DAY, 365) { s, e ->
            "/usersummary-service/v2/dailysummaries" to listOf("startDate" to s.toString(), "endDate" to e.toString())
        },
        Plan("steps", Kind.DAY, 28) { s, e -> "/usersummary-service/stats/steps/daily/$s/$e" to emptyList() },
        Plan("stress", Kind.DAY, 28) { s, e -> "/usersummary-service/stats/stress/daily/$s/$e" to emptyList() },
        Plan("hrv", Kind.DAY, 28) { s, e -> "/hrv-service/hrv/daily/$s/$e" to emptyList() },
        Plan("sleep", Kind.SLEEP, 31) { s, e ->
            "/sleep-service/sleep/dailySleepSummariesByDate" to listOf("startDate" to s.toString(), "endDate" to e.toString())
        },
        Plan("bodyBattery", Kind.BODY_BATTERY, 31) { s, e ->
            "/wellness-service/wellness/bodyBattery/reports/daily" to listOf("startDate" to s.toString(), "endDate" to e.toString())
        },
    )

    private class Collected {
        val patches = LinkedHashMap<LocalDate, DailyPatch>()
        val nights = LinkedHashMap<LocalDate, SleepSummary>()
    }

    fun sync(
        days: Int,
        onProgress: (GarminProgress) -> Unit = {},
        isCancelled: () -> Boolean = { false },
    ): GarminSyncReport {
        val span = days.coerceIn(1, 365)
        val end = clock().atZone(zone).toLocalDate()
        val start = end.minusDays(span - 1L)

        val chunksPerPlan = plans.associateWith { windows(start, end, it.chunkDays) }
        var total = chunksPerPlan.values.sumOf { it.size } + 1
        var done = 0
        val collected = Collected()
        val results = ArrayList<EndpointResult>()
        var stopped: GarminApiException? = null
        var cancelled = false

        plans@ for (plan in plans) {
            var records = 0
            val failures = ArrayList<String>()
            var shape: String? = null
            for ((from, to) in chunksPerPlan.getValue(plan)) {
                if (isCancelled()) {
                    cancelled = true
                    break@plans
                }
                onProgress(GarminProgress(done, total, plan.id))
                val (path, query) = plan.request(from, to)
                val json = try {
                    client.getJson(path, query)
                } catch (e: GarminApiException) {
                    if (e.stopsSync) {
                        stopped = e
                        results.add(EndpointResult(plan.id, EndpointStatus.FAILED, records, e.message))
                        break@plans
                    }
                    failures.add(e.message.orEmpty())
                    done++
                    continue
                }
                done++
                val found = collect(plan.kind, json, start, end, collected)
                records += found
                if (found == 0 && json != null && !isEmptyJson(json) && shape == null) shape = JsonShape.describe(json)
            }
            results.add(
                when {
                    records > 0 -> EndpointResult(plan.id, EndpointStatus.OK, records, failures.firstOrNull())
                    failures.isNotEmpty() -> EndpointResult(plan.id, EndpointStatus.FAILED, 0, failures.first())
                    shape != null -> EndpointResult(plan.id, EndpointStatus.UNRECOGNISED, 0, shape)
                    else -> EndpointResult(plan.id, EndpointStatus.EMPTY, 0)
                },
            )
        }

        val (daysUpdated, nightsUpdated) = store(collected)

        var added = 0
        var known = 0
        var withoutFile = 0
        var pending = 0
        if (stopped == null && !cancelled) {
            onProgress(GarminProgress(done, total, "activities"))
            val outcome = syncActivities(start, end, isCancelled) { extra ->
                total += extra
                done++
                onProgress(GarminProgress(done, total, "activities"))
            }
            added = outcome.added
            known = outcome.known
            withoutFile = outcome.withoutFile
            pending = outcome.pending
            stopped = outcome.stopped
            cancelled = outcome.cancelled
            results.add(outcome.result)
            done++
            onProgress(GarminProgress(total, total, ""))
        }

        return GarminSyncReport(
            days = span,
            endpoints = results,
            daysUpdated = daysUpdated,
            nightsUpdated = nightsUpdated,
            activitiesAdded = added,
            activitiesKnown = known,
            activitiesWithoutFile = withoutFile,
            activitiesPending = pending,
            stopped = stopped,
            cancelled = cancelled,
        )
    }

    // ------------------------------------------------------------------------------------ days and nights

    private fun windows(start: LocalDate, end: LocalDate, chunk: Int): List<Pair<LocalDate, LocalDate>> {
        val list = ArrayList<Pair<LocalDate, LocalDate>>()
        var from = start
        while (!from.isAfter(end)) {
            val to = minOf(from.plusDays(chunk - 1L), end)
            list.add(from to to)
            from = to.plusDays(1)
        }
        return list
    }

    private fun isEmptyJson(json: JsonElement): Boolean = when (json) {
        is JsonArray -> json.isEmpty()
        is JsonObject -> json.isEmpty()
        else -> false
    }

    /** Takes what an answer says into [into] and returns how many days or nights it held. */
    private fun collect(kind: Kind, json: JsonElement?, start: LocalDate, end: LocalDate, into: Collected): Int {
        fun inRange(date: LocalDate) = !date.isBefore(start) && !date.isAfter(end)
        return when (kind) {
            Kind.DAY -> DayLeaves.collect(json).filterKeys(::inRange).count { (date, facts) ->
                val patch = DailyPatch.from(facts)
                if (patch.isEmpty) return@count false
                into.patches.merge(date, patch) { a, b -> a.and(b) }
                true
            }
            Kind.SLEEP -> DayLeaves.collect(json).filterKeys(::inRange).count { (date, facts) ->
                val night = SleepMapping.night(date, facts) ?: return@count false
                val seen = into.nights[date]
                if (seen == null || seen.durationS <= night.durationS) into.nights[date] = night
                // The night's own numbers (resting heart rate, HRV) also belong to that day.
                DailyPatch.from(facts).let { patch -> if (!patch.isEmpty) into.patches.merge(date, patch) { a, b -> a.and(b) } }
                true
            }
            Kind.BODY_BATTERY -> DayLeaves.bodyBattery(json).filterKeys(::inRange).count { (date, range) ->
                into.patches.merge(date, DailyPatch(bodyBatteryHigh = range.first, bodyBatteryLow = range.second)) { a, b -> a.and(b) }
                true
            }
        }
    }

    private fun store(collected: Collected): Pair<Int, Int> {
        var daysUpdated = 0
        for ((date, patch) in collected.patches) {
            if (patch.isEmpty) continue
            repo.upsertDaily(patch.applyTo(date, repo.listDaily(date, date).firstOrNull()))
            daysUpdated++
        }
        var nights = 0
        for ((date, night) in collected.nights) {
            val existing = repo.listSleep(date, date).firstOrNull()
            if (existing == null || existing.durationS <= night.durationS) {
                repo.upsertSleep(night)
                nights++
            }
        }
        return daysUpdated to nights
    }

    // ------------------------------------------------------------------------------------------ activities

    private class ActivityOutcome(
        val added: Int,
        val known: Int,
        val withoutFile: Int,
        val pending: Int,
        val result: EndpointResult,
        val stopped: GarminApiException?,
        val cancelled: Boolean,
    )

    private class Summary(
        val id: String,
        val name: String,
        val typeKey: String?,
        val start: Instant,
        val durationS: Double,
        val distanceM: Double,
        val elevationGainM: Double,
        val avgHr: Int?,
        val maxHr: Int?,
        val calories: Int?,
    )

    private fun syncActivities(
        start: LocalDate,
        end: LocalDate,
        isCancelled: () -> Boolean,
        onStep: (extraSteps: Int) -> Unit,
    ): ActivityOutcome {
        val summaries = ArrayList<Summary>()
        var shape: String? = null
        try {
            var offset = 0
            while (offset < MAX_LISTED) {
                val json = client.getJson(
                    "/activitylist-service/activities/search/activities",
                    listOf("start" to offset.toString(), "limit" to PAGE.toString(), "startDate" to start.toString(), "endDate" to end.toString()),
                )
                val items = activityList(json)
                val parsed = items.mapNotNull(::summary)
                if (parsed.isEmpty() && items.isNotEmpty() && shape == null) shape = JsonShape.describe(json)
                summaries.addAll(parsed)
                if (items.size < PAGE) break
                offset += PAGE
            }
        } catch (e: GarminApiException) {
            return ActivityOutcome(0, 0, 0, 0, EndpointResult("activities", EndpointStatus.FAILED, 0, e.message), e.takeIf { it.stopsSync }, false)
        }

        var added = 0
        var known = 0
        var withoutFile = 0
        var pending = 0
        var stopped: GarminApiException? = null
        var cancelled = false
        val fresh = summaries.sortedByDescending { it.start }.filter { summary ->
            val isKnown = repo.listActivities(start = summary.start.minusSeconds(120), end = summary.start.plusSeconds(120), limit = 1).isNotEmpty()
            if (isKnown) known++
            !isKnown
        }
        onStep(minOf(fresh.size, maxNewActivities))
        for ((index, summary) in fresh.withIndex()) {
            if (isCancelled()) {
                cancelled = true
                pending += fresh.size - index
                break
            }
            if (index >= maxNewActivities) {
                pending++
                continue
            }
            try {
                val fromFile = downloadActivity(summary)
                if (fromFile == null) withoutFile++
                repo.upsertActivity(fromFile ?: fromSummary(summary))
                added++
            } catch (e: GarminApiException) {
                if (e.stopsSync) {
                    stopped = e
                    pending += fresh.size - index
                    break
                }
                repo.upsertActivity(fromSummary(summary))
                withoutFile++
                added++
            }
            onStep(0)
        }

        val records = summaries.size
        val result = when {
            records > 0 -> EndpointResult("activities", EndpointStatus.OK, records)
            shape != null -> EndpointResult("activities", EndpointStatus.UNRECOGNISED, 0, shape)
            else -> EndpointResult("activities", EndpointStatus.EMPTY, 0)
        }
        return ActivityOutcome(added, known, withoutFile, pending, result, stopped, cancelled)
    }

    /** The activity as the watch recorded it, or null when the file is missing or cannot be read. */
    private fun downloadActivity(summary: Summary): Activity? {
        val bytes = try {
            client.download("/download-service/files/activity/${summary.id}")
        } catch (e: GarminApiException) {
            if (e.stopsSync) throw e
            return null
        }
        val fit = ZipFit.extract(bytes) ?: bytes
        val parsed = try {
            Formats.import(fit, "activity-${summary.id}.fit")
        } catch (_: FormatError) {
            return null
        }
        return parsed.copy(
            source = "garmin",
            externalId = summary.id,
            name = summary.name.ifBlank { parsed.name },
        )
    }

    private fun fromSummary(s: Summary) = Activity(
        id = "garmin-${s.id}",
        source = "garmin",
        externalId = s.id,
        name = s.name,
        sport = garminSport(s.typeKey),
        startTime = s.start,
        durationS = s.durationS,
        distanceM = s.distanceM,
        elevationGainM = s.elevationGainM,
        avgHr = s.avgHr,
        maxHr = s.maxHr,
        calories = s.calories,
    )

    private fun activityList(json: JsonElement?): List<JsonObject> = when (json) {
        is JsonArray -> json.filterIsInstance<JsonObject>()
        is JsonObject -> json.values.filterIsInstance<JsonArray>().firstOrNull()?.filterIsInstance<JsonObject>().orEmpty()
        else -> emptyList()
    }

    private fun summary(o: JsonObject): Summary? {
        fun text(key: String) = (o[key] as? JsonPrimitive)?.contentOrNull
        fun number(key: String) = (o[key] as? JsonPrimitive)?.doubleOrNull
        val id = text("activityId")?.trim()?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) } ?: return null
        val startTime = text("startTimeGMT")?.let(::parseUtc)
            ?: text("startTimeLocal")?.let { parseLocal(it) }
            ?: return null
        return Summary(
            id = id,
            name = text("activityName").orEmpty(),
            typeKey = ((o["activityType"] as? JsonObject)?.get("typeKey") as? JsonPrimitive)?.contentOrNull,
            start = startTime,
            durationS = number("duration") ?: 0.0,
            distanceM = number("distance") ?: 0.0,
            elevationGainM = number("elevationGain") ?: 0.0,
            avgHr = number("averageHR")?.roundToInt()?.takeIf { it > 0 },
            maxHr = number("maxHR")?.roundToInt()?.takeIf { it > 0 },
            calories = number("calories")?.roundToInt()?.takeIf { it > 0 },
        )
    }

    private fun parseUtc(text: String): Instant? = parseStamp(text)?.toInstant(ZoneOffset.UTC)

    private fun parseLocal(text: String): Instant? = parseStamp(text)?.atZone(zone)?.toInstant()

    private fun parseStamp(text: String): LocalDateTime? = try {
        LocalDateTime.parse(text.trim().replace('T', ' ').substringBefore('.'), STAMP)
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val PAGE = 50
        const val MAX_LISTED = 200
        val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}

/** The Garmin activity type key (`trail_running`, `indoor_cycling`, ...) as one of the app's sports. */
fun garminSport(typeKey: String?): Sport {
    val key = typeKey?.lowercase(Locale.ROOT) ?: return Sport.OTHER
    return when {
        "run" in key -> Sport.RUNNING
        "cycling" in key || "biking" in key -> Sport.CYCLING
        "swim" in key -> Sport.SWIMMING
        "walking" in key -> Sport.WALKING
        "hiking" in key -> Sport.HIKING
        "strength" in key -> Sport.STRENGTH
        "yoga" in key -> Sport.YOGA
        else -> sportFromText(key)
    }
}

/** Garmin delivers an activity file as a ZIP holding the original FIT file. */
internal object ZipFit {
    private const val MAX_BYTES = 30 * 1024 * 1024

    /** The first `.fit` file in [zip], or null when [zip] is not a ZIP or holds none. */
    fun extract(zip: ByteArray): ByteArray? {
        if (zip.size < 4 || zip[0] != 'P'.code.toByte() || zip[1] != 'K'.code.toByte()) return null
        return try {
            ZipInputStream(ByteArrayInputStream(zip)).use { input -> firstFit(input) }
        } catch (_: java.io.IOException) {
            null
        }
    }

    private fun firstFit(input: ZipInputStream): ByteArray? {
        var entry = input.nextEntry
        while (entry != null) {
            if (!entry.isDirectory && entry.name.endsWith(".fit", ignoreCase = true)) return readCapped(input)
            entry = input.nextEntry
        }
        return null
    }

    private fun readCapped(input: ZipInputStream): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) return out.toByteArray()
            out.write(buffer, 0, n)
            if (out.size() > MAX_BYTES) return null
        }
    }
}
