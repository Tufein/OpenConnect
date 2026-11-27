package nl.tufein.openconnect.core

import java.time.Instant
import java.time.LocalDate
import java.time.Month
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** v1.13.12 (M15 "Terugblik"): this year against last year, a month grid over all years, year summaries and a record timeline. */
class LookBackTest {
    private fun day(date: String) = LocalDate.parse(date)

    /** An activity without a track: [km] kilometres in [minutes] minutes on [date] at 08:00 UTC. */
    private fun act(
        id: String,
        date: String,
        km: Double = 5.0,
        minutes: Int = 30,
        sport: Sport = Sport.RUNNING,
        climb: Double = 12.0,
    ) = TestData.activity(
        id = id,
        sport = sport,
        start = Instant.parse("${date}T08:00:00Z"),
        distanceM = km * 1000,
        durationS = minutes * 60.0,
        track = emptyList(),
    ).copy(elevationGainM = climb)

    // ---- this year against last year -------------------------------------------------------------------------

    @Test
    fun `this year is counted up to today and last year up to the same day`() {
        val activities = listOf(
            act("a", "2026-02-01", km = 10.0, minutes = 60),
            act("b", "2026-06-10", km = 5.0, minutes = 30),
            act("c", "2025-03-01", km = 20.0, minutes = 120),
            act("d", "2025-06-14", km = 8.0, minutes = 40),
            act("e", "2025-06-16", km = 100.0, minutes = 600), // after the same day last year: only in the full year
        )
        val c = LookBackCalculator.comparison(activities, day("2026-06-15"), null)!!
        assertEquals(2026, c.year)
        assertEquals(2025, c.previousYear)
        assertEquals(LookBackTotals(2, 15_000.0, 5400.0), c.thisYear)
        assertEquals(LookBackTotals(2, 28_000.0, 9600.0), c.previousToDate)
        assertEquals(LookBackTotals(3, 128_000.0, 45_600.0), c.previousFull)
        assertEquals(-13_000.0, c.distanceDiffM, 1e-9)
        assertEquals(-4200.0, c.durationDiffS, 1e-9)
        assertEquals(0, c.countDiff)
    }

    @Test
    fun `a leap day is compared with the 28th of February a year earlier`() {
        val activities = listOf(
            act("a", "2028-02-29", km = 3.0),
            act("b", "2027-02-28", km = 4.0),
            act("c", "2027-03-01", km = 50.0),
        )
        val c = LookBackCalculator.comparison(activities, day("2028-02-29"), null)!!
        assertEquals(day("2028-02-29"), c.asOf)
        assertEquals(3000.0, c.thisYear.distanceM, 1e-9)
        assertEquals(4000.0, c.previousToDate.distanceM, 1e-9)
        assertEquals(54_000.0, c.previousFull.distanceM, 1e-9)
    }

    @Test
    fun `an activity dated after today is not counted this year`() {
        val activities = listOf(act("a", "2026-06-10", km = 5.0), act("b", "2026-06-20", km = 7.0))
        val c = LookBackCalculator.comparison(activities, day("2026-06-15"), null)!!
        assertEquals(1, c.thisYear.count)
        assertEquals(5000.0, c.thisYear.distanceM, 1e-9)
    }

    @Test
    fun `the lines are running totals per month and this year's line stops in the current month`() {
        val activities = listOf(
            act("a", "2026-01-10", km = 10.0),
            act("b", "2026-03-05", km = 20.0),
            act("c", "2026-06-01", km = 5.0),
            act("d", "2025-02-02", km = 6.0),
            act("e", "2025-12-30", km = 9.0),
        )
        val c = LookBackCalculator.comparison(activities, day("2026-06-15"), null)!!
        assertEquals(12, c.thisYearLine.size)
        assertEquals(12, c.previousYearLine.size)
        assertEquals(10_000.0, c.thisYearLine[0]!!.distanceM, 1e-9) // January
        assertEquals(10_000.0, c.thisYearLine[1]!!.distanceM, 1e-9) // February: nothing new, the total stays
        assertEquals(30_000.0, c.thisYearLine[2]!!.distanceM, 1e-9) // March
        assertEquals(35_000.0, c.thisYearLine[5]!!.distanceM, 1e-9) // June, up to today
        for (month in 6..11) assertNull(c.thisYearLine[month], "no point after the current month")
        assertEquals(0.0, c.previousYearLine[0].distanceM, 1e-9)
        assertEquals(6000.0, c.previousYearLine[1].distanceM, 1e-9)
        assertEquals(6000.0, c.previousYearLine[10].distanceM, 1e-9)
        assertEquals(15_000.0, c.previousYearLine[11].distanceM, 1e-9) // December: the whole year
        assertEquals(c.previousFull, c.previousYearLine[11])
    }

