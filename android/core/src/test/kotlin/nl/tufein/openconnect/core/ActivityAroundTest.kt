package nl.tufein.openconnect.core

import nl.tufein.openconnect.core.oura.OuraDay
import nl.tufein.openconnect.core.oura.OuraWorkout
import nl.tufein.openconnect.core.watch.Metric
import nl.tufein.openconnect.core.watch.MetricSample
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** v0.43.0: an activity is filled in with what the watch and the Oura Ring measured around it. */
class ActivityAroundTest {
    private val zone = ZoneId.of("Europe/Brussels")
    private val day = LocalDate.of(2026, 11, 10)

    private fun at(hour: Int, minute: Int = 0, on: LocalDate = day): Instant = on.atTime(hour, minute).atZone(zone).toInstant()

    private fun reading(metric: Metric, time: Instant, value: Double) = MetricSample(metric, time, time.atZone(zone).toLocalDate(), value)

    /** An evening run from 18:00 to 19:00 with its own heart rate. */
    private val run = Activity(id = "a1", source = "garmin-ble", name = "Evening run", sport = Sport.RUNNING, startTime = at(18), durationS = 3600.0, avgHr = 150, maxHr = 172)

    private fun around(
        activity: Activity = run,
        daily: DailySummary? = null,
        sleep: SleepSummary? = null,
        samples: List<MetricSample> = emptyList(),
        ringDays: List<OuraDay> = emptyList(),
        useWatch: Boolean = true,
        useRing: Boolean = false,
    ) = ActivityAroundCalculator.compute(activity, zone, daily, sleep, samples, ringDays, useWatch, useRing)

    @Test
    fun bodyBatteryAtTheStartAndTheEndIsTheReadingClosestToThoseMoments() {
        val watch = around(
            samples = listOf(
                reading(Metric.BODY_BATTERY, at(12), 80.0),
                reading(Metric.BODY_BATTERY, at(17, 50), 62.0),
                reading(Metric.BODY_BATTERY, at(18, 20), 57.0),
                reading(Metric.BODY_BATTERY, at(18, 58), 48.0),
                reading(Metric.BODY_BATTERY, at(21), 44.0),
            ),
        ).watch
        assertNotNull(watch)
        assertEquals(62, watch.bodyBatteryStart)
        assertEquals(48, watch.bodyBatteryEnd)
    }

    @Test
    fun aReadingMoreThanHalfAnHourAwayIsNotPassedOffAsTheStartOrTheEnd() {
        val watch = around(samples = listOf(reading(Metric.BODY_BATTERY, at(16), 70.0), reading(Metric.BODY_BATTERY, at(20), 40.0)), daily = DailySummary(day, restingHr = 52)).watch
        assertNotNull(watch)
        assertNull(watch.bodyBatteryStart)
        assertNull(watch.bodyBatteryEnd)
    }

    @Test
    fun stressIsTheAverageOfTheHourBeforeAndOfTheHourAfter() {
        val watch = around(
            samples = listOf(
                reading(Metric.STRESS, at(16, 30), 99.0), // more than an hour before: not counted
                reading(Metric.STRESS, at(17, 10), 20.0),
                reading(Metric.STRESS, at(17, 40), 30.0),
                reading(Metric.STRESS, at(17, 50), 0.0), // 0 is "could not measure", not calm
                reading(Metric.STRESS, at(18, 30), 90.0), // during the run: belongs to neither
                reading(Metric.STRESS, at(19, 10), 40.0),
                reading(Metric.STRESS, at(19, 50), 50.0),
                reading(Metric.STRESS, at(20, 30), 99.0),
            ),
        ).watch
        assertNotNull(watch)
        assertEquals(25, watch.stressBefore)
        assertEquals(45, watch.stressAfter)
    }

