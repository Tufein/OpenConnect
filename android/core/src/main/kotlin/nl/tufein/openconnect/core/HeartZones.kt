package nl.tufein.openconnect.core

import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.roundToInt

/** Where the maximum heart rate comes from: typed in by the user, the highest value in the activities, or nowhere yet. */
enum class MaxHrSource { SETTING, MEASURED, NONE }

data class MaxHr(val bpm: Int, val source: MaxHrSource)

/** One zone as a range of beats per minute. [zone] is 1 to 5; [to] is null for the top zone (no upper limit). */
data class ZoneRange(val zone: Int, val from: Int, val to: Int?)

/**
 * Seconds spent below zone 1 (index 0) and in zones 1 to 5 (index 1 to 5). Zones follow Garmin's usual scheme: 50 to 60,
 * 60 to 70, 70 to 80, 80 to 90 and 90 to 100 percent of the maximum heart rate.
 */
data class ZoneTimes(val seconds: List<Double> = List(HeartZones.ZONES + 1) { 0.0 }) {
    /** All time with a heart rate, including the time below zone 1. */
    val total: Double get() = seconds.sum()

    /** Time in zones 1 to 5 only. */
    val zoned: Double get() = seconds.drop(1).sum()

    operator fun plus(other: ZoneTimes) = ZoneTimes(seconds.zip(other.seconds) { a, b -> a + b })
}

/** The zone times of one activity. [exact] is false when only the average heart rate was known (a rough guess). */
data class ActivityZones(val times: ZoneTimes, val exact: Boolean)

/** One 7-day block, labelled with the day it starts. [exact] is false when any activity in it was a guess. */
data class WeekZones(val start: LocalDate, val times: ZoneTimes, val activities: Int, val exact: Boolean)

/** How the zoned time divides: zones 1 and 2 are easy, 3 is the middle, 4 and 5 are hard. The three add up to 1. */
data class ZoneSplit(val easy: Double, val middle: Double, val hard: Double)

enum class ZoneVerdict {
    /** Too little time with a heart rate to say anything. */
    NO_DATA,

    /** Mostly easy with some hard work: the shape most plans aim for. */
    BALANCED,

    /** More than 30 percent in zones 4 and 5. */
    TOO_HARD,

    /** More than 40 percent in zone 3, the grey zone that is neither easy nor hard. */
    TOO_MIDDLE,

    /** Less than 5 percent in zones 4 and 5. */
    ALL_EASY,
}

/** What the Zones tab shows: the maximum, the zone table, the last four weeks and a verdict on the latest one. */
data class ZoneOverview(
    val maxHr: MaxHr,
    val ranges: List<ZoneRange>,
    /** Four 7-day blocks, oldest first; the last one ends today. */
    val weeks: List<WeekZones>,
    val split: ZoneSplit?,
    val verdict: ZoneVerdict,
) {
    val thisWeek: WeekZones? get() = weeks.lastOrNull()
}

/**
 * Heart-rate zones as a percentage of the maximum heart rate. This is OpenConnect's own arithmetic on the heart rate
 * your watch recorded; it is not Garmin's zone set (which can follow your lactate threshold or heart-rate reserve) and
 * not medical advice. A wrist sensor can read too high at the start of a run and a chest strap is more exact.
 */
object HeartZones {
    const val ZONES = 5
    const val MIN_MAX_HR = 120
    const val MAX_MAX_HR = 230

    /** Where a first press of "+" starts when nothing is known yet. */
    const val DEFAULT_MAX_HR = 190

    /** A sample counts for the time until the next one, but never for more than this: a pause is not a workout. */
    const val MAX_GAP_S = 15.0

    /** At least half an hour with a heart rate before a verdict is given. */
    const val MIN_VERDICT_SECONDS = 1800.0

    const val WEEKS = 4

    private val LIMITS = listOf(0.5, 0.6, 0.7, 0.8, 0.9)
    private const val HARD_SHARE = 0.30
    private const val MIDDLE_SHARE = 0.40
    private const val NO_HARD_SHARE = 0.05

    /** The typed-in maximum when it is a believable one, otherwise the highest heart rate in [activities] (capped). */
    fun maxHr(setting: Int, activities: List<Activity>): MaxHr {
        if (setting in MIN_MAX_HR..MAX_MAX_HR) return MaxHr(setting, MaxHrSource.SETTING)
        val seen = activities.mapNotNull { it.maxHr ?: it.avgHr }.maxOrNull()
        if (seen == null || seen < MIN_MAX_HR) return MaxHr(0, MaxHrSource.NONE)
        return MaxHr(seen.coerceAtMost(MAX_MAX_HR), MaxHrSource.MEASURED)
    }

