package nl.tufein.openconnect

import androidx.compose.ui.text.font.FontWeight
import nl.tufein.openconnect.ui.Fmt
import nl.tufein.openconnect.ui.downsample
import nl.tufein.openconnect.ui.inlineMarkdown
import java.time.Instant
import java.time.LocalDate
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
