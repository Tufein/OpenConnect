package nl.tufein.openconnect.core

import nl.tufein.openconnect.core.oura.OuraDay
import nl.tufein.openconnect.core.oura.OuraWorkout
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DayInsightsTest {
    private val today = LocalDate.parse("2026-11-17")

    private fun oura(day: LocalDate, readiness: Int? = null, steps: Int? = null, sleepS: Int? = null, hrv: Int? = null, temp: Double? = null, workouts: List<OuraWorkout> = emptyList()) =
        OuraDay(day = day.toString(), readiness = readiness, steps = steps, sleepS = sleepS, averageHrv = hrv, temperatureDeviation = temp, workouts = workouts)

    private fun ouraSet(vararg days: OuraDay) = DayRecords.oura(today.minusDays(40), today, days.toList())

    private fun garminSet(
        dailies: List<DailySummary> = emptyList(),
        sleeps: List<SleepSummary> = emptyList(),
        readiness: List<Pair<LocalDate, Int?>> = emptyList(),
        activities: List<Activity> = emptyList(),
    ) = DayRecords.garmin(today.minusDays(40), today, dailies, sleeps, readiness, activities)

    // ------------------------------------------------------------------------------------------------ records

    @Test
    fun `a garmin day holds the numbers of the watch under one date`() {
        val set = garminSet(
            dailies = listOf(DailySummary(today, steps = 9_500, restingHr = 52, avgStress = 28, bodyBatteryHigh = 80, hrvMs = 61, spo2Avg = 96, respirationAvg = 14.5)),
            sleeps = listOf(SleepSummary(today, durationS = 7 * 3600 + 1800, score = 84)),
            readiness = listOf(today to 77),
        )
        val v = assertNotNull(set.record(today)).values
        assertEquals(9_500.0, v[ChartMetric.STEPS])
        assertEquals(52.0, v[ChartMetric.RESTING_HR])
        assertEquals(28.0, v[ChartMetric.STRESS])
        assertEquals(80.0, v[ChartMetric.BODY_BATTERY])
        assertEquals(61.0, v[ChartMetric.HRV])
        assertEquals(96.0, v[ChartMetric.SPO2])
        assertEquals(14.5, v[ChartMetric.RESPIRATION])
        assertEquals(7.5, v[ChartMetric.SLEEP])
        assertEquals(84.0, v[ChartMetric.SLEEP_SCORE])
        assertEquals(77.0, v[ChartMetric.READINESS])
        // Nothing of the ring leaks in.
        assertNull(v[ChartMetric.ACTIVITY_SCORE])
        assertNull(v[ChartMetric.TEMPERATURE])
        assertNull(v[ChartMetric.LOWEST_HR])
    }

    @Test
    fun `zero readings that mean not measured are left out, a sleep of zero seconds too`() {
        val set = garminSet(
            dailies = listOf(DailySummary(today, steps = 0, restingHr = 0, hrvMs = 0, spo2Avg = 0, respirationAvg = 0.0)),
            sleeps = listOf(SleepSummary(today, durationS = 0)),
        )
        val v = assertNotNull(set.record(today)).values
        assertEquals(setOf(ChartMetric.STEPS), v.keys)
    }

    @Test
    fun `an oura day holds the numbers of the ring and its own scores`() {
        val day = OuraDay(
            day = today.toString(), readiness = 81, sleepScore = 88, activityScore = 72, steps = 8_200, sleepS = 8 * 3600,
            lowestHr = 47, averageHrv = 64, breathAvg = 14.1, spo2Avg = 96.6, temperatureDeviation = -0.3,
        )
        val v = assertNotNull(ouraSet(day).record(today)).values
        assertEquals(81.0, v[ChartMetric.READINESS])
        assertEquals(88.0, v[ChartMetric.SLEEP_SCORE])
        assertEquals(72.0, v[ChartMetric.ACTIVITY_SCORE])
        assertEquals(8.0, v[ChartMetric.SLEEP])
        assertEquals(8_200.0, v[ChartMetric.STEPS])
        assertEquals(47.0, v[ChartMetric.LOWEST_HR])
        assertEquals(64.0, v[ChartMetric.HRV])
        assertEquals(14.1, v[ChartMetric.RESPIRATION])
        assertEquals(96.6, v[ChartMetric.SPO2])
        assertEquals(-0.3, v[ChartMetric.TEMPERATURE])
        // The ring has no body battery, stress or resting heart rate.
        assertNull(v[ChartMetric.BODY_BATTERY])
        assertNull(v[ChartMetric.STRESS])
        assertNull(v[ChartMetric.RESTING_HR])
    }

    @Test
    fun `every day in the range has a record, empty when nothing was measured`() {
        val set = ouraSet(oura(today, steps = 1))
        assertTrue(assertNotNull(set.record(today.minusDays(3))).isEmpty)
        assertNull(set.record(today.plusDays(1)))
        assertEquals(41, (0..40).count { set.record(today.minusDays(it.toLong())) != null })
    }

    @Test
    fun `workouts sit under their day, watch ones by their start, ring ones with the oura id`() {
        val start = today.atTime(7, 0).atZone(java.time.ZoneOffset.UTC).toInstant()
        val run = Activity(id = "a1", name = "Morning run", sport = Sport.RUNNING, startTime = start, durationS = 1800.0, distanceM = 5000.0, calories = 300)
        val g = garminSet(activities = listOf(run))
        val w = g.record(today)!!.workouts.single()
        assertEquals("a1", w.id)
        assertEquals("Morning run", w.name)
        assertEquals(5000.0, w.distanceM)
        assertTrue(g.record(today.minusDays(1))!!.workouts.isEmpty())

        val ring = OuraWorkout("w9", "cycling", null, start.toEpochMilli(), start.toEpochMilli() + 3_600_000, 500, 0.0)
        val o = ouraSet(oura(today, workouts = listOf(ring)))
        val rw = o.record(today)!!.workouts.single()
        assertEquals("oura:w9", rw.id)
        assertEquals(Sport.CYCLING, rw.sport)
        assertEquals(3600.0, rw.durationS)
        assertNull(rw.distanceM, "zero metres is not a distance")
    }

    @Test
    fun `a night run belongs to the day it started on in the phone's own zone`() {
        // 23:30 UTC on the 16th is 00:30 on the 17th in Amsterdam, and still the 16th in UTC.
        val start = LocalDate.parse("2026-11-16").atTime(23, 30).atZone(java.time.ZoneOffset.UTC).toInstant()
        val run = Activity(id = "n1", sport = Sport.RUNNING, startTime = start, durationS = 1200.0)
        val utc = DayRecords.garmin(today.minusDays(5), today, emptyList(), emptyList(), emptyList(), listOf(run))
        assertEquals(1, utc.record(today.minusDays(1))!!.workouts.size)
        val local = DayRecords.garmin(today.minusDays(5), today, emptyList(), emptyList(), emptyList(), listOf(run), java.time.ZoneId.of("Europe/Amsterdam"))
        assertEquals(1, local.record(today)!!.workouts.size)
        assertTrue(local.record(today.minusDays(1))!!.workouts.isEmpty())
    }

    @Test
    fun `the history reaches far enough back for the oldest day on offer`() {
        // The oldest choice is 59 days back; its usual needs 28 days before it and the chart of 30 days ends on it (29 before).
        assertEquals(59 + 29, DayInsights.HISTORY_DAYS)
        assertTrue(DayInsights.HISTORY_DAYS >= DayInsights.CHOICES - 1 + DayInsights.BASELINE_DAYS)
    }

    // ------------------------------------------------------------------------------------------------ the day

    @Test
    fun `a day is read against the average of the days before it`() {
        val days = (1..10).map { oura(today.minusDays(it.toLong()), steps = 8_000) } + oura(today, steps = 9_000)
        val r = DayInsights.of(ouraSet(*days.toTypedArray()), today).readings.single { it.metric == ChartMetric.STEPS }
        assertEquals(9_000.0, r.value)
        assertEquals(8_000.0, r.usual)
        assertEquals(12.5, r.changePct!!, 1e-9)
        assertEquals(true, r.better)
        assertEquals(Verdict.FAIR, r.verdict)
        assertEquals(Extreme.HIGHEST, r.extreme)
    }

    @Test
    fun `the usual needs three earlier readings and the day itself is not part of it`() {
        val two = DayInsights.of(ouraSet(oura(today.minusDays(1), steps = 5_000), oura(today.minusDays(2), steps = 5_000), oura(today, steps = 9_000)), today)
        assertNull(two.readings.single().usual)
        assertNull(two.readings.single().changePct)
        assertNull(two.readings.single().better)
        val three = DayInsights.of(ouraSet(oura(today.minusDays(1), steps = 5_000), oura(today.minusDays(2), steps = 5_000), oura(today.minusDays(3), steps = 5_000), oura(today, steps = 9_000)), today)
        assertEquals(5_000.0, three.readings.single().usual)
    }

    @Test
    fun `only the 28 days before count towards the usual`() {
        val old = oura(today.minusDays(29), steps = 100_000)
        val inside = (1..28).map { oura(today.minusDays(it.toLong()), steps = 6_000) }
        val r = DayInsights.of(ouraSet(*(inside + old + oura(today, steps = 6_000)).toTypedArray()), today).readings.single()
        assertEquals(6_000.0, r.usual)
    }

    @Test
    fun `within five percent of the usual is the same, and for a lower-is-better measure lower is better`() {
        fun lowestHr(now: Int): DayReading {
            val set = DayRecords.oura(today.minusDays(40), today, (1..8).map { OuraDay(day = today.minusDays(it.toLong()).toString(), lowestHr = 50) } + OuraDay(day = today.toString(), lowestHr = now))
            return DayInsights.of(set, today).readings.single()
        }
        assertNull(lowestHr(51).better) // 2% above
        assertEquals(true, lowestHr(46).better) // 8% below the usual 50: better for a heart rate
        assertEquals(false, lowestHr(55).better) // 10% above: worse
    }

    @Test
    fun `highest or lowest in four weeks needs a week of readings and a day beyond all of them`() {
        fun set(now: Int, earlier: Int): DaySeries =
            ouraSet(*((1..earlier).map { oura(today.minusDays(it.toLong()), steps = 8_000 + it * 10) } + oura(today, steps = now)).toTypedArray())
        assertEquals(Extreme.HIGHEST, DayInsights.of(set(20_000, 8), today).readings.single().extreme)
        assertEquals(Extreme.LOWEST, DayInsights.of(set(1_000, 8), today).readings.single().extreme)
        assertNull(DayInsights.of(set(8_025, 8), today).readings.single().extreme, "inside the range")
        assertNull(DayInsights.of(set(20_000, 5), today).readings.single().extreme, "too few days to say")
        assertEquals(Extreme.HIGHEST, DayInsights.of(set(20_000, 6), today).readings.single().extreme, "six before plus the day is seven")
    }

    @Test
    fun `a level is judged by the same limits as a period`() {
        fun verdict(metric: ChartMetric, v: Double) = ChartInsight.verdictOfDay(metric, v, null)
        assertEquals(Verdict.GOOD, verdict(ChartMetric.STEPS, 10_000.0))
        assertEquals(Verdict.WATCH, verdict(ChartMetric.STEPS, 5_000.0))
        assertEquals(Verdict.GOOD, verdict(ChartMetric.SLEEP, 7.5))
        assertEquals(Verdict.WATCH, verdict(ChartMetric.SLEEP, 5.0))
        assertEquals(Verdict.GOOD, verdict(ChartMetric.ACTIVITY_SCORE, 70.0))
        assertEquals(Verdict.FAIR, verdict(ChartMetric.ACTIVITY_SCORE, 69.0))
        assertEquals(Verdict.WATCH, verdict(ChartMetric.ACTIVITY_SCORE, 49.0))
        assertEquals(Verdict.GOOD, verdict(ChartMetric.LOWEST_HR, 55.0))
        assertEquals(Verdict.FAIR, verdict(ChartMetric.LOWEST_HR, 56.0))
        assertEquals(Verdict.WATCH, verdict(ChartMetric.LOWEST_HR, 71.0))
        assertEquals(Verdict.GOOD, verdict(ChartMetric.TEMPERATURE, -0.5))
        assertEquals(Verdict.FAIR, verdict(ChartMetric.TEMPERATURE, 0.8))
        assertEquals(Verdict.WATCH, verdict(ChartMetric.TEMPERATURE, 1.2))
    }

    @Test
    fun `heart rate variability is judged against the person's own usual and not at all without one`() {
        assertNull(ChartInsight.verdictOfDay(ChartMetric.HRV, 40.0, null))
        assertEquals(Verdict.GOOD, ChartInsight.verdictOfDay(ChartMetric.HRV, 55.0, 60.0)) // 92%
        assertEquals(Verdict.FAIR, ChartInsight.verdictOfDay(ChartMetric.HRV, 48.0, 60.0)) // 80%
        assertEquals(Verdict.WATCH, ChartInsight.verdictOfDay(ChartMetric.HRV, 40.0, 60.0)) // 67%
    }

    @Test
    fun `a temperature difference has no percentage and no trend, only the number`() {
        val set = ouraSet(*((1..8).map { oura(today.minusDays(it.toLong()), temp = 0.1) } + oura(today, temp = 0.6)).toTypedArray())
        val r = DayInsights.of(set, today).readings.single()
        assertEquals(0.1, r.usual!!, 1e-9)
        assertNull(r.changePct)
        assertNull(r.better)
        assertEquals(Verdict.FAIR, r.verdict)
        val series = (0 until 10).map { today.minusDays(9L - it) to 0.1 * it }
        assertNull(ChartInsight.of(ChartMetric.TEMPERATURE, series)!!.trend, "a trend of a figure around zero would be nonsense")
    }

    @Test
    fun `measures the source has no number for are listed as missing and the ring is judged by ring measures only`() {
        val d = DayInsights.of(ouraSet(oura(today, readiness = 80, steps = 9_000)), today)
        assertEquals(listOf(ChartMetric.READINESS, ChartMetric.STEPS), d.readings.map { it.metric })
        assertTrue(ChartMetric.SLEEP in d.missing)
        assertTrue(ChartMetric.LOWEST_HR in d.missing)
        assertTrue(ChartMetric.BODY_BATTERY !in d.missing, "the ring has no body battery to miss")
        assertEquals(InsightSource.OURA.metrics.size, d.readings.size + d.missing.size)
        val g = DayInsights.of(garminSet(dailies = listOf(DailySummary(today, steps = 3_000))), today)
        assertEquals(InsightSource.GARMIN.metrics.size, g.readings.size + g.missing.size)
        assertTrue(ChartMetric.BODY_BATTERY in g.missing)
        assertTrue(ChartMetric.ACTIVITY_SCORE !in g.missing)
    }

    @Test
    fun `good and judged count the readings that have a verdict`() {
        val d = DayInsights.of(ouraSet(oura(today, readiness = 85, steps = 3_000, hrv = 50)), today)
        assertEquals(3, d.readings.size)
        assertEquals(2, d.judged, "hrv has no usual, so no verdict")
        assertEquals(1, d.good)
        assertTrue(!d.isEmpty)
    }

    @Test
    fun `a day nothing was measured on is empty`() {
        val d = DayInsights.of(ouraSet(oura(today, steps = 1)), today.minusDays(5))
        assertTrue(d.isEmpty)
        assertEquals(InsightSource.OURA.metrics, d.missing)
    }

    @Test
    fun `a day with only a workout is not empty`() {
        val start = today.atTime(7, 0).atZone(java.time.ZoneOffset.UTC).toInstant()
        val run = Activity(id = "a1", sport = Sport.RUNNING, startTime = start, durationS = 600.0)
        val d = DayInsights.of(garminSet(activities = listOf(run)), today)
        assertTrue(d.readings.isEmpty())
        assertTrue(!d.isEmpty)
        assertEquals(1, d.workouts.size)
    }

    // ------------------------------------------------------------------------------------------------ choosing a day

    @Test
    fun `the series for a chart ends on the chosen day and keeps the gaps`() {
        val set = ouraSet(oura(today, steps = 9_000), oura(today.minusDays(2), steps = 4_000))
        val s = set.series(ChartMetric.STEPS, today, 5)
        assertEquals((4 downTo 0).map { today.minusDays(it.toLong()) }, s.map { it.first })
        assertEquals(listOf(null, null, 4_000.0, null, 9_000.0), s.map { it.second })
    }

    @Test
    fun `the day to show is the wanted one, else the newest with data, else today`() {
        val set = ouraSet(oura(today.minusDays(2), steps = 4_000), oura(today.minusDays(9), steps = 1))
        assertEquals(today.minusDays(5), DayInsights.pick(set, today, today.minusDays(5)))
        assertEquals(today.minusDays(2), DayInsights.pick(set, today, null))
        assertEquals(today, DayInsights.pick(ouraSet(), today, null))
        // A day in the future or past the list is not wanted.
        assertEquals(today.minusDays(2), DayInsights.pick(set, today, today.plusDays(1)))
        assertEquals(today.minusDays(2), DayInsights.pick(set, today, today.minusDays(DayInsights.CHOICES.toLong())))
        assertEquals(today.minusDays(DayInsights.CHOICES - 1L), DayInsights.pick(set, today, today.minusDays(DayInsights.CHOICES - 1L)))
    }

    @Test
    fun `the list holds sixty days, newest first, and stepping stays inside it`() {
        val c = DayInsights.choices(today)
        assertEquals(60, c.size)
        assertEquals(today, c.first())
        assertEquals(today.minusDays(59), c.last())
        assertEquals(today, DayInsights.step(today, 1, today))
        assertEquals(today.minusDays(1), DayInsights.step(today, -1, today))
        assertEquals(today.minusDays(59), DayInsights.step(today.minusDays(59), -1, today))
    }

    @Test
    fun `the newest day with data ignores days after the cut-off`() {
        val set = DayRecords.oura(today.minusDays(5), today.plusDays(2), listOf(oura(today.plusDays(1), steps = 1), oura(today.minusDays(1), steps = 2)))
        assertEquals(today.minusDays(1), set.latestWithData(today))
        assertTrue(set.hasAnyData)
        assertTrue(!ouraSet().hasAnyData)
    }

    @Test
    fun `demo ring days give a full day card`() {
        val days = nl.tufein.openconnect.core.oura.OuraDemo.days(today)
        val set = DayRecords.oura(today.minusDays(40), today, days)
        val d = DayInsights.of(set, today)
        assertEquals(InsightSource.OURA.metrics.size, d.readings.size, "every ring measure is in the demo")
        assertTrue(d.readings.all { it.usual != null })
    }
}
