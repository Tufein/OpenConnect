package nl.tufein.openconnect.core

import java.time.Instant
import java.time.LocalDate
import java.time.Month
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class YearScoreTest {
    private fun at(date: String) = Instant.parse("${date}T08:00:00Z")

    private fun act(
        id: String,
        date: String,
        sport: Sport = Sport.RUNNING,
        km: Double = 5.0,
        minutes: Int = 30,
        climb: Double = 0.0,
    ) = Activity(id = id, name = id, sport = sport, startTime = at(date), durationS = minutes * 60.0, distanceM = km * 1000, elevationGainM = climb)

    @Test
    fun `a year without activities or steps has no score`() {
        assertNull(YearScoreCalculator.compute(emptyList(), emptyList(), 2026))
        assertNull(YearScoreCalculator.compute(listOf(act("a", "2025-06-01")), emptyList(), 2026))
    }

    @Test
    fun `it adds up the year and leaves other years out`() {
        val score = YearScoreCalculator.compute(
            listOf(act("a", "2026-03-01", km = 10.0, minutes = 60), act("b", "2026-03-02", km = 5.0, minutes = 30), act("old", "2025-12-31", km = 99.0)),
            emptyList(),
            2026,
        )!!
        assertEquals(2, score.activities)
        assertEquals(15.0, score.distanceKm, 1e-9)
        assertEquals(1.5, score.hours, 1e-9)
        assertEquals(2, score.activeDays)
        assertEquals(99.0, score.previousDistanceKm, 1e-9)
    }

    @Test
    fun `active days count a day once and the longest streak needs days in a row`() {
        val score = YearScoreCalculator.compute(
            listOf(
                act("a", "2026-05-01"), act("b2", "2026-05-01"), act("c", "2026-05-02"), act("d", "2026-05-03"),
                act("e", "2026-05-10"), act("f", "2026-05-11"),
            ),
            emptyList(),
            2026,
        )!!
        assertEquals(5, score.activeDays)
        assertEquals(3, score.longestStreakDays)
    }

    @Test
    fun `the records are the longest run and ride and the biggest climb with their dates`() {
        val score = YearScoreCalculator.compute(
            listOf(
                act("short", "2026-02-01", km = 5.0), act("long", "2026-02-08", km = 21.1, climb = 80.0),
                act("ride", "2026-04-04", sport = Sport.CYCLING, km = 60.0, climb = 700.0), act("ride2", "2026-04-05", sport = Sport.CYCLING, km = 30.0),
            ),
            emptyList(),
            2026,
        )!!
        assertEquals("long", score.longestRun!!.activityId)
        assertEquals(21.1, score.longestRun!!.value, 1e-9)
        assertEquals(LocalDate.parse("2026-02-08"), score.longestRun!!.date)
        assertEquals("ride", score.longestRide!!.activityId)
        assertEquals("ride", score.biggestClimb!!.activityId)
        assertEquals(700.0, score.biggestClimb!!.value, 1e-9)
    }

    @Test
    fun `no run or no climb means no record for it`() {
        val score = YearScoreCalculator.compute(listOf(act("w", "2026-02-01", sport = Sport.WALKING, km = 3.0)), emptyList(), 2026)!!
        assertNull(score.longestRun)
        assertNull(score.longestRide)
        assertNull(score.biggestClimb)
    }

    @Test
    fun `the busiest month is the one with the most kilometres`() {
        val score = YearScoreCalculator.compute(
            listOf(act("a", "2026-01-10", km = 10.0), act("b", "2026-07-01", km = 8.0), act("c", "2026-07-20", km = 8.0)),
            emptyList(),
            2026,
        )!!
        assertEquals(Month.JULY, score.busiestMonth)
    }

    @Test
    fun `steps come from the daily summaries and the best day is kept`() {
        val dailies = listOf(
            DailySummary(LocalDate.parse("2026-03-01"), steps = 8000),
            DailySummary(LocalDate.parse("2026-03-02"), steps = 21_000),
            DailySummary(LocalDate.parse("2025-03-02"), steps = 99_000),
        )
        val score = YearScoreCalculator.compute(listOf(act("a", "2026-03-01")), dailies, 2026)!!
        assertEquals(29_000L, score.steps)
        assertEquals(LocalDate.parse("2026-03-02") to 21_000, score.bestStepsDay)
    }

    @Test
    fun `a year with only steps still has a score and the change against last year is a percentage`() {
        val onlySteps = YearScoreCalculator.compute(emptyList(), listOf(DailySummary(LocalDate.parse("2026-03-01"), steps = 5000)), 2026)
        assertNotNull(onlySteps)
        assertEquals(0, onlySteps.activities)
        assertNull(onlySteps.distanceChangePct)
        val up = YearScoreCalculator.compute(listOf(act("a", "2026-03-01", km = 150.0), act("b", "2025-03-01", km = 100.0)), emptyList(), 2026)!!
        assertEquals(50.0, up.distanceChangePct!!, 1e-9)
    }

    @Test
    fun `the years you can look at are the years with data, newest first`() {
        val years = YearScoreCalculator.years(listOf(act("a", "2024-03-01"), act("b", "2026-03-01")), listOf(DailySummary(LocalDate.parse("2025-03-01"), steps = 1000)))
        assertEquals(listOf(2026, 2025, 2024), years)
    }
}
