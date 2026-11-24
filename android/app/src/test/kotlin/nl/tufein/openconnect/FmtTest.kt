package nl.tufein.openconnect

import androidx.compose.ui.text.font.FontWeight
import nl.tufein.openconnect.ui.Fmt
import nl.tufein.openconnect.ui.downsample
import nl.tufein.openconnect.ui.inlineMarkdown
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FmtTest {
    @Test
    fun durations() {
        assertEquals("0:00", Fmt.duration(0.0))
        assertEquals("1:00", Fmt.duration(59.6))
        assertEquals("42:10", Fmt.duration(2530.0))
        assertEquals("1:02:05", Fmt.duration(3725.0))
    }

    @Test
    fun pace() {
        assertEquals("5:33", Fmt.pace(333.0))
        assertEquals("4:07", Fmt.pace(247.0))
        assertEquals("–", Fmt.pace(null))
        assertEquals("–", Fmt.pace(0.0))
    }

    @Test
    fun numbersFollowTheGivenLocale() {
        assertEquals("12.35", Fmt.km(12_345.0, Locale.US))
        assertEquals("12,35", Fmt.km(12_345.0, Locale.GERMANY))
        assertEquals("10,432", Fmt.thousands(10_432, Locale.US))
        assertEquals("9.5", Fmt.oneDecimal(9.46, Locale.US))
        assertEquals("25.9", Fmt.speedKmh(7.2, Locale.US))
        assertEquals("7:30", Fmt.hours(27_000, Locale.US))
    }

    @Test
    fun dates() {
        val instant = Instant.parse("2026-05-02T07:30:00Z")
        val text = Fmt.dateTime(instant, ZoneId.of("Europe/Amsterdam"), Locale.US)
        assertTrue("May 2, 2026" in text, text)
        assertTrue("9:30" in text, text) // shown in local time, not UTC
        assertEquals("2 May", Fmt.shortDate(LocalDate.parse("2026-05-02"), Locale.US))
    }

    @Test
    fun daysForTheDayList() {
        val day = LocalDate.parse("2026-11-17")
        assertEquals("Tue 17 Nov", Fmt.dayLabel(day, Locale.US))
        assertEquals("Tuesday 17 November 2026", Fmt.longDate(day, Locale.US))
        // In Dutch the weekday starts with a small letter; the title makes it a capital.
        assertEquals("Dinsdag 17 november 2026", Fmt.longDate(day, Locale("nl", "NL")))
    }

    @Test
    fun germanDatesAndNumbers() {
        val day = LocalDate.parse("2026-11-24")
        assertEquals("24.11.2026", Fmt.date(day, Locale.GERMANY))
        assertEquals("24. Nov.", Fmt.shortDate(day, Locale.GERMANY))
        assertEquals("Di., 24. Nov.", Fmt.dayLabel(day, Locale.GERMANY))
        assertEquals("Dienstag, 24. November 2026", Fmt.longDate(day, Locale.GERMANY))
        assertEquals("November 2026", Fmt.monthYear(YearMonth.from(day), Locale.GERMANY))

        val dateTime = Fmt.dateTime(Instant.parse("2026-11-24T07:30:00Z"), ZoneId.of("Europe/Berlin"), Locale.GERMANY)
        assertTrue("24.11.2026" in dateTime, dateTime)
        assertTrue("08:30" in dateTime, dateTime)

        assertEquals("9,5", Fmt.oneDecimal(9.46, Locale.GERMANY))
        assertEquals("10.432", Fmt.thousands(10_432, Locale.GERMANY))
        assertEquals("25,9", Fmt.speedKmh(7.2, Locale.GERMANY))
    }

    @Test
    fun frenchDatesAndNumbers() {
        val day = LocalDate.parse("2026-11-24")
        assertEquals("24/11/2026", Fmt.date(day, Locale.FRANCE))
        assertEquals("24 nov.", Fmt.shortDate(day, Locale.FRANCE))
        assertEquals("mar. 24 nov.", Fmt.dayLabel(day, Locale.FRANCE))
        assertEquals("Mardi 24 novembre 2026", Fmt.longDate(day, Locale.FRANCE))
        assertEquals("novembre 2026", Fmt.monthYear(YearMonth.from(day), Locale.FRANCE))

        val dateTime = Fmt.dateTime(Instant.parse("2026-11-24T07:30:00Z"), ZoneId.of("Europe/Berlin"), Locale.FRANCE)
        assertTrue("24 nov. 2026" in dateTime, dateTime)
        assertTrue("08:30" in dateTime, dateTime)

        assertEquals("9,5", Fmt.oneDecimal(9.46, Locale.FRANCE))
        assertEquals("12,35", Fmt.km(12_345.0, Locale.FRANCE))
        // French groups thousands with a narrow no-break space (U+202F), not a comma or a full stop.
        assertEquals("10\u202F432", Fmt.thousands(10_432, Locale.FRANCE))
        assertEquals("25,9", Fmt.speedKmh(7.2, Locale.FRANCE))
    }

    @Test
    fun inlineMarkdownBoldsOnlyPairedMarkers() {
        val text = inlineMarkdown("a **bold** and **more** b")
        assertEquals("a bold and more b", text.text)
        assertEquals(2, text.spanStyles.size)
        assertTrue(text.spanStyles.all { it.item.fontWeight == FontWeight.Bold })
        assertEquals("**not closed", inlineMarkdown("**not closed").text)
        assertTrue(inlineMarkdown("<b>html</b> stays literal").text.contains("<b>"))
    }

    @Test
    fun downsampleKeepsShortSeriesAndThinsLongOnes() {
        assertEquals((0 until 10).toList(), downsample((0 until 10).toList(), 300))
        val thinned = downsample((0 until 5000).toList(), 300)
        assertEquals(300, thinned.size)
        assertEquals(0, thinned.first())
        assertTrue(thinned.last() > 4900)
    }
}