    @Test
    fun `the comparison can be limited to one sport`() {
        val activities = listOf(
            act("run", "2026-05-01", km = 10.0, sport = Sport.RUNNING),
            act("ride", "2026-05-02", km = 80.0, sport = Sport.CYCLING),
            act("oldRun", "2025-05-01", km = 8.0, sport = Sport.RUNNING),
        )
        val running = LookBackCalculator.comparison(activities, day("2026-06-15"), Sport.RUNNING)!!
        assertEquals(10_000.0, running.thisYear.distanceM, 1e-9)
        assertEquals(8000.0, running.previousFull.distanceM, 1e-9)
        val all = LookBackCalculator.comparison(activities, day("2026-06-15"), null)!!
        assertEquals(90_000.0, all.thisYear.distanceM, 1e-9)
        assertEquals(Sport.RUNNING, running.sport)
        assertNull(all.sport)
    }

    @Test
    fun `there is no comparison when neither year has an activity`() {
        val old = listOf(act("a", "2019-05-01"))
        assertNull(LookBackCalculator.comparison(old, day("2026-06-15"), null))
        assertNull(LookBackCalculator.comparison(emptyList(), day("2026-06-15"), null))
        val swim = listOf(act("a", "2026-05-01", sport = Sport.CYCLING))
        assertNull(LookBackCalculator.comparison(swim, day("2026-06-15"), Sport.SWIMMING))
    }

    @Test
    fun `a year with only last year's activities still compares and shows zero so far`() {
        val c = LookBackCalculator.comparison(listOf(act("a", "2025-05-01", km = 8.0)), day("2026-01-03"), null)!!
        assertEquals(0, c.thisYear.count)
        assertEquals(8000.0, c.previousFull.distanceM, 1e-9)
        assertEquals(0.0, c.previousToDate.distanceM, 1e-9)
    }

    @Test
    fun `the percentage is the change against last year to the same day and unknown when last year had none`() {
        val activities = listOf(act("a", "2026-02-01", km = 15.0), act("b", "2025-02-01", km = 10.0))
        val c = LookBackCalculator.comparison(activities, day("2026-06-15"), null)!!
        assertEquals(50, c.percent(LookBackMetric.DISTANCE))
        val none = LookBackCalculator.comparison(listOf(act("a", "2026-02-01")), day("2026-06-15"), null)!!
        assertNull(none.percent(LookBackMetric.DISTANCE))
    }

    @Test
    fun `sports are listed by how often they happen and the default is running when it is recent`() {
        val activities = listOf(
            act("1", "2026-03-01", sport = Sport.CYCLING),
            act("2", "2026-03-02", sport = Sport.CYCLING),
            act("3", "2026-03-03", sport = Sport.WALKING),
            act("4", "2025-03-03", sport = Sport.RUNNING),
        )
        // Cycling twice; running and walking once each, and a tie goes by the order of the sports in the app.
        assertEquals(listOf(Sport.CYCLING, Sport.RUNNING, Sport.WALKING), LookBackCalculator.sports(activities))
        assertEquals(Sport.RUNNING, LookBackCalculator.defaultSport(activities, day("2026-06-15")))
        val noRun = activities.filter { it.sport != Sport.RUNNING }
        assertEquals(Sport.CYCLING, LookBackCalculator.defaultSport(noRun, day("2026-06-15")))
        assertNull(LookBackCalculator.defaultSport(emptyList(), day("2026-06-15")))
        val longAgo = listOf(act("x", "2019-01-01", sport = Sport.RUNNING))
        assertNull(LookBackCalculator.defaultSport(longAgo, day("2026-06-15")), "nothing in the last two years: all sports")
    }

    // ---- the month grid ----------------------------------------------------------------------------------------

