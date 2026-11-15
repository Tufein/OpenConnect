package nl.tufein.openconnect.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JournalTest {
    private val d0 = LocalDate.parse("2026-06-01")

    private fun daily(day: LocalDate, hrv: Int? = 60, rhr: Int? = 52) = DailySummary(date = day, hrvMs = hrv, restingHr = rhr)

    private fun sleep(day: LocalDate, hours: Double = 7.5) = SleepSummary(date = day, durationS = (hours * 3600).toInt())

    @Test
    fun `a tag is switched on and off for a day and an empty day disappears`() {
        var book = JournalBook()
        book = book.toggle(d0, JournalTag.ALCOHOL).toggle(d0, JournalTag.CAFFEINE)
        assertEquals(setOf(JournalTag.ALCOHOL, JournalTag.CAFFEINE), book.tags(d0))
        book = book.toggle(d0, JournalTag.ALCOHOL)
        assertEquals(setOf(JournalTag.CAFFEINE), book.tags(d0))
        book = book.toggle(d0, JournalTag.CAFFEINE)
        assertTrue(book.days.isEmpty())
        assertEquals(emptySet(), book.tags(d0.plusDays(5)))
    }

    @Test
    fun `the book is written and read back, and bad lines are skipped`() {
        val book = JournalBook().toggle(d0, JournalTag.ALCOHOL).toggle(d0, JournalTag.LATE_MEAL).toggle(d0.plusDays(2), JournalTag.TRAVEL)
        val text = book.encode()
        assertEquals("2026-06-01|alcohol,late_meal\n2026-06-03|travel\n", text)
        assertEquals(book, JournalBook.decode(text))
        val messy = "nonsense\n2026-06-04|unknown,stress\n2026-13-45|alcohol\n2026-06-05|\n\n2026-06-06|ill"
        assertEquals(mapOf(d0.plusDays(3) to setOf(JournalTag.STRESS), d0.plusDays(5) to setOf(JournalTag.ILL)), JournalBook.decode(messy).days)
    }

    /** 24 days; alcohol on every fifth day, and the night after it is worse on all three measures. */
    private fun scenario(drop: Boolean = true): Triple<JournalBook, List<DailySummary>, List<SleepSummary>> {
        var book = JournalBook()
        val dailies = ArrayList<DailySummary>()
        val sleeps = ArrayList<SleepSummary>()
        for (i in 0 until 25) {
            val day = d0.plusDays(i.toLong())
            val afterAlcohol = i >= 1 && (i - 1) % 5 == 0 && i - 1 < 24 && drop
            dailies += daily(day, hrv = if (afterAlcohol) 40 else 60, rhr = if (afterAlcohol) 58 else 52)
            sleeps += sleep(day, hours = if (afterAlcohol) 6.5 else 7.5)
            if (i < 24) book = book.toggle(day, JournalTag.CAFFEINE.takeIf { i % 2 == 0 } ?: JournalTag.STRESS)
            if (i % 5 == 0 && i < 24) book = book.toggle(day, JournalTag.ALCOHOL)
        }
        return Triple(book, dailies, sleeps)
    }

    @Test
    fun `a tag that goes with worse nights shows up as a notable effect`() {
        val (book, dailies, sleeps) = scenario()
        val effects = JournalInsights.effects(book, dailies, sleeps).filter { it.tag == JournalTag.ALCOHOL }
        assertEquals(setOf(JournalMetric.HRV, JournalMetric.RESTING_HR, JournalMetric.SLEEP_HOURS), effects.map { it.metric }.toSet())
        val hrv = effects.first { it.metric == JournalMetric.HRV }
        assertEquals(40.0, hrv.withTag, 1e-9)
        assertEquals(60.0, hrv.without, 1e-9)
        assertEquals(-20.0, hrv.difference, 1e-9)
        assertTrue(hrv.notable)
        assertTrue(hrv.worse)
        val rhr = effects.first { it.metric == JournalMetric.RESTING_HR }
        assertTrue(rhr.notable && rhr.worse)
        val hours = effects.first { it.metric == JournalMetric.SLEEP_HOURS }
        assertTrue(hours.notable && hours.worse)
        assertEquals(5, hrv.taggedNights)
    }

    @Test
    fun `no difference means nothing notable`() {
        val (book, dailies, sleeps) = scenario(drop = false)
        val effects = JournalInsights.effects(book, dailies, sleeps).filter { it.tag == JournalTag.ALCOHOL }
        assertTrue(effects.isNotEmpty())
        assertFalse(effects.any { it.notable })
    }

    @Test
    fun `a tag needs enough nights before it says anything`() {
        var book = JournalBook()
        for (i in 0 until 3) book = book.toggle(d0.plusDays(i * 5L), JournalTag.ALCOHOL)
        val dailies = (0 until 25).map { daily(d0.plusDays(it.toLong())) }
        val sleeps = (0 until 25).map { sleep(d0.plusDays(it.toLong())) }
        assertTrue(JournalInsights.effects(book, dailies, sleeps).none { it.tag == JournalTag.ALCOHOL })
    }

    @Test
    fun `days before the first entry and nights without data are left out`() {
        val (book, dailies, sleeps) = scenario()
        // Strip the first four days of data and one tagged night's HRV: nothing may crash and the counts drop by one.
        val damaged = dailies.map { if (it.date == d0.plusDays(6)) it.copy(hrvMs = null) else it }
        val hrv = JournalInsights.effects(book, damaged, sleeps).first { it.tag == JournalTag.ALCOHOL && it.metric == JournalMetric.HRV }
        assertEquals(4, hrv.taggedNights)
        // A night from before the first entry (HRV 1) must not drag the average of the other nights down.
        val early = JournalInsights.effects(book, dailies + daily(d0.minusDays(3), hrv = 1), sleeps)
        assertEquals(60.0, early.first { it.tag == JournalTag.ALCOHOL && it.metric == JournalMetric.HRV }.without, 1e-9)
    }
}
