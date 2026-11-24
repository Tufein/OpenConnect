package nl.tufein.openconnect.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** The phone's locale as an observable read: a composable that formats with it redraws when the user changes it. */
@Composable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

/** Display formatting. Pure functions so they can be tested without Android. */
object Fmt {
    /**
     * German puts a dot after the day number and after the short month ("24. Nov."), and separates weekday and date
     * with a comma in a title. French, English and Dutch keep the compact forms the app has always shown; their month
     * and weekday names still come from the locale.
     */
    private fun shortDatePattern(locale: Locale): String = if (locale.language == "de") "d. MMM" else "d MMM"

    private fun dayLabelPattern(locale: Locale): String = if (locale.language == "de") "EEE, d. MMM" else "EEE d MMM"

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

    /** The time of day only, such as `7:05 AM` or `07:05`, on the phone's clock. */
    fun time(instant: Instant, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).withZone(zone).format(instant)

    /** A date on its own: German `24.11.2026`, French `24/11/2026`, English `Nov 24, 2026` and Dutch `24 nov. 2026`. */
    fun date(day: LocalDate, locale: Locale = Locale.getDefault()): String {
        // French CLDR writes a numeric short date with the year, which is the form a French reader expects.
        val style = if (locale.language == "fr") FormatStyle.SHORT else FormatStyle.MEDIUM
        return DateTimeFormatter.ofLocalizedDate(style).withLocale(locale).format(day)
    }

    fun shortDate(day: LocalDate, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofPattern(shortDatePattern(locale), locale).format(day)

    /** A day for a list to choose from: weekday, day and month, such as `Tue 17 Nov` or `Di., 17. Nov.`. */
    fun dayLabel(day: LocalDate, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofPattern(dayLabelPattern(locale), locale).format(day)

    /** A day in full for a title: `Tuesday 17 November 2026`, `Dienstag, 17. November 2026` or `Mardi 17 novembre 2026`. */
    fun longDate(day: LocalDate, locale: Locale = Locale.getDefault()): String {
        val text = if (locale.language == "de" || locale.language == "fr") {
            DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)
        } else {
            DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", locale)
        }.format(day)
        return text.replaceFirstChar { it.uppercase() }
    }

    /** The month as a heading, such as `November 2026` or `novembre 2026`, in the language of [locale]. */
    fun monthYear(month: YearMonth, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofPattern("LLLL yyyy", locale).format(month)

    fun monthYear(day: LocalDate, locale: Locale = Locale.getDefault()): String = monthYear(YearMonth.from(day), locale)

    fun thousands(value: Int, locale: Locale = Locale.getDefault()): String = String.format(locale, "%,d", value)

    fun hours(seconds: Int, locale: Locale = Locale.getDefault()): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        return String.format(locale, "%d:%02d", h, m)
    }
}