    @Test
    fun `the grid has one row per year from the first to the last, newest first, with empty years in between`() {
        val activities = listOf(act("a", "2022-04-10"), act("b", "2025-01-02"))
        val table = LookBackCalculator.monthTable(activities, null)!!
        assertEquals(listOf(2025, 2024, 2023, 2022), table.years.map { it.year })
        assertTrue(table.years.single { it.year == 2023 }.months.all { it.count == 0 })
        assertEquals(12, table.years.first().months.size)
    }

    @Test
    fun `a cell sums the month and the year total sums the cells`() {
        val activities = listOf(
            act("a", "2024-03-01", km = 10.0, minutes = 60),
            act("b", "2024-03-20", km = 6.0, minutes = 30),
            act("c", "2024-07-04", km = 4.0, minutes = 20),
        )
        val year = LookBackCalculator.monthTable(activities, null)!!.years.single()
        assertEquals(LookBackTotals(2, 16_000.0, 5400.0), year.months[Month.MARCH.value - 1])
        assertEquals(LookBackTotals(1, 4000.0, 1200.0), year.months[Month.JULY.value - 1])
        assertEquals(LookBackTotals(3, 20_000.0, 6600.0), year.total)
    }

    @Test
    fun `the heaviest month depends on the metric and a tie goes to the newest month`() {
        val activities = listOf(
            act("a", "2023-05-01", km = 50.0, minutes = 30),
            act("b", "2024-02-01", km = 10.0, minutes = 300),
            act("c", "2024-08-01", km = 10.0, minutes = 10),
            act("d", "2024-08-09", km = 10.0, minutes = 10),
            act("e", "2023-11-01", km = 50.0, minutes = 30), // same distance as May 2023: newest wins
        )
        val table = LookBackCalculator.monthTable(activities, null)!!
        val byDistance = table.heaviest(LookBackMetric.DISTANCE)!!
        assertEquals(2023, byDistance.year)
        assertEquals(Month.NOVEMBER, byDistance.month)
        val byTime = table.heaviest(LookBackMetric.TIME)!!
        assertEquals(2024, byTime.year)
        assertEquals(Month.FEBRUARY, byTime.month)
        val byCount = table.heaviest(LookBackMetric.COUNT)!!
        assertEquals(2024, byCount.year)
        assertEquals(Month.AUGUST, byCount.month)
        assertEquals(2, byCount.totals.count)
    }

    @Test
    fun `the grid can be limited to one sport and is absent without activities`() {
        val activities = listOf(
            act("run", "2024-03-01", km = 10.0, sport = Sport.RUNNING),
            act("ride", "2024-03-02", km = 80.0, sport = Sport.CYCLING),
        )
        val running = LookBackCalculator.monthTable(activities, Sport.RUNNING)!!
        assertEquals(10_000.0, running.years.single().total.distanceM, 1e-9)
        assertNull(LookBackCalculator.monthTable(activities, Sport.SWIMMING))
        assertNull(LookBackCalculator.monthTable(emptyList(), null))
    }

    // ---- the years ----------------------------------------------------------------------------------------------

    @Test
    fun `each year lists its sports by time with their totals`() {
        val activities = listOf(
            act("1", "2024-03-01", km = 10.0, minutes = 60, sport = Sport.RUNNING),
            act("2", "2024-03-05", km = 100.0, minutes = 200, sport = Sport.CYCLING),
            act("3", "2024-03-09", km = 3.0, minutes = 40, sport = Sport.WALKING),
            act("4", "2023-01-09", km = 1.0, minutes = 10, sport = Sport.SWIMMING),
        )
        val years = LookBackCalculator.yearSummaries(activities, day("2026-01-01"))
        assertEquals(listOf(2024, 2023), years.map { it.year })
        val y2024 = years.first()
        assertEquals(LookBackTotals(3, 113_000.0, 18_000.0), y2024.totals)
        assertEquals(listOf(Sport.CYCLING, Sport.RUNNING, Sport.WALKING), y2024.sports.map { it.sport })
        assertEquals(LookBackTotals(1, 100_000.0, 12_000.0), y2024.sports.first().totals)
    }

