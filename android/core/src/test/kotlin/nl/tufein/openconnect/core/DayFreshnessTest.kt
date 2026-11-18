package nl.tufein.openconnect.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** v0.54.0: the Home card said "Today" over the latest record whatever its date, so it must say which day it really is. */
class DayFreshnessTest {
    private val today = LocalDate.of(2026, 11, 18)

    @Test
    fun aRecordOfTodayIsToday() {
        val f = DayFreshness.of(today, today)
        assertEquals(DayAge.TODAY, f.age)
        assertEquals(today, f.date)
        assertEquals(0, f.daysBehind)
    }

    @Test
    fun aRecordOfYesterdayIsYesterday() {
        val f = DayFreshness.of(today.minusDays(1), today)
        assertEquals(DayAge.YESTERDAY, f.age)
        assertEquals(1, f.daysBehind)
    }

    @Test
    fun anOlderRecordSaysHowManyDaysBehindItIs() {
        val f = DayFreshness.of(today.minusDays(4), today)
        assertEquals(DayAge.EARLIER, f.age)
        assertEquals(4, f.daysBehind)
        assertEquals(today.minusDays(4), f.date)
    }

    @Test
    fun noRecordIsNone() {
        val f = DayFreshness.of(null, today)
        assertEquals(DayAge.NONE, f.age)
        assertNull(f.date)
    }

    @Test
    fun aRecordDatedTomorrowBecauseOfTheTimeZoneStillCountsAsToday() {
        assertEquals(DayAge.TODAY, DayFreshness.of(today.plusDays(1), today).age)
    }

    @Test
    fun onlyAnOldDayNeedsAWarning() {
        assertEquals(false, DayFreshness.of(today, today).isStale)
        assertEquals(true, DayFreshness.of(today.minusDays(1), today).isStale)
        assertEquals(true, DayFreshness.of(today.minusDays(9), today).isStale)
        assertEquals(false, DayFreshness.of(null, today).isStale)
    }
}
