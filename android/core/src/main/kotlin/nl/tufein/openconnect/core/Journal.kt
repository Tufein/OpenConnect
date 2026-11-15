package nl.tufein.openconnect.core

import java.time.LocalDate
import kotlin.math.abs

/** The things a day can be marked with, the way WHOOP's journal and Oura's tags do. */
enum class JournalTag(val key: String) {
    ALCOHOL("alcohol"),
    LATE_MEAL("late_meal"),
    CAFFEINE("caffeine"),
    STRESS("stress"),
    TRAVEL("travel"),
    ILL("ill"),
    ;

    companion object {
        fun of(key: String): JournalTag? = entries.firstOrNull { it.key == key }
    }
}

/** The tags per day. A day with no tags is not kept. */
data class JournalBook(val days: Map<LocalDate, Set<JournalTag>> = emptyMap()) {
    fun tags(date: LocalDate): Set<JournalTag> = days[date].orEmpty()

    /** The book with [tag] switched on for [date] if it was off, and off if it was on. */
    fun toggle(date: LocalDate, tag: JournalTag): JournalBook {
        val now = tags(date).let { if (tag in it) it - tag else it + tag }
        return JournalBook(if (now.isEmpty()) days - date else days + (date to now))
    }

    /** One line per day, oldest first: `2026-06-01|alcohol,late_meal`. */
    fun encode(): String = days.toSortedMap().entries.joinToString("") { (date, tags) ->
        "$date|${tags.sortedBy { it.ordinal }.joinToString(",") { it.key }}\n"
    }

    companion object {
        /** Reads what [encode] wrote. A line that makes no sense, or a tag it does not know, is skipped. */
        fun decode(text: String): JournalBook {
            val days = LinkedHashMap<LocalDate, Set<JournalTag>>()
            for (line in text.lines()) {
                val parts = line.split('|')
                if (parts.size != 2) continue
                val date = runCatching { LocalDate.parse(parts[0]) }.getOrNull() ?: continue
                val tags = parts[1].split(',').mapNotNull { JournalTag.of(it) }.toSet()
                if (tags.isNotEmpty()) days[date] = tags
            }
            return JournalBook(days)
        }
    }
}

enum class JournalMetric { HRV, RESTING_HR, SLEEP_HOURS }

/**
 * How the night after a day with [tag] compares with the nights after the other days, for one [metric]. [withTag] and
 * [without] are averages over [taggedNights] and [otherNights] nights.
 */
data class TagEffect(
    val tag: JournalTag,
    val metric: JournalMetric,
    val withTag: Double,
    val without: Double,
    val taggedNights: Int,
    val otherNights: Int,
) {
    val difference: Double get() = withTag - without

    /** Big enough to mention: HRV 5 percent, resting heart rate 2 beats, sleep 24 minutes. */
    val notable: Boolean
        get() = when (metric) {
            JournalMetric.HRV -> without > 0 && abs(difference) / without >= 0.05
            JournalMetric.RESTING_HR -> abs(difference) >= 2.0
            JournalMetric.SLEEP_HOURS -> abs(difference) >= 0.4
        }

    /** Lower HRV, a higher resting heart rate and shorter sleep are the worse direction. */
    val worse: Boolean
        get() = when (metric) {
            JournalMetric.RESTING_HR -> difference > 0
            else -> difference < 0
        }
}

/**
 * What the journal says about your nights. A tag on day D is compared with the night that ends on D+1: HRV and resting heart
 * rate of that day and the sleep that ended on it. Days before the first entry are left out, so are nights without data, and a
 * tag needs at least [MIN_NIGHTS] nights with it and without it. This shows what goes together, not what causes what.
 */
object JournalInsights {
    const val MIN_NIGHTS = 4

    fun effects(book: JournalBook, dailies: List<DailySummary>, sleeps: List<SleepSummary>): List<TagEffect> {
        val first = book.days.keys.minOrNull() ?: return emptyList()
        val byDay = dailies.associateBy { it.date }
        val sleepByDay = sleeps.associateBy { it.date }
        fun value(metric: JournalMetric, night: LocalDate): Double? = when (metric) {
            JournalMetric.HRV -> byDay[night]?.hrvMs?.takeIf { it > 0 }?.toDouble()
            JournalMetric.RESTING_HR -> byDay[night]?.restingHr?.takeIf { it > 0 }?.toDouble()
            JournalMetric.SLEEP_HOURS -> sleepByDay[night]?.takeIf { it.durationS > 0 }?.hours
        }
        val lastNight = (byDay.keys + sleepByDay.keys).maxOrNull() ?: return emptyList()
        val daysConsidered = generateSequence(first) { it.plusDays(1) }.takeWhile { it < lastNight }.toList()

        val out = ArrayList<TagEffect>()
        for (tag in JournalTag.entries) {
            for (metric in JournalMetric.entries) {
                val tagged = ArrayList<Double>()
                val other = ArrayList<Double>()
                for (day in daysConsidered) {
                    val v = value(metric, day.plusDays(1)) ?: continue
                    if (tag in book.tags(day)) tagged += v else other += v
                }
                if (tagged.size >= MIN_NIGHTS && other.size >= MIN_NIGHTS) {
                    out += TagEffect(tag, metric, tagged.average(), other.average(), tagged.size, other.size)
                }
            }
        }
        return out
    }
}