    @Test
    fun heartRateFromTheDayReadingsOnlyFillsInWhenTheActivityHasNoneOfItsOwn() {
        val readings = listOf(
            reading(Metric.HEART_RATE, at(17, 55), 70.0),
            reading(Metric.HEART_RATE, at(18, 10), 140.0),
            reading(Metric.HEART_RATE, at(18, 40), 161.0),
            reading(Metric.HEART_RATE, at(19, 5), 110.0),
        )
        val withOwn = around(samples = readings + reading(Metric.BODY_BATTERY, at(18), 60.0)).watch
        assertNotNull(withOwn)
        assertNull(withOwn.heartRateAvg, "the activity's own heart rate is the better one")
        val without = around(activity = run.copy(avgHr = null, maxHr = null), samples = readings).watch
        assertNotNull(without)
        assertEquals(151, without.heartRateAvg) // (140 + 161) / 2 = 150.5
        assertEquals(161, without.heartRateMax)
    }

    @Test
    fun theDayAndTheNightBeforeComeFromTheWatchsOwnSummaries() {
        val night = SleepSummary(day, durationS = 7 * 3600 + 12 * 60, score = 81)
        val watch = around(
            daily = DailySummary(day, steps = 12_400, restingHr = 52, hrvMs = 58, avgStress = 31, bodyBatteryHigh = 85, bodyBatteryLow = 23),
            sleep = night,
        ).watch
        assertNotNull(watch)
        assertEquals(52, watch.restingHr)
        assertEquals(58, watch.hrvMs)
        assertEquals(31, watch.dayStress)
        assertEquals(85, watch.dayBodyBatteryHigh)
        assertEquals(23, watch.dayBodyBatteryLow)
        assertEquals(night, watch.sleep)
    }

    @Test
    fun aDayOrNightOfAnotherDateIsNotUsed() {
        val result = around(daily = DailySummary(day.minusDays(1), restingHr = 52), sleep = SleepSummary(day.minusDays(1), durationS = 25_000))
        assertNull(result.watch)
        assertEquals(listOf(AroundGap.WATCH_NOTHING), result.gaps)
    }

    @Test
    fun theRingGivesItsMorningScoresAndItsOwnRecordOfTheSameEffort() {
        val same = OuraWorkout("w-evening", "running", null, at(18, 5).toEpochMilli(), at(18, 55).toEpochMilli(), calories = 610, distanceM = 9_800.0)
        val other = OuraWorkout("w-morning", "walking", null, at(8).toEpochMilli(), at(8, 30).toEpochMilli(), calories = 90)
        val ringDay = OuraDay(
            day = day.toString(), readiness = 78, sleepScore = 80, sleepS = 25_200, lowestHr = 48, averageHrv = 58,
            temperatureDeviation = 0.1, workouts = listOf(other, same),
        )
        val result = around(ringDays = listOf(OuraDay(day.minusDays(1).toString(), readiness = 12), ringDay), useRing = true)
        val ring = result.ring
        assertNotNull(ring)
        assertEquals(78, ring.readiness)
        assertEquals(80, ring.sleepScore)
        assertEquals(25_200, ring.sleepS)
        assertEquals(48, ring.lowestHr)
        assertEquals(58, ring.averageHrv)
        assertEquals(0.1, ring.temperatureDeviation)
        assertEquals(same, ring.workout)
    }

    @Test
    fun aRingWorkoutOnAnotherRingDayIsStillFoundByItsTime() {
        // Oura files a late workout under its own day, which need not be the day on the phone's clock.
        val late = OuraWorkout("w", "running", null, at(18, 5).toEpochMilli(), at(18, 55).toEpochMilli())
        val ring = around(ringDays = listOf(OuraDay(day.plusDays(1).toString(), workouts = listOf(late))), useRing = true).ring
        assertNotNull(ring)
        assertEquals(late, ring.workout)
        assertNull(ring.readiness)
    }

