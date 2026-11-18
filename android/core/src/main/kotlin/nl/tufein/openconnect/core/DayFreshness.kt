package nl.tufein.openconnect.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** How old the day is that a "today" card is really showing. */
enum class DayAge { TODAY, YESTERDAY, EARLIER, NONE }

/**
 * Home reads "the latest day with data" and used to call it Today. When the watch has not reached the Garmin account
 * yet, that is yesterday or older, and the numbers then differ from the watch. This says which day it is.
 */
data class DayFreshness(val age: DayAge, val date: LocalDate?, val daysBehind: Int) {
    /** True when the card shows a day before today, which the screen must say. */
    val isStale: Boolean get() = age == DayAge.YESTERDAY || age == DayAge.EARLIER

    companion object {
        fun of(recordDate: LocalDate?, today: LocalDate): DayFreshness {
            if (recordDate == null) return DayFreshness(DayAge.NONE, null, 0)
            // A record dated tomorrow (the day of the data and the phone can disagree around midnight) is still today's.
            val behind = ChronoUnit.DAYS.between(recordDate, today).toInt().coerceAtLeast(0)
            val age = when (behind) {
                0 -> DayAge.TODAY
                1 -> DayAge.YESTERDAY
                else -> DayAge.EARLIER
            }
            return DayFreshness(age, recordDate, behind)
        }
    }
}