    /** The five lower limits in beats per minute: 50, 60, 70, 80 and 90 percent of [maxHr], rounded. */
    fun limits(maxHr: Int): List<Int> = LIMITS.map { (maxHr * it).roundToInt() }

    /** 0 below zone 1, otherwise 1 to 5. */
    fun zoneOf(hr: Int, limits: List<Int>): Int = limits.count { hr >= it }

    fun ranges(maxHr: Int): List<ZoneRange> {
        val limits = limits(maxHr)
        return limits.indices.map { i -> ZoneRange(i + 1, limits[i], limits.getOrNull(i + 1)?.minus(1)) }
    }

    private fun timesOf(zone: Int, seconds: Double): ZoneTimes =
        ZoneTimes(List(ZONES + 1) { if (it == zone) seconds else 0.0 })

    /** Time per zone from the heart-rate samples of a track; null when the track has no usable heart rate. */
    fun timeInZones(track: List<TrackPoint>, maxHr: Int): ZoneTimes? {
        if (maxHr <= 0) return null
        val limits = limits(maxHr)
        val seconds = MutableList(ZONES + 1) { 0.0 }
        var any = false
        for (i in 0 until track.size - 1) {
            val hr = track[i].hr ?: continue
            if (hr <= 0) continue
            val dt = Duration.between(track[i].time, track[i + 1].time).toMillis() / 1000.0
            if (dt <= 0) continue
            seconds[zoneOf(hr, limits)] += dt.coerceAtMost(MAX_GAP_S)
            any = true
        }
        return if (any) ZoneTimes(seconds) else null
    }

    /**
     * The zones of one activity: exact from its track, or, when the track has no heart rate but the average is known, all
     * of the time in the average's zone (marked as a guess). Null without any heart rate.
     */
    fun activityZones(activity: Activity, maxHr: Int): ActivityZones? {
        if (maxHr <= 0) return null
        timeInZones(activity.track, maxHr)?.let { return ActivityZones(it, exact = true) }
        val avg = activity.avgHr ?: return null
        if (avg <= 0 || activity.durationS <= 0) return null
        return ActivityZones(timesOf(zoneOf(avg, limits(maxHr)), activity.durationS), exact = false)
    }

    private fun day(a: Activity): LocalDate = a.startTime.atZone(ZoneOffset.UTC).toLocalDate()

    /** The last [WEEKS] blocks of 7 days ending [today], oldest first, from activities that carry their tracks. */
    fun weeks(activities: List<Activity>, maxHr: Int, today: LocalDate): List<WeekZones> =
        (WEEKS - 1 downTo 0).map { i ->
            val end = today.minusDays(i * 7L)
            val start = end.minusDays(6)
            var times = ZoneTimes()
            var count = 0
            var exact = true
            for (a in activities) {
                val d = day(a)
                if (d.isBefore(start) || d.isAfter(end)) continue
                val z = activityZones(a, maxHr) ?: continue
                times += z.times
                count++
                if (!z.exact) exact = false
            }
            WeekZones(start, times, count, exact)
        }

    /** The easy, middle and hard share of the zoned time; null with less than [MIN_VERDICT_SECONDS] of it. */
    fun split(times: ZoneTimes): ZoneSplit? {
        val zoned = times.zoned
        if (zoned < MIN_VERDICT_SECONDS) return null
        val s = times.seconds
        return ZoneSplit(easy = (s[1] + s[2]) / zoned, middle = s[3] / zoned, hard = (s[4] + s[5]) / zoned)
    }

    fun verdict(split: ZoneSplit?): ZoneVerdict = when {
        split == null -> ZoneVerdict.NO_DATA
        split.hard > HARD_SHARE -> ZoneVerdict.TOO_HARD
        split.middle > MIDDLE_SHARE -> ZoneVerdict.TOO_MIDDLE
        split.hard < NO_HARD_SHARE -> ZoneVerdict.ALL_EASY
        else -> ZoneVerdict.BALANCED
    }

    /**
     * Everything for the Zones tab. [all] (summaries are enough) decides the measured maximum; [recent] must carry tracks
     * and cover the last four weeks. The verdict is about the latest week.
     */
    fun overview(all: List<Activity>, recent: List<Activity>, setting: Int, today: LocalDate): ZoneOverview {
        val max = maxHr(setting, all)
        if (max.bpm <= 0) return ZoneOverview(max, emptyList(), emptyList(), null, ZoneVerdict.NO_DATA)
        val weeks = weeks(recent, max.bpm, today)
        val split = split(weeks.last().times)
        return ZoneOverview(max, ranges(max.bpm), weeks, split, verdict(split))
    }
}
