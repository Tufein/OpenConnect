package nl.tufein.openconnect.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Display formatting. Pure functions so they can be tested without Android. */
object Fmt {
    fun km(meters: Double, locale: Locale = Locale.getDefault()): String = String.format(locale, "%.2f", meters / 1000)

    fun oneDecimal(value: Double, locale: Locale = Locale.getDefault()): String = String.format(locale, "%.1f", value)

    fun duration(seconds: Double): String {
        val total = Math.rint(seconds).toLong()
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(Locale.ROOT, h, m, s) else "%d:%02d".format(Locale.ROOT, m, s)
    }

    /** Minutes and seconds per kilometre, e.g. `5:33`. */
    fun pace(secondsPerKm: Double?): String {
        if (secondsPerKm == null || secondsPerKm <= 0) return "–"
        val total = Math.rint(secondsPerKm).toLong()
        return "%d:%02d".format(Locale.ROOT, total / 60, total % 60)
    }

    fun speedKmh(metersPerSecond: Double?, locale: Locale = Locale.getDefault()): String =
        if (metersPerSecond == null) "–" else String.format(locale, "%.1f", metersPerSecond * 3.6)

    fun dateTime(instant: Instant, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale).withZone(zone).format(instant)

    fun date(day: LocalDate, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(day)

    fun shortDate(day: LocalDate, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofPattern("d MMM", locale).format(day)

    fun thousands(value: Int, locale: Locale = Locale.getDefault()): String = String.format(locale, "%,d", value)

    fun hours(seconds: Int, locale: Locale = Locale.getDefault()): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        return String.format(locale, "%d:%02d", h, m)
    }
}