    @Test
    fun whatIsMissingIsNamedInsteadOfLeftOut() {
        // Day totals but nothing per minute: this is what an activity from the Garmin account or a file has.
        val dayOnly = around(daily = DailySummary(day, restingHr = 52))
        assertEquals(listOf(AroundGap.WATCH_NO_READINGS_AROUND), dayOnly.gaps)

        // Readings around it: nothing to apologise for.
        val full = around(daily = DailySummary(day, restingHr = 52), samples = listOf(reading(Metric.BODY_BATTERY, at(18), 60.0)))
        assertEquals(emptyList(), full.gaps)

        // The ring was chosen but has no day here.
        val noRingDay = around(samples = listOf(reading(Metric.BODY_BATTERY, at(18), 60.0)), useRing = true)
        assertNull(noRingDay.ring)
        assertEquals(listOf(AroundGap.RING_NO_DAY), noRingDay.gaps)

        // The ring has the day: its heart rate during the effort is still something OpenConnect does not read.
        val withRing = around(samples = listOf(reading(Metric.BODY_BATTERY, at(18), 60.0)), ringDays = listOf(OuraDay(day.toString(), readiness = 70)), useRing = true)
        assertEquals(listOf(AroundGap.RING_NO_HEART_RATE), withRing.gaps)
    }

    @Test
    fun aDeviceThatWasNotChosenIsNeverMentioned() {
        val ringOnly = around(daily = DailySummary(day, restingHr = 52), ringDays = listOf(OuraDay(day.toString(), readiness = 70)), useWatch = false, useRing = true)
        assertNull(ringOnly.watch)
        assertEquals(listOf(AroundGap.RING_NO_HEART_RATE), ringOnly.gaps)
        val watchOnly = around(ringDays = listOf(OuraDay(day.toString(), readiness = 70)), useRing = false)
        assertNull(watchOnly.ring)
        assertEquals(listOf(AroundGap.WATCH_NOTHING), watchOnly.gaps)
        assertTrue(around(useWatch = false, useRing = false).isEmpty)
    }

    @Test
    fun anActivityThatIsTheRingsOwnWorkoutDoesNotListItselfAsItsMatch() {
        val workout = OuraWorkout("w1", "walking", null, at(8).toEpochMilli(), at(8, 30).toEpochMilli(), calories = 90)
        val ringDay = OuraDay(day.toString(), readiness = 70, workouts = listOf(workout))
        val ring = around(activity = RingWorkouts.toActivity(workout), ringDays = listOf(ringDay), useRing = true).ring
        assertNotNull(ring)
        assertEquals(70, ring.readiness)
        assertNull(ring.workout)
    }

    @Test
    fun theDayIsTheOneOnThePersonsOwnClock() {
        // 23:30 in Brussels is 22:30 UTC: still the 10th there, and the night that ended on the 10th belongs to it.
        val late = run.copy(startTime = at(23, 30), durationS = 1200.0)
        val result = ActivityAroundCalculator.compute(
            late, zone, DailySummary(day, restingHr = 51), SleepSummary(day, durationS = 26_000), emptyList(), emptyList(), useWatch = true, useRing = false,
        )
        assertEquals(51, result.watch?.restingHr)
        assertEquals(day, ActivityAroundCalculator.dayOf(late, zone))
        assertEquals(day.plusDays(1), ActivityAroundCalculator.dayOf(late.copy(startTime = at(0, 10, day.plusDays(1))), zone))
    }

    @Test
    fun itReadsWhatItNeedsFromTheRepository() {
        val repo = InMemoryRepository()
        repo.upsertDaily(DailySummary(day, restingHr = 52, hrvMs = 58))
        repo.upsertDaily(DailySummary(day.minusDays(1), restingHr = 40))
        repo.upsertSleep(SleepSummary(day, durationS = 25_920, score = 81))
        repo.upsertSamples(listOf(reading(Metric.BODY_BATTERY, at(17, 55), 62.0), reading(Metric.BODY_BATTERY, at(19, 2), 48.0)))
        repo.upsertOuraDay(OuraDay(day.toString(), readiness = 78, sleepScore = 80))
        val result = ActivityAroundCalculator.load(repo, run, zone, useWatch = true, useRing = true)
        assertEquals(WatchAround(bodyBatteryStart = 62, bodyBatteryEnd = 48, restingHr = 52, hrvMs = 58, sleep = SleepSummary(day, durationS = 25_920, score = 81)), result.watch)
        assertEquals(78, result.ring?.readiness)
        assertEquals(listOf(AroundGap.RING_NO_HEART_RATE), result.gaps)
    }
}