    @Test
    fun `the busiest week is the one with the most time, starting on a Monday`() {
        val activities = listOf(
            act("a", "2024-05-06", minutes = 30), // Monday of week one
            act("b", "2024-05-08", minutes = 30),
            act("c", "2024-05-15", minutes = 50), // Wednesday of week two: 50 minutes
            act("d", "2024-05-12", minutes = 40), // Sunday of week one: week one has 100 minutes
        )
        val busiest = LookBackCalculator.yearSummaries(activities, day("2026-01-01")).single().busiestWeek
        assertEquals(day("2024-05-06"), busiest.weekStart)
        assertEquals(LookBackTotals(3, 15_000.0, 6000.0), busiest.totals)
    }

    @Test
    fun `quiet weeks are the weeks without any activity between the first and the last of the year`() {
        val activities = listOf(
            act("a", "2024-05-06"), // week of 6 May
            act("b", "2024-05-27"), // week of 27 May: 13 and 20 May are quiet
            act("c", "2024-06-03"),
        )
        val summary = LookBackCalculator.yearSummaries(activities, day("2026-01-01")).single()
        assertEquals(2, summary.quietWeeks)
    }

    @Test
    fun `in the current year only finished weeks count as quiet`() {
        // The only activity is in the week of Monday 4 May; today is Friday 29 May, and the week of 25 May has just begun.
        val activities = listOf(act("a", "2026-05-06"))
        val summary = LookBackCalculator.yearSummaries(activities, day("2026-05-29")).single()
        // Finished weeks after the activity: 11 May and 18 May. The running week of 25 May is not counted.
        assertEquals(2, summary.quietWeeks)
    }

    @Test
    fun `a year with a single activity has no quiet weeks`() {
        val summary = LookBackCalculator.yearSummaries(listOf(act("a", "2024-05-06")), day("2026-01-01")).single()
        assertEquals(0, summary.quietWeeks)
        assertEquals(1, summary.totals.count)
    }

    // ---- the record timeline -------------------------------------------------------------------------------------

    @Test
    fun `only a beaten record is an event and it names the one it beat`() {
        val activities = listOf(
            act("a", "2024-01-01", km = 5.0, minutes = 30),
            act("b", "2024-02-01", km = 8.0, minutes = 45),
            act("c", "2024-03-01", km = 6.0, minutes = 35), // no record
            act("d", "2024-04-01", km = 10.0, minutes = 55),
        )
        val distance = LookBackCalculator.recordTimeline(activities).filter { it.kind == RecordKind.LONGEST_DISTANCE }
        assertEquals(2, distance.size)
        assertEquals("d", distance[0].activityId) // newest first
        assertEquals(10_000.0, distance[0].value, 1e-9)
        assertEquals(8000.0, distance[0].previousValue, 1e-9)
        assertEquals(day("2024-02-01"), distance[0].previousDate)
        assertEquals(day("2024-04-01"), distance[0].date)
        assertEquals("b", distance[1].activityId)
        assertEquals(5000.0, distance[1].previousValue, 1e-9)
    }

    @Test
    fun `the first activity sets a standing but is not an event, and a tie is not a new record`() {
        val activities = listOf(
            act("a", "2024-01-01", km = 5.0, minutes = 30, climb = 50.0),
            act("b", "2024-02-01", km = 5.0, minutes = 30, climb = 50.0),
        )
        assertTrue(LookBackCalculator.recordTimeline(activities).isEmpty())
        assertTrue(LookBackCalculator.recordTimeline(emptyList()).isEmpty())
    }

    @Test
    fun `zero is never a record`() {
        val activities = listOf(
            act("a", "2024-01-01", km = 5.0, climb = 0.0),
            act("b", "2024-02-01", km = 5.0, climb = 0.0),
            act("c", "2024-03-01", km = 5.0, climb = 10.0),
        )
        val kinds = LookBackCalculator.recordTimeline(activities).map { it.kind }
        assertTrue(RecordKind.MOST_ELEVATION !in kinds, "the first climbing value is a standing, not an event")
    }

