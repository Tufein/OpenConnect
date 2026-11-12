package nl.tufein.openconnect.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Everything the demo generator produces, ready to be stored. */
data class DemoDataset(
    val activities: List<Activity>,
    val dailies: List<DailySummary>,
    val sleeps: List<SleepSummary>,
)

/**
 * Deterministic, Garmin-like demo data so the app is usable without an account, a key or a server.
 * The same seed and calendar day always give the same activity.
 */
object DemoData {
    private class Spec(val sport: Sport, val name: String, val device: String, val minutes: Int)

    // weekday (Mon = 1) -> workout
    private val SCHEDULE = mapOf(
        2 to Spec(Sport.RUNNING, "Tempo Run", "Forerunner 965", 38),
        3 to Spec(Sport.STRENGTH, "Strength", "Forerunner 965", 40),
        4 to Spec(Sport.RUNNING, "Easy Run", "Forerunner 965", 45),
        6 to Spec(Sport.CYCLING, "Weekend Ride", "Edge 1050", 95),
        7 to Spec(Sport.WALKING, "Sunday Walk", "Forerunner 965", 60),
    )
    private val SPEED_MPS = mapOf(Sport.RUNNING to 2.9, Sport.CYCLING to 7.2, Sport.WALKING to 1.4)
    private val HR_BASE = mapOf(Sport.RUNNING to 152, Sport.CYCLING to 138, Sport.WALKING to 105, Sport.STRENGTH to 118)
    private const val CENTRE_LAT = 52.3676
    private const val CENTRE_LON = 4.9041
    private const val SAMPLE_EVERY_S = 10

    private fun rng(seed: Long, kind: String, day: LocalDate) = Random("$seed:$kind:$day".hashCode().toLong())

    private fun Random.int(lo: Int, hi: Int) = lo + nextInt(hi - lo + 1)

    private fun Random.uniform(lo: Double, hi: Double) = lo + nextDouble() * (hi - lo)

    fun generate(today: LocalDate, days: Int = 45, seed: Long = 42): DemoDataset {
        val activities = ArrayList<Activity>()
        val dailies = ArrayList<DailySummary>()
        val sleeps = ArrayList<SleepSummary>()
        for (offset in days downTo 0) {
            val day = today.minusDays(offset.toLong())
            val activity = activity(day, seed)
            activity?.let { activities.add(it) }
            val (daily, sleep) = health(day, seed, ran = activity?.sport == Sport.RUNNING)
            dailies.add(daily)
            sleeps.add(sleep)
        }
        return DemoDataset(activities, dailies, sleeps)
    }

