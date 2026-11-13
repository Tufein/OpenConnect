package nl.tufein.openconnect.core.oura

import kotlinx.serialization.json.JsonObject
import nl.tufein.openconnect.core.Activity
import nl.tufein.openconnect.core.DailySummary
import nl.tufein.openconnect.core.Repository
import nl.tufein.openconnect.core.SleepSummary
import nl.tufein.openconnect.core.Sport
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.math.abs

data class OuraSyncReport(val daysUpdated: Int = 0, val nightsUpdated: Int = 0, val activitiesAdded: Int = 0) {
    val total: Int get() = daysUpdated + nightsUpdated + activitiesAdded
}

/**
 * Brings an Oura ring's days, nights and workouts into the app. By default it only fills what is missing, so a day or
 * night your watch already gave stays as it is and nothing is counted twice. With [preferSleep] the ring's night
 * replaces the watch's (a ring is usually worn at night when a watch is not).
 *
 * Oura's own readiness and stress scores are not stored: the app works out its own from the same numbers.
 */
class OuraSync(private val client: OuraClient, private val repo: Repository) {
    fun sync(days: Int, today: LocalDate, preferSleep: Boolean): OuraSyncReport {
        val start = today.minusDays(days.toLong() - 1).toString()
        // One day further than today: harmless, and it covers a collection that would leave the end day out.
        val end = today.plusDays(1).toString()
        val nights = nights(client.documents("sleep", start, end))
        val scores = client.documents("daily_sleep", start, end).mapNotNull { d -> day(d)?.let { it to d.int("score") } }.toMap()
        val spo2 = client.documents("daily_spo2", start, end).mapNotNull { d ->
            val average = (d["spo2_percentage"] as? JsonObject)?.number("average") ?: return@mapNotNull null
            day(d)?.let { it to Math.round(average).toInt() }
        }.toMap()
        val activity = client.documents("daily_activity", start, end).mapNotNull { d -> day(d)?.let { it to d } }.toMap()

        var daysUpdated = 0
        for (date in (activity.keys + nights.keys + spo2.keys).sorted()) {
            val before = repo.listDaily(date, date).firstOrNull() ?: DailySummary(date = date)
            val after = fill(before, activity[date], nights[date], spo2[date])
            if (after != before) {
                repo.upsertDaily(after)
                daysUpdated++
            }
        }
        var nightsUpdated = 0
        for ((date, night) in nights) {
            val mine = sleep(date, night, scores[date]) ?: continue
            val existing = repo.listSleep(date, date).firstOrNull()
            val keep = when {
                existing == null -> mine
                preferSleep -> mine
                existing.score == null && mine.score != null && existing.durationS == mine.durationS -> mine
                else -> null
            }
            if (keep != null && keep != existing) {
                repo.upsertSleep(keep)
                nightsUpdated++
            }
        }
        var added = 0
        for (w in client.documents("workout", start, end)) {
            val a = workout(w) ?: continue
            if (repo.getActivity(a.id) != null) continue
            val near = repo.listActivities(start = a.startTime.minus(NEAR), end = a.startTime.plus(NEAR), limit = 5)
            if (near.any { abs(Duration.between(it.startTime, a.startTime).seconds) <= NEAR.seconds }) continue // the watch has it already
            repo.upsertActivity(a)
            added++
        }
        return OuraSyncReport(daysUpdated, nightsUpdated, added)
    }

    private companion object {
        val NEAR: Duration = Duration.ofMinutes(2)

        fun day(d: JsonObject): LocalDate? = d.string("day")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

        fun time(text: String?): Instant? = text?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }

        /** The main sleep of each day: the longest one that is not a nap, a rest or deleted. */
        fun nights(documents: List<JsonObject>): Map<LocalDate, JsonObject> =
            documents.filter { it.string("type") == "long_sleep" || it.string("type") == "sleep" }
                .mapNotNull { d -> day(d)?.let { it to d } }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, list) -> list.maxBy { it.int("total_sleep_duration") ?: 0 } }

        fun sleep(date: LocalDate, d: JsonObject, score: Int?): SleepSummary? {
            val total = d.int("total_sleep_duration")?.takeIf { it > 0 } ?: return null
            return SleepSummary(
                date = date,
                durationS = total,
                deepS = d.int("deep_sleep_duration") ?: 0,
                lightS = d.int("light_sleep_duration") ?: 0,
                remS = d.int("rem_sleep_duration") ?: 0,
                awakeS = d.int("awake_time") ?: 0,
                score = score,
            )
        }

        /** Every field keeps what is there; the ring only fills the gaps. */
        fun fill(before: DailySummary, activity: JsonObject?, night: JsonObject?, spo2: Int?): DailySummary {
            var d = before
            if (activity != null) {
                if (d.steps == 0) d = d.copy(steps = activity.int("steps") ?: 0)
                if (d.activeKcal == 0) d = d.copy(activeKcal = activity.int("active_calories") ?: 0)
                if (d.distanceM == 0.0) d = d.copy(distanceM = activity.number("equivalent_walking_distance") ?: 0.0)
                if (d.moderateIntensityMin == 0 && d.vigorousIntensityMin == 0) {
                    d = d.copy(
                        moderateIntensityMin = (activity.int("medium_activity_time") ?: 0) / 60,
                        vigorousIntensityMin = (activity.int("high_activity_time") ?: 0) / 60,
                    )
                }
            }
            if (night != null) {
                if (d.restingHr == null) d = d.copy(restingHr = night.int("lowest_heart_rate"))
                if (d.hrvMs == null) d = d.copy(hrvMs = night.int("average_hrv"))
                if (d.respirationAvg == null) d = d.copy(respirationAvg = night.number("average_breath"))
            }
            if (d.spo2Avg == null && spo2 != null) d = d.copy(spo2Avg = spo2)
            return d
        }

        fun workout(w: JsonObject): Activity? {
            val id = w.string("id") ?: return null
            val start = time(w.string("start_datetime")) ?: return null
            val end = time(w.string("end_datetime")) ?: return null
            val kind = w.string("activity").orEmpty()
            return Activity(
                id = "oura-$id",
                source = "oura",
                externalId = id,
                name = w.string("label")?.takeIf { it.isNotBlank() } ?: kind.replace('_', ' ').replaceFirstChar { it.uppercase() },
                sport = sport(kind),
                startTime = start,
                durationS = Duration.between(start, end).seconds.coerceAtLeast(0).toDouble(),
                distanceM = w.number("distance") ?: 0.0,
                calories = w.int("calories"),
                deviceName = "Oura Ring",
            )
        }

        fun sport(kind: String): Sport = when {
            "run" in kind -> Sport.RUNNING
            "cycl" in kind || "bik" in kind -> Sport.CYCLING
            "swim" in kind -> Sport.SWIMMING
            "hik" in kind -> Sport.HIKING
            "walk" in kind -> Sport.WALKING
            "strength" in kind || "weight" in kind -> Sport.STRENGTH
            "yoga" in kind -> Sport.YOGA
            else -> Sport.OTHER
        }
    }
}
