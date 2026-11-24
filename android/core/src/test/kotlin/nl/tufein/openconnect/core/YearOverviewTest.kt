package nl.tufein.openconnect.core

import java.time.Instant
import java.time.LocalDate
import java.time.Month
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class YearOverviewTest {
    private fun day(date: String) = LocalDate.parse(date)

    private fun at(instant: String) = Instant.parse(instant)

    private fun month(overview: YearOverview, month: Month) = overview.months.single { it.month == month }

    @Test
    fun `months are always the twelve calendar months in order`() {
        val overview = YearOverviewCalculator.compute(listOf(TestData.daily(day("2026-03-04"))), emptyList(), emptyList(), 2026)!!
        assertEquals(12, overview.months.size)
        assertEquals(Month.values().toList(), overview.months.map { it.month })
    }

    @Test
    fun `steps are summed per month and resting heart rate averaged`() {
        val overview = YearOverviewCalculator.compute(
            listOf(
                TestData.daily(day("2026-03-01"), steps = 8000),
                TestData.daily(day("2026-03-02"), steps = 12_000),
                TestData.daily(day("2026-04-01"), steps = 500),
            ),
            emptyList(),
            emptyList(),
            2026,
        )!!
        assertEquals(20_000L, month(overview, Month.MARCH).steps)
        assertEquals(500L, month(overview, Month.APRIL).steps)
        assertEquals(52, month(overview, Month.MARCH).restingHr)
        assertNull(month(overview, Month.MAY).steps)
        assertNull(month(overview, Month.MAY).restingHr)
    }

    @Test
    fun `sleep is averaged per month over every night and trainings are counted`() {
        val overview = YearOverviewCalculator.compute(
            emptyList(),
            listOf(TestData.sleep(day("2026-06-01"), hours = 7.0), TestData.sleep(day("2026-06-02"), hours = 8.0)),
            listOf(
                TestData.activity(id = "a", start = at("2026-06-03T08:00:00Z")),
                TestData.activity(id = "b", start = at("2026-06-20T08:00:00Z")),
                TestData.activity(id = "c", start = at("2026-07-01T08:00:00Z")),
            ),
            2026,
        )!!
        assertEquals(7.5, month(overview, Month.JUNE).sleepHours!!, 1e-9)
        assertEquals(2, month(overview, Month.JUNE).trainings)
        assertEquals(1, month(overview, Month.JULY).trainings)
        assertEquals(0, month(overview, Month.AUGUST).trainings)
        assertNull(month(overview, Month.JULY).sleepHours)
    }

    @Test
    fun `resting heart rate ignores zero and null values and rounds`() {
        val overview = YearOverviewCalculator.compute(
            listOf(
                DailySummary(day("2026-01-01"), restingHr = 50),
                DailySummary(day("2026-01-02"), restingHr = 61),
                DailySummary(day("2026-01-03"), restingHr = 0),
                DailySummary(day("2026-01-04"), restingHr = null),
                DailySummary(day("2026-02-01"), restingHr = 60),
            ),
            emptyList(),
            emptyList(),
            2026,
        )!!
        assertEquals(56, month(overview, Month.JANUARY).restingHr)
        assertEquals(60, month(overview, Month.FEBRUARY).restingHr)
        assertEquals(58, overview.restingHrAvg)
    }

    @Test
    fun `a month with a zero step day is zero while an empty month is null`() {
        val overview = YearOverviewCalculator.compute(
            listOf(DailySummary(day("2026-01-15"), steps = 0)),
            listOf(SleepSummary(day("2026-02-01"), durationS = 0)),
            emptyList(),
            2026,
        )!!
        val jan = month(overview, Month.JANUARY)
        assertEquals(0L, jan.steps)
        assertNull(jan.sleepHours)
        assertNull(jan.restingHr)
        val feb = month(overview, Month.FEBRUARY)
        assertNull(feb.steps)
        assertEquals(0.0, feb.sleepHours!!, 1e-9)
        assertNull(feb.restingHr)
        assertEquals(0L, overview.stepsTotal)
        assertTrue(overview.hasData)
    }

    @Test
    fun `a zero hour night still counts as a night`() {
        val overview = YearOverviewCalculator.compute(
            emptyList(),
            listOf(TestData.sleep(day("2026-01-01"), hours = 0.0), TestData.sleep(day("2026-01-02"), hours = 8.0)),
            emptyList(),
            2026,
        )!!
        assertEquals(4.0, month(overview, Month.JANUARY).sleepHours!!, 1e-9)
        assertEquals(4.0, overview.sleepHoursAvg!!, 1e-9)
    }

    @Test
    fun `activities use their UTC date so a late night stays in its own year`() {
        val dailies = listOf(TestData.daily(day("2025-12-31")))
        val sleeps = listOf(TestData.sleep(day("2026-01-01")))
        val activities = listOf(
            TestData.activity(id = "late", start = at("2025-12-31T23:30:00Z")),
            TestData.activity(id = "early", start = at("2026-01-01T00:30:00Z")),
        )
        val lastYear = YearOverviewCalculator.compute(dailies, sleeps, activities, 2025)!!
        val thisYear = YearOverviewCalculator.compute(dailies, sleeps, activities, 2026)!!

        assertEquals(1, month(lastYear, Month.DECEMBER).trainings)
        assertEquals(0, month(lastYear, Month.JANUARY).trainings)
        assertEquals(1, month(thisYear, Month.JANUARY).trainings)
        assertNotNull(month(thisYear, Month.JANUARY).sleepHours)
        assertNull(month(thisYear, Month.DECEMBER).steps)
    }

    @Test
    fun `the years come from dailies sleeps and activities newest first`() {
        val years = YearOverviewCalculator.years(
            listOf(TestData.daily(day("2024-05-05")), DailySummary(day("2022-01-01"), steps = 0)),
            listOf(TestData.sleep(day("2023-01-01"))),
            listOf(
                TestData.activity(id = "a", start = at("2026-01-01T00:00:00Z")),
                TestData.activity(id = "b", start = at("2025-06-01T00:00:00Z")),
            ),
        )
        assertEquals(listOf(2026, 2025, 2024, 2023, 2022), years)
    }

    @Test
    fun `years are distinct and empty input has none`() {
        assertEquals(emptyList<Int>(), YearOverviewCalculator.years(emptyList(), emptyList(), emptyList()))
        assertEquals(
            listOf(2026),
            YearOverviewCalculator.years(
                listOf(TestData.daily(day("2026-01-01"))),
                listOf(TestData.sleep(day("2026-02-01"))),
                listOf(TestData.activity(id = "a", start = at("2026-03-01T00:00:00Z"))),
            ),
        )
    }

    @Test
    fun `compute returns null for a year without any data`() {
        assertNull(YearOverviewCalculator.compute(emptyList(), emptyList(), emptyList(), 2026))
        val dailies = listOf(TestData.daily(day("2026-01-01")))
        assertNull(YearOverviewCalculator.compute(dailies, emptyList(), emptyList(), 2025))
        assertNotNull(YearOverviewCalculator.compute(dailies, emptyList(), emptyList(), 2026))
    }

    @Test
    fun `the year totals and averages only use months with data`() {
        val overview = YearOverviewCalculator.compute(
            listOf(
                DailySummary(day("2026-01-01"), steps = 1000, restingHr = 50),
                DailySummary(day("2026-01-02"), steps = 2000, restingHr = 52),
                DailySummary(day("2026-03-01"), steps = 4000, restingHr = 60),
            ),
            listOf(TestData.sleep(day("2026-01-01"), hours = 7.0), TestData.sleep(day("2026-03-01"), hours = 8.0)),
            listOf(
                TestData.activity(id = "a", start = at("2026-01-10T08:00:00Z")),
                TestData.activity(id = "b", start = at("2026-03-10T08:00:00Z")),
                TestData.activity(id = "c", start = at("2026-03-11T08:00:00Z")),
            ),
            2026,
        )!!
        assertEquals(7000L, overview.stepsTotal)
        assertEquals(7.5, overview.sleepHoursAvg!!, 1e-9)
        assertEquals(56, overview.restingHrAvg)
        assertEquals(3, overview.trainingsTotal)
        assertTrue(overview.hasData)
    }

    @Test
    fun `february in a leap year includes the 29th`() {
        val dailies = listOf(
            TestData.daily(day("2024-02-28"), steps = 1000),
            TestData.daily(day("2024-02-29"), steps = 2000),
        )
        val sleeps = listOf(TestData.sleep(day("2024-02-29"), hours = 6.0))
        val activities = listOf(TestData.activity(id = "leap", start = at("2024-02-29T12:00:00Z")))
        val overview = YearOverviewCalculator.compute(dailies, sleeps, activities, 2024)!!
        val feb = month(overview, Month.FEBRUARY)
        assertEquals(3000L, feb.steps)
        assertEquals(6.0, feb.sleepHours!!, 1e-9)
        assertEquals(1, feb.trainings)
        assertEquals(3000L, overview.stepsTotal)
        assertNull(YearOverviewCalculator.compute(dailies, sleeps, activities, 2023))
    }

    @Test
    fun `an overview without any figures has no data`() {
        val empty = YearOverview(2026, Month.values().map { MonthOverview(it, null, null, null, 0) })
        assertFalse(empty.hasData)
        assertNull(empty.stepsTotal)
        assertNull(empty.sleepHoursAvg)
        assertNull(empty.restingHrAvg)
        assertEquals(0, empty.trainingsTotal)
    }

    @Test
    fun `a year with only sleep has no steps total but does have data`() {
        val overview = YearOverviewCalculator.compute(emptyList(), listOf(TestData.sleep(day("2026-01-01"))), emptyList(), 2026)!!
        assertNull(overview.stepsTotal)
        assertNull(overview.restingHrAvg)
        assertEquals(0, overview.trainingsTotal)
        assertTrue(overview.hasData)
    }
}
