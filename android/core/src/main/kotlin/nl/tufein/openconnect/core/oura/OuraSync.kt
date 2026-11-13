package nl.tufein.openconnect.core.oura

import kotlinx.serialization.json.JsonObject
import nl.tufein.openconnect.core.Repository
import java.time.LocalDate
import java.time.OffsetDateTime

data class OuraSyncReport(val daysUpdated: Int = 0, val nightsUpdated: Int = 0, val activitiesAdded: Int = 0) {
    val total: Int get() = daysUpdated + nightsUpdated + activitiesAdded
}

/**
 * Brings an Oura ring's days, nights, scores and workouts into the app's own Oura storage.
 *
 * Since v0.17.0 nothing is mixed into the watch's data: the ring and the watch each keep their own numbers, so a
 * day is never counted twice and someone without a ring sees nothing of it. The Oura tab shows what is stored here.
 */
class OuraSync(private val client: OuraClient, private val repo: Repository) {
    fun sync(days: Int, today: LocalDate): OuraSyncReport {
        val start = today.minusDays(days.toLong() - 1).toString()
        // One day further than today: harmless, and it covers a collection that would leave the end day out.
        val end = today.plusDays(1).toString()
        val nights = nights(client.documents("sleep", start, end))
        val sleepScores = byDay(client.documents("daily_sleep", start, end))
        val readiness = byDay(client.documents("daily_readiness", start, end))
        val activity = byDay(client.documents("daily_activity", start, end))
        val spo2 = byDay(client.documents("daily_spo2", start, end))
        val workouts = client.documents("workout", start, end)
            .mapNotNull { w -> workout(w)?.let { (day(w) ?: startDay(w) ?: return@mapNotNull null) to it } }
            .groupBy({ it.first }, { it.second })

        val dates = (nights.keys + sleepScores.keys + readiness.keys + activity.keys + spo2.keys + workouts.keys).sorted()
        val before = repo.listOuraDays(LocalDate.parse(start), LocalDate.parse(end)).associateBy { it.date }
        var daysUpdated = 0
        var nightsUpdated = 0
        var added = 0
        for (date in dates) {
            val night = nights[date]
            val a = activity[date]
            val r = readiness[date]
            val day = OuraDay(
                day = date.toString(),
                readiness = r?.int("score"),
                sleepScore = sleepScores[date]?.int("score"),
                activityScore = a?.int("score"),
                steps = a?.int("steps"),
                activeKcal = a?.int("active_calories"),
                sleepS = night?.int("total_sleep_duration")?.takeIf { it > 0 },
                deepS = night?.int("deep_sleep_duration"),
                lightS = night?.int("light_sleep_duration"),
                remS = night?.int("rem_sleep_duration"),
                awakeS = night?.int("awake_time"),
                lowestHr = night?.int("lowest_heart_rate"),
                averageHrv = night?.int("average_hrv"),
                breathAvg = night?.number("average_breath"),
                spo2Avg = (spo2[date]?.get("spo2_percentage") as? JsonObject)?.number("average"),
                temperatureDeviation = r?.number("temperature_deviation"),
                workouts = workouts[date].orEmpty().sortedBy { it.startMs },
            )
            val old = before[date]
            if (day == old) continue
            repo.upsertOuraDay(day)
            daysUpdated++
            if (day.sleepS != null && day.sleepS != old?.sleepS) nightsUpdated++
            val known = old?.workouts.orEmpty().map { it.id }.toSet()
            added += day.workouts.count { it.id !in known }
        }
        return OuraSyncReport(daysUpdated, nightsUpdated, added)
    }

    private companion object {
        fun day(d: JsonObject): LocalDate? = d.string("day")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

        /** The local date the workout started on, for a workout without Oura's "day". */
        fun startDay(w: JsonObject): LocalDate? = w.string("start_datetime")?.let { runCatching { OffsetDateTime.parse(it).toLocalDate() }.getOrNull() }

        fun byDay(documents: List<JsonObject>): Map<LocalDate, JsonObject> =
            documents.mapNotNull { d -> day(d)?.let { it to d } }.toMap()

        fun millis(text: String?): Long? = text?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }

        /** The main sleep of each day: the longest one that is not a nap, a rest or deleted. */
        fun nights(documents: List<JsonObject>): Map<LocalDate, JsonObject> =
            documents.filter { it.string("type") == "long_sleep" || it.string("type") == "sleep" }
                .mapNotNull { d -> day(d)?.let { it to d } }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, list) -> list.maxBy { it.int("total_sleep_duration") ?: 0 } }

        fun workout(w: JsonObject): OuraWorkout? {
            val id = w.string("id") ?: return null
            return OuraWorkout(
                id = id,
                activity = w.string("activity").orEmpty(),
                label = w.string("label"),
                startMs = millis(w.string("start_datetime")) ?: return null,
                endMs = millis(w.string("end_datetime")) ?: return null,
                calories = w.number("calories")?.let { Math.round(it).toInt() },
                distanceM = w.number("distance"),
            )
        }
    }
}
