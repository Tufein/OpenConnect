package nl.tufein.openconnect.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HeartZonesTest {
    private val today = LocalDate.of(2026, 11, 14)

    private fun start(day: LocalDate): Instant = day.atTime(8, 0).toInstant(ZoneOffset.UTC)

    /** A track with one sample per second at a steady heart rate: [seconds] seconds, so [seconds] + 1 samples. */
    private fun steady(day: LocalDate, hr: Int?, seconds: Int): List<TrackPoint> =
        (0..seconds).map { TrackPoint(time = start(day).plusSeconds(it.toLong()), hr = hr) }

    private fun run(id: String, day: LocalDate, hr: Int?, seconds: Int, maxHr: Int? = null, avgHr: Int? = null) = Activity(
        id = id,
        startTime = start(day),
        durationS = seconds.toDouble(),
        avgHr = avgHr,
        maxHr = maxHr,
        track = steady(day, hr, seconds),
    )

    private fun times(vararg s: Double) = ZoneTimes(s.toList())

    @Test
    fun theLimitsAreFiftyToNinetyPercentOfTheMaximum() {
        assertEquals(listOf(100, 120, 140, 160, 180), HeartZones.limits(200))
        val ranges = HeartZones.ranges(200)
        assertEquals(ZoneRange(1, 100, 119), ranges[0])
        assertEquals(ZoneRange(4, 160, 179), ranges[3])
        assertEquals(ZoneRange(5, 180, null), ranges[4])
        // Rounded, not cut: 50 percent of 185 is 92.5.
        assertEquals(93, HeartZones.limits(185)[0])
    }

    @Test
    fun aHeartRateFallsInTheZoneWhoseLowerLimitItHasReached() {
        val limits = HeartZones.limits(200)
        assertEquals(0, HeartZones.zoneOf(99, limits))
        assertEquals(1, HeartZones.zoneOf(100, limits))
        assertEquals(1, HeartZones.zoneOf(119, limits))
        assertEquals(2, HeartZones.zoneOf(120, limits))
        assertEquals(5, HeartZones.zoneOf(180, limits))
        assertEquals(5, HeartZones.zoneOf(230, limits))
    }

    @Test
    fun aBelievableTypedInMaximumWinsOverTheMeasuredOne() {
        val seen = listOf(run("a", today, 150, 10, maxHr = 191))
        assertEquals(MaxHr(185, MaxHrSource.SETTING), HeartZones.maxHr(185, seen))
        assertEquals(MaxHr(191, MaxHrSource.MEASURED), HeartZones.maxHr(0, seen))
        // Nonsense in the setting is ignored.
        assertEquals(MaxHrSource.MEASURED, HeartZones.maxHr(50, seen).source)
        assertEquals(MaxHrSource.MEASURED, HeartZones.maxHr(300, seen).source)
    }

    @Test
    fun theMeasuredMaximumFallsBackToTheAverageAndIsCapped() {
        assertEquals(158, HeartZones.maxHr(0, listOf(run("a", today, null, 10, avgHr = 158))).bpm)
        // A wrist sensor that spiked to 252 does not set a maximum of 252.
        assertEquals(HeartZones.MAX_MAX_HR, HeartZones.maxHr(0, listOf(run("a", today, null, 10, maxHr = 252))).bpm)
    }

    @Test
    fun withoutAnyHeartRateThereIsNoMaximum() {
        assertEquals(MaxHr(0, MaxHrSource.NONE), HeartZones.maxHr(0, emptyList()))
        assertEquals(MaxHrSource.NONE, HeartZones.maxHr(0, listOf(run("a", today, null, 10, maxHr = 90))).source)
    }

    @Test
    fun everySampleCountsForTheSecondsUntilTheNextOne() {
        // 10 s at 130 (zone 2 of 200) and 20 s at 170 (zone 4); the last sample has nothing after it.
        val track = steady(today, 130, 10).dropLast(1) + steady(today, 170, 30).drop(10)
        val z = HeartZones.timeInZones(track, 200)!!
        assertEquals(listOf(0.0, 0.0, 10.0, 0.0, 20.0, 0.0), z.seconds)
        assertEquals(30.0, z.total, 1e-9)
        assertEquals(30.0, z.zoned, 1e-9)
    }

    @Test
    fun timeBelowTheFirstZoneIsKeptApartFromTheZones() {
        val z = HeartZones.timeInZones(steady(today, 90, 60), 200)!!
        assertEquals(60.0, z.seconds[0], 1e-9)
        assertEquals(0.0, z.zoned, 1e-9)
    }

    @Test
    fun aLongGapCountsForFifteenSecondsOnly() {
        val track = listOf(
            TrackPoint(time = start(today), hr = 150),
            TrackPoint(time = start(today).plusSeconds(600), hr = 150),
            TrackPoint(time = start(today).plusSeconds(601), hr = 150),
        )
        val z = HeartZones.timeInZones(track, 200)!!
        assertEquals(15.0 + 1.0, z.seconds[3], 1e-9)
    }

    @Test
    fun missingAndZeroHeartRatesAreSkipped() {
        val track = listOf(
            TrackPoint(time = start(today), hr = null),
            TrackPoint(time = start(today).plusSeconds(1), hr = 0),
            TrackPoint(time = start(today).plusSeconds(2), hr = 150),
            TrackPoint(time = start(today).plusSeconds(3), hr = 150),
        )
        assertEquals(1.0, HeartZones.timeInZones(track, 200)!!.total, 1e-9)
        assertNull(HeartZones.timeInZones(steady(today, null, 30), 200))
        assertNull(HeartZones.timeInZones(steady(today, 150, 30), 0))
    }

    @Test
    fun anActivityWithoutSamplesIsAGuessFromItsAverage() {
        val guess = Activity(id = "g", startTime = start(today), durationS = 600.0, avgHr = 150)
        val z = HeartZones.activityZones(guess, 200)!!
        assertFalse(z.exact)
        assertEquals(600.0, z.times.seconds[3], 1e-9)
        val exact = HeartZones.activityZones(run("e", today, 150, 60), 200)!!
        assertTrue(exact.exact)
        assertEquals(60.0, exact.times.seconds[3], 1e-9)
        assertNull(HeartZones.activityZones(Activity(id = "n", startTime = start(today), durationS = 600.0), 200))
        assertNull(HeartZones.activityZones(guess, 0))
    }

    @Test
    fun weeksAreFourBlocksOfSevenDaysEndingToday() {
        val activities = listOf(
            run("today", today, 150, 100),
            run("last-week", today.minusDays(8), 170, 200),
            run("edge", today.minusDays(20), 130, 50), // first day of the third block
            run("first-day", today.minusDays(27), 130, 40), // first day of the oldest block
            run("too-old", today.minusDays(28), 130, 999),
        )
        val weeks = HeartZones.weeks(activities, 200, today)
        assertEquals(4, weeks.size)
        assertEquals(today.minusDays(27), weeks[0].start)
        assertEquals(today.minusDays(6), weeks[3].start)
        assertEquals(40.0, weeks[0].times.seconds[2], 1e-9)
        assertEquals(50.0, weeks[1].times.seconds[2], 1e-9)
        assertEquals(200.0, weeks[2].times.seconds[4], 1e-9)
        assertEquals(100.0, weeks[3].times.seconds[3], 1e-9)
        assertEquals(listOf(1, 1, 1, 1), weeks.map { it.activities })
        assertTrue(weeks.all { it.exact })
    }

    @Test
    fun aWeekIsInexactAsSoonAsOneActivityWasAGuess() {
        val guess = Activity(id = "g", startTime = start(today), durationS = 600.0, avgHr = 150)
        val weeks = HeartZones.weeks(listOf(run("a", today, 150, 100), guess), 200, today)
        assertFalse(weeks.last().exact)
        assertEquals(2, weeks.last().activities)
        assertEquals(700.0, weeks.last().times.seconds[3], 1e-9)
        assertTrue(weeks.first().exact)
        assertEquals(0, weeks.first().activities)
    }

    @Test
    fun theSplitIsEasyMiddleAndHardAndNeedsHalfAnHour() {
        val split = HeartZones.split(times(0.0, 1500.0, 1200.0, 300.0, 400.0, 200.0))!!
        assertEquals(0.75, split.easy, 1e-9)
        assertEquals(300.0 / 3600, split.middle, 1e-9)
        assertEquals(600.0 / 3600, split.hard, 1e-9)
        assertEquals(1.0, split.easy + split.middle + split.hard, 1e-9)
        assertNull(HeartZones.split(times(0.0, 1799.0, 0.0, 0.0, 0.0, 0.0)))
        assertNotNull(HeartZones.split(times(0.0, 1800.0, 0.0, 0.0, 0.0, 0.0)))
        // Time below zone 1 is not part of the shares.
        assertEquals(split, HeartZones.split(times(5000.0, 1500.0, 1200.0, 300.0, 400.0, 200.0)))
    }

    @Test
    fun theVerdictFollowsTheShares() {
        fun verdict(vararg s: Double) = HeartZones.verdict(HeartZones.split(times(*s)))
        assertEquals(ZoneVerdict.NO_DATA, verdict(0.0, 600.0, 0.0, 0.0, 0.0, 0.0))
        assertEquals(ZoneVerdict.ALL_EASY, verdict(0.0, 1200.0, 600.0, 0.0, 0.0, 0.0))
        assertEquals(ZoneVerdict.TOO_HARD, verdict(0.0, 900.0, 900.0, 360.0, 1080.0, 360.0))
        assertEquals(ZoneVerdict.TOO_MIDDLE, verdict(0.0, 600.0, 600.0, 1800.0, 300.0, 300.0))
        assertEquals(ZoneVerdict.BALANCED, verdict(0.0, 1500.0, 1200.0, 300.0, 400.0, 200.0))
    }

    @Test
    fun theLimitsBetweenVerdictsBelongToTheCalmerOne() {
        fun verdict(vararg s: Double) = HeartZones.verdict(HeartZones.split(times(*s)))
        // Exactly 30 percent hard is still balanced; exactly 5 percent hard is not "all easy".
        assertEquals(ZoneVerdict.BALANCED, verdict(0.0, 1260.0, 1260.0, 0.0, 1080.0, 0.0))
        assertEquals(ZoneVerdict.BALANCED, verdict(0.0, 1800.0, 1620.0, 0.0, 180.0, 0.0))
        assertEquals(ZoneVerdict.ALL_EASY, verdict(0.0, 1800.0, 1621.0, 0.0, 179.0, 0.0))
        // Exactly 40 percent in the middle is not too much of it.
        assertEquals(ZoneVerdict.BALANCED, verdict(0.0, 1080.0, 720.0, 1440.0, 360.0, 0.0))
        // Too hard wins over too middle.
        assertEquals(ZoneVerdict.TOO_HARD, verdict(0.0, 0.0, 0.0, 2000.0, 1600.0, 0.0))
    }

    @Test
    fun theOverviewTakesTheMaximumFromEverythingAndTheWeeksFromTheTracks() {
        val summaries = listOf(run("old", today.minusDays(90), 150, 10, maxHr = 200))
        val recent = listOf(run("steady", today.minusDays(1), 130, 3600), run("hill", today, 170, 600))
        val o = HeartZones.overview(summaries, recent, setting = 0, today = today)
        assertEquals(MaxHr(200, MaxHrSource.MEASURED), o.maxHr)
        assertEquals(5, o.ranges.size)
        assertEquals(4, o.weeks.size)
        assertEquals(o.weeks.last(), o.thisWeek)
        // 3600 s in zone 2 and 600 s in zone 4: 14 percent hard, so balanced.
        assertEquals(ZoneVerdict.BALANCED, o.verdict)
        assertEquals(600.0 / 4200, o.split!!.hard, 1e-9)
        // A typed-in maximum moves the zones: at 170 the same heart rates are all hard or middle.
        val typed = HeartZones.overview(summaries, recent, setting = 170, today = today)
        assertEquals(MaxHrSource.SETTING, typed.maxHr.source)
        assertEquals(ZoneVerdict.TOO_MIDDLE, typed.verdict)
    }

    @Test
    fun withoutAnyHeartRateTheOverviewIsEmptyAndNoDataIsTheVerdict() {
        val o = HeartZones.overview(emptyList(), emptyList(), setting = 0, today = today)
        assertEquals(MaxHrSource.NONE, o.maxHr.source)
        assertTrue(o.ranges.isEmpty() && o.weeks.isEmpty())
        assertNull(o.thisWeek)
        assertEquals(ZoneVerdict.NO_DATA, o.verdict)
    }
}
