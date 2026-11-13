package nl.tufein.openconnect.core.reports

import nl.tufein.openconnect.core.weekStart
import java.time.LocalDate

/** The stretch of time a report covers. Weeks start on Monday, months and years on the first day. */
enum class ReportPeriod(val kind: String) {
    WEEK("weekly"),
    MONTH("monthly"),
    YEAR("yearly"),
    ;

    fun start(anyDay: LocalDate): LocalDate = when (this) {
        WEEK -> weekStart(anyDay)
        MONTH -> anyDay.withDayOfMonth(1)
        YEAR -> anyDay.withDayOfYear(1)
    }

    /** The last day, for a period that starts on [start]. */
    fun end(start: LocalDate): LocalDate = when (this) {
        WEEK -> start.plusDays(6)
        MONTH -> start.plusMonths(1).minusDays(1)
        YEAR -> start.plusYears(1).minusDays(1)
    }

    fun shift(start: LocalDate, by: Long): LocalDate = when (this) {
        WEEK -> start.plusWeeks(by)
        MONTH -> start.plusMonths(by)
        YEAR -> start.plusYears(by)
    }
}