    fun activity(day: LocalDate, seed: Long = 42): Activity? {
        val spec = SCHEDULE[day.dayOfWeek.value] ?: return null
        val rng = rng(seed, "activity", day)
        val start = day.atTime(LocalTime.of(7, 0)).toInstant(ZoneOffset.UTC).plusSeconds(rng.int(0, 90) * 60L)
        val duration = ((spec.minutes + rng.int(-6, 8)) * 60)
        val n = duration / SAMPLE_EVERY_S
        val speed = (SPEED_MPS[spec.sport] ?: 0.0) * rng.uniform(0.94, 1.06)
        val hasGps = spec.sport != Sport.STRENGTH
        val radius = speed * duration / (2 * PI)
        val baseHr = HR_BASE.getValue(spec.sport) + rng.int(-5, 5)

        val track = ArrayList<TrackPoint>(n + 1)
        var distance = 0.0
        var climb = 0.0
        var prevEle: Double? = null
        val hrs = ArrayList<Int>(n + 1)
        val lat0 = CENTRE_LAT + 0.01 * (day.dayOfWeek.value - 4) / 3
        for (i in 0..n) {
            val frac = i.toDouble() / n
            val ele = 2 + 9 * sin(frac * 4 * PI) + rng.uniform(-0.3, 0.3)
            val hr = Math.rint(baseHr + 12 * min(1.0, frac * 6) + 5 * sin(frac * 9) + rng.int(-2, 2)).toInt()
            hrs.add(hr)
            val time: Instant = start.plusSeconds(i.toLong() * SAMPLE_EVERY_S)
            if (!hasGps) {
                track.add(TrackPoint(time = time, hr = hr))
                continue
            }
            val angle = 2 * PI * frac
            val wobble = 1 + 0.28 * sin(2 * angle + day.dayOfMonth) + 0.12 * sin(5 * angle)
            if (i > 0) {
                distance += speed * SAMPLE_EVERY_S * rng.uniform(0.97, 1.03)
                prevEle?.let { if (ele - it > 0.05) climb += ele - it }
            }
            prevEle = ele
            track.add(
                TrackPoint(
                    time = time,
                    lat = round(lat0 + radius * wobble * sin(angle) / 111_320, 6),
                    lon = round(CENTRE_LON + radius * (wobble * cos(angle) - 1) / (111_320 * cos(Math.toRadians(lat0))), 6),
                    ele = round(ele, 1),
                    hr = hr,
                    cadence = when (spec.sport) {
                        Sport.CYCLING -> 82 + rng.int(-6, 6)
                        Sport.RUNNING -> 168 + rng.int(-4, 6)
                        else -> null
                    },
                    power = if (spec.sport == Sport.CYCLING) 190 + rng.int(-40, 60) else null,
                    distanceM = round(distance, 1),
                ),
            )
        }
        val id = day.toString().replace("-", "")
        return Activity(
            id = "demo:$id",
            source = "demo",
            externalId = id,
            name = spec.name,
            sport = spec.sport,
            startTime = start,
            durationS = duration.toDouble(),
            distanceM = round(distance, 1),
            elevationGainM = round(climb, 1),
            avgHr = Math.rint(hrs.sum().toDouble() / hrs.size).toInt(),
            maxHr = hrs.max(),
            calories = Math.rint(duration / 60.0 * (if (spec.sport == Sport.RUNNING) 11 else 7)).toInt(),
            deviceName = spec.device,
            track = track,
        )
    }

    fun health(day: LocalDate, seed: Long = 42, ran: Boolean = false): Pair<DailySummary, SleepSummary> {
        val rng = rng(seed, "health", day)
        val steps = 6500 + rng.int(-1800, 2600) + (if (ran) 3500 else 0)
        val resting = 50 + rng.int(0, 6)
        val avgStress = 22 + rng.int(0, 25)
        val daily = DailySummary(
            date = day,
            steps = steps,
            stepsGoal = 10_000,
            distanceM = round(steps * 0.76, 1),
            activeKcal = steps / 22,
            floorsClimbed = 4 + rng.int(0, 12),
            moderateIntensityMin = rng.int(8, 35),
            vigorousIntensityMin = if (ran) rng.int(5, 20) else 0,
            minHr = resting - rng.int(2, 5),
            maxHr = 120 + rng.int(0, 50),
            restingHr = resting,
            avgStress = avgStress,
            maxStress = min(99, avgStress + rng.int(30, 55)),
            bodyBatteryHigh = 85 + rng.int(0, 14),
            bodyBatteryLow = 12 + rng.int(0, 25),
        )
        val total = (3600 * rng.uniform(6.0, 8.6)).toInt()
        val deep = (total * 0.17).toInt()
        val rem = (total * 0.22).toInt()
        val awake = (total * 0.06).toInt()
        val sleep = SleepSummary(
            date = day,
            durationS = total,
            deepS = deep,
            lightS = total - deep - rem - awake,
            remS = rem,
            awakeS = awake,
            score = 55 + (total / 3600.0 * 4).toInt() + rng.int(0, 8),
        )
        return daily to sleep
    }

    private fun round(value: Double, digits: Int): Double {
        val factor = Math.pow(10.0, digits.toDouble())
        return Math.rint(value * factor) / factor
    }

    /** Store a dataset, replacing nothing else. */
    fun store(repo: Repository, data: DemoDataset) {
        data.activities.forEach(repo::upsertActivity)
        data.dailies.forEach(repo::upsertDaily)
        data.sleeps.forEach(repo::upsertSleep)
    }
}