    @Test
    fun `each sport keeps its own records and a faster pace is a record only for runs of a kilometre or more`() {
        val activities = listOf(
            act("run1", "2024-01-01", km = 5.0, minutes = 30, sport = Sport.RUNNING), // 6:00 per km
            act("ride1", "2024-01-02", km = 20.0, minutes = 60, sport = Sport.CYCLING),
            act("run2", "2024-02-01", km = 5.0, minutes = 25, sport = Sport.RUNNING), // 5:00 per km, faster
            act("ride2", "2024-02-02", km = 15.0, minutes = 30, sport = Sport.CYCLING), // shorter than the first ride
            act("dash", "2024-03-01", km = 0.5, minutes = 1, sport = Sport.RUNNING), // too short for a pace record
        )
        val events = LookBackCalculator.recordTimeline(activities)
        val pace = events.filter { it.kind == RecordKind.FASTEST_PACE }
        assertEquals(1, pace.size)
        assertEquals("run2", pace.single().activityId)
        assertEquals(300.0, pace.single().value, 1e-9)
        assertEquals(360.0, pace.single().previousValue, 1e-9)
        assertTrue(events.none { it.sport == Sport.CYCLING && it.kind == RecordKind.LONGEST_DISTANCE })
        assertTrue(events.none { it.activityId == "dash" })
    }

    @Test
    fun `the last event of each record is the standing that personalRecords reports`() {
        val activities = listOf(
            act("a", "2023-01-01", km = 5.0, minutes = 30, climb = 20.0),
            act("b", "2023-06-01", km = 12.0, minutes = 70, climb = 90.0),
            act("c", "2024-01-01", km = 9.0, minutes = 80, climb = 40.0),
            act("d", "2024-06-01", km = 15.0, minutes = 80, climb = 200.0),
            act("e", "2025-01-01", km = 21.0, minutes = 100, climb = 60.0),
            act("f", "2025-03-01", km = 10.0, minutes = 45, climb = 10.0),
        )
        val repo = InMemoryRepository()
        activities.forEach(repo::upsertActivity)
        val standing = personalRecords(repo).getValue(Sport.RUNNING)
        val newest = LookBackCalculator.recordTimeline(activities)
            .groupBy { it.kind }
            .mapValues { (_, events) -> events.first() } // newest first
        assertEquals(standing.getValue("longest_distance").value, newest.getValue(RecordKind.LONGEST_DISTANCE).value, 1e-9)
        assertEquals(standing.getValue("longest_duration").value, newest.getValue(RecordKind.LONGEST_DURATION).value, 1e-9)
        assertEquals(standing.getValue("most_elevation").value, newest.getValue(RecordKind.MOST_ELEVATION).value, 1e-9)
        assertEquals(standing.getValue("fastest_pace").value, newest.getValue(RecordKind.FASTEST_PACE).value, 1e-9)
    }

    // ---- the report the screen shows -----------------------------------------------------------------------------

    @Test
    fun `the report picks the default sport until one is chosen and keeps a chosen sport or all`() {
        val activities = listOf(
            act("run", "2026-05-01", sport = Sport.RUNNING),
            act("ride", "2026-05-02", sport = Sport.CYCLING),
            act("run2", "2025-05-01", sport = Sport.RUNNING),
        )
        val today = day("2026-06-15")
        val first = LookBackCalculator.report(activities, today, Sport.CYCLING, chosen = false)
        assertEquals(Sport.RUNNING, first.sport, "not chosen yet: the default wins over what was passed")
        assertEquals(false, first.chosen)
        val all = LookBackCalculator.report(activities, today, null, chosen = true)
        assertNull(all.sport)
        assertEquals(true, all.chosen)
        val cycling = LookBackCalculator.report(activities, today, Sport.CYCLING, chosen = true)
        assertEquals(Sport.CYCLING, cycling.sport)
        assertEquals(listOf(Sport.RUNNING, Sport.CYCLING), cycling.sports)
        val gone = LookBackCalculator.report(activities, today, Sport.SWIMMING, chosen = true)
        assertEquals(Sport.RUNNING, gone.sport, "a sport that has no activities any more falls back to the default")
        assertNotNull(gone.comparison)
    }

    @Test
    fun `an empty history gives an empty report`() {
        val report = LookBackCalculator.report(emptyList(), day("2026-06-15"), null, chosen = false)
        assertTrue(report.isEmpty)
        assertNull(report.comparison)
        assertNull(report.table)
        assertTrue(report.years.isEmpty() && report.records.isEmpty() && report.sports.isEmpty())
    }
}
