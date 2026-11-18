package nl.tufein.openconnect.core.whoop

import kotlinx.serialization.json.JsonObject
import nl.tufein.openconnect.core.Repository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

data class WhoopSyncReport(val daysUpdated: Int = 0, val nightsUpdated: Int = 0, val activitiesAdded: Int = 0) {
    val total: Int get() = daysUpdated + nightsUpdated + activitiesAdded
}

/**
 * Brings a WHOOP band's days, nights, recovery, strain and workouts into the app's own WHOOP storage. Nothing is mixed into
 * the watch's data or the ring's: each device keeps its own numbers, so a day is never counted twice.
 */
class WhoopSync(private val client: WhoopClient, private val repo: Repository) {
    fun sync(days: Int, today: LocalDate): WhoopSyncReport {
        val first = today.minusDays(days.toLong() - 1)
        // A day before the first day, at midnight UTC: the night that ends on the first morning began the evening before.
        val start = first.minusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
        val cycles = client.records("cycle", start).associateBy { it.long("id") }
        val recoveries = client.records("recovery", start).associateBy { it.long("cycle_id") }
        val sleeps = client.records("activity/sleep", start)
        val workouts = client.records("activity/workout", start)
            .mapNotNull { w -> workout(w)?.let { (localDate(w, "start") ?: return@mapNotNull null) to it } }
            .groupBy({ it.first }, { it.second })

        // One night per day: the longest one that is not a nap. Everything else of that day follows its cycle id.
        val nights = HashMap<LocalDate, JsonObject>()
        for (sleep in sleeps) {
            if (sleep.bool("nap") == true) continue
            val date = localDate(sleep, "end") ?: continue
            val old = nights[date]
            if (old == null || asleep(sleep) > asleep(old)) nights[date] = sleep
        }
        val end = today.plusDays(1)
        val dates = (nights.keys + workouts.keys).filter { !it.isBefore(first) && !it.isAfter(end) }.sorted()
        val before = repo.listWhoopDays(first, end).associateBy { it.date }
        var daysUpdated = 0
        var nightsUpdated = 0
        var added = 0
        for (date in dates) {
            val night = nights[date]
            val cycleId = night?.long("cycle_id")
            val cycle = cycleId?.let(cycles::get)?.obj("score")?.takeIf { cycles[cycleId]?.string("score_state") == "SCORED" }
            val recovery = cycleId?.let(recoveries::get)?.let { r -> r.obj("score")?.takeIf { r.string("score_state") == "SCORED" } }
            val sleepScore = night?.obj("score")?.takeIf { night.string("score_state") == "SCORED" }
            val stages = sleepScore?.obj("stage_summary")
            val need = sleepScore?.obj("sleep_needed")
            val asleep = stages?.let { seconds(it, "total_light_sleep_time_milli", "total_slow_wave_sleep_time_milli", "total_rem_sleep_time_milli") }
            val day = WhoopDay(
                day = date.toString(),
                recovery = recovery?.int("recovery_score"),
                strain = cycle?.number("strain")?.let { Math.rint(it * 10) / 10 },
                sleepPerformance = sleepScore?.int("sleep_performance_percentage"),
                sleepConsistency = sleepScore?.int("sleep_consistency_percentage"),
                sleepEfficiency = sleepScore?.int("sleep_efficiency_percentage"),
                sleepS = asleep?.takeIf { it > 0 },
                deepS = stages?.number("total_slow_wave_sleep_time_milli")?.let { (it / 1000).toInt() },
                lightS = stages?.number("total_light_sleep_time_milli")?.let { (it / 1000).toInt() },
                remS = stages?.number("total_rem_sleep_time_milli")?.let { (it / 1000).toInt() },
                awakeS = stages?.number("total_awake_time_milli")?.let { (it / 1000).toInt() },
                sleepNeedS = need?.let { seconds(it, "baseline_milli", "need_from_sleep_debt_milli", "need_from_recent_strain_milli", "need_from_recent_nap_milli") }?.takeIf { it > 0 },
                restingHr = recovery?.int("resting_heart_rate"),
                hrv = recovery?.number("hrv_rmssd_milli")?.let { Math.rint(it * 10) / 10 },
                respiration = sleepScore?.number("respiratory_rate"),
                spo2 = recovery?.number("spo2_percentage"),
                skinTempC = recovery?.number("skin_temp_celsius"),
                kcal = cycle?.number("kilojoule")?.let { Math.round(it / KJ_PER_KCAL).toInt() },
                avgHr = cycle?.int("average_heart_rate"),
                maxHr = cycle?.int("max_heart_rate"),
                steps = cycleId?.let(cycles::get)?.int("step_count"),
                workouts = workouts[date].orEmpty().sortedBy { it.startMs },
            )
            val old = before[date]
            if (day == old) continue
            repo.upsertWhoopDay(day)
            daysUpdated++
            if (day.sleepS != null && day.sleepS != old?.sleepS) nightsUpdated++
            val known = old?.workouts.orEmpty().map { it.id }.toSet()
            added += day.workouts.count { it.id !in known }
        }
        return WhoopSyncReport(daysUpdated, nightsUpdated, added)
    }

    private companion object {
        const val KJ_PER_KCAL = 4.184

        /** The local date of a timestamp (`start` or `end`) on the clock of the place it was recorded in, from its `timezone_offset`. */
        fun localDate(record: JsonObject, field: String): LocalDate? {
            val instant = record.string(field)?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null
            val offset = record.string("timezone_offset")?.let { runCatching { ZoneOffset.of(it) }.getOrNull() } ?: ZoneOffset.UTC
            return instant.atOffset(offset).toLocalDate()
        }

        fun millis(record: JsonObject, field: String): Long? = record.string(field)?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

        /** Sums fields that hold milliseconds into seconds; null when none of them is there. */
        fun seconds(from: JsonObject, vararg names: String): Int? {
            val values = names.mapNotNull { from.number(it) }
            return values.takeIf { it.isNotEmpty() }?.sum()?.let { (it / 1000).toInt() }
        }

        fun asleep(sleep: JsonObject): Int = sleep.obj("score")?.obj("stage_summary")?.let {
            seconds(it, "total_light_sleep_time_milli", "total_slow_wave_sleep_time_milli", "total_rem_sleep_time_milli")
        } ?: 0

        fun workout(w: JsonObject): WhoopWorkout? {
            val id = w.string("id") ?: return null
            val score = w.obj("score")?.takeIf { w.string("score_state") == "SCORED" }
            return WhoopWorkout(
                id = id,
                activity = w.string("sport_name").orEmpty().ifEmpty { "workout" },
                startMs = millis(w, "start") ?: return null,
                endMs = millis(w, "end") ?: return null,
                strain = score?.number("strain")?.let { Math.rint(it * 10) / 10 },
                avgHr = score?.int("average_heart_rate"),
                maxHr = score?.int("max_heart_rate"),
                calories = score?.number("kilojoule")?.let { Math.round(it / KJ_PER_KCAL).toInt() },
                distanceM = score?.number("distance_meter"),
            )
        }
    }
}
