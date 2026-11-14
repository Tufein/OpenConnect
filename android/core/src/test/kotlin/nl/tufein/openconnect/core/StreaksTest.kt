package nl.tufein.openconnect.core

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StreaksTest {
    // Saturday 14 November 2026.
    private val today = LocalDate.of(2026, 11, 14)

    private fun act(day: LocalDate, id: String = "a-$day", km: Double = 5.0, name: String = "StreakRun", hour: Int = 8) = Activity(
        id = id,
        name = name,
        startTime = day.atTime(hour, 0).toInstant(ZoneOffset.UTC),
        distanceM = km * 1000.0,
    )

    private fun days(vararg offsets: Int) = offsets.map { act(today.minusDays(it.toLong())) }

    @Test
    fun aMonthListsEachDaysActivitiesInOrderWithTheirTotals() {
        val nov = LocalDate.of(2026, 11, 3)
        val view = Streaks.month(
            listOf(act(nov, "late", 3.0, "Evening", hour = 19), act(nov, "early", 10.0, "Morning", hour = 6), act(LocalDate.of(2026, 10, 31)), act(LocalDate.of(2026, 12, 1))),
            YearMonth.of(2026, 11),
        )
        assertEquals(1, view.activeDays)
        val d = view.days.getValue(nov)
        assertEquals(2, d.count)
        assertEquals(13_000.0, d.distanceM, 1e-9)
        assertEquals(listOf("early", "late"), d.items.map { it.id })
        assertEquals(13_000.0, view.totalDistanceM, 1e-9)
        assertEquals(2, view.totalCount)
    }

    @Test
    fun theFirstOfTheMonthLandsOnTheRightWeekdayWithMondayFirst() {
        // 1 November 2026 is a Sunday: six blanks. 1 June 2026 is a Monday: none.
        assertEquals(6, Streaks.month(emptyList(), YearMonth.of(2026, 11)).leadingBlanks)
        assertEquals(0, Streaks.month(emptyList(), YearMonth.of(2026, 6)).leadingBlanks)
    }

    @Test
    fun anActivityWithoutANameIsListedByItsSport() {
        val view = Streaks.month(listOf(act(today, name = "")), YearMonth.of(2026, 11))
        assertEquals("other", view.days.getValue(today).items.single().name)
    }

    @Test
    fun theFirstMonthIsTheOneOfTheOldestActivity() {
        assertNull(Streaks.firstMonth(emptyList()))
        assertEquals(YearMonth.of(2026, 9), Streaks.firstMonth(listOf(act(today), act(LocalDate.of(2026, 9, 30)))))
    }

    @Test
    fun theCurrentStreakCountsBackFromTodayAndTheLongestIsFoundAnywhere() {
        // today, yesterday, 2 days ago | gap | 5, 6, 7, 8, 9 days ago
        val s = Streaks.compute(days(0, 1, 2, 5, 6, 7, 8, 9), today)
        assertEquals(StreakRun(3, today.minusDays(2), today), s.currentDays)
        assertEquals(StreakRun(5, today.minusDays(9), today.minusDays(5)), s.longestDays)
        assertFalse(s.todayOpen)
    }

    @Test
    fun aStreakThatEndedYesterdayIsStillAliveAndTodayIsOpen() {
        val s = Streaks.compute(days(1, 2, 3), today)
        assertEquals(StreakRun(3, today.minusDays(3), today.minusDays(1)), s.currentDays)
        assertTrue(s.todayOpen)
    }

    @Test
    fun aStreakThatEndedTwoDaysAgoIsOver() {
        val s = Streaks.compute(days(2, 3, 4), today)
        assertEquals(StreakRun.NONE, s.currentDays)
        assertEquals(3, s.longestDays.length)
        assertFalse(s.todayOpen)
    }

    @Test
    fun severalActivitiesOnADayCountOnce() {
        val s = Streaks.compute(listOf(act(today, "1"), act(today, "2", hour = 17), act(today.minusDays(1), "3")), today)
        assertEquals(2, s.currentDays.length)
    }

    @Test
    fun ofTwoEquallyLongRunsTheMostRecentIsTheLongest() {
        val s = Streaks.compute(days(0, 1, 10, 11), today)
        assertEquals(StreakRun(2, today.minusDays(1), today), s.longestDays)
    }

    @Test
    fun weeksStartOnMondayAndOneActivityMakesAWeek() {
        // Weeks of 26 Oct, 2 Nov and 9 Nov (this week) have an activity; the week of 19 Oct does not; 12 Oct does.
        val s = Streaks.compute(
            listOf(
                act(LocalDate.of(2026, 10, 14)), act(LocalDate.of(2026, 10, 28)), act(LocalDate.of(2026, 11, 8)), act(LocalDate.of(2026, 11, 9)),
            ),
            today,
        )
        assertEquals(StreakRun(3, LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 9)), s.currentWeeks)
        assertEquals(s.currentWeeks, s.longestWeeks)
    }

    @Test
    fun aWeekStreakSurvivesTheStartOfANewWeekWithoutAnActivityYet() {
        val monday = LocalDate.of(2026, 11, 16)
        val s = Streaks.compute(listOf(act(LocalDate.of(2026, 11, 3)), act(LocalDate.of(2026, 11, 10))), monday)
        assertEquals(StreakRun(2, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 9)), s.currentWeeks)
        // Two weeks without anything breaks it.
        assertEquals(StreakRun.NONE, Streaks.compute(listOf(act(LocalDate.of(2026, 11, 3))), monday.plusDays(7)).currentWeeks)
    }

    @Test
    fun noActivitiesMeansNoStreaks() {
        val s = Streaks.compute(emptyList(), today)
        assertEquals(StreakSummary(StreakRun.NONE, StreakRun.NONE, StreakRun.NONE, StreakRun.NONE, false), s)
    }
}
