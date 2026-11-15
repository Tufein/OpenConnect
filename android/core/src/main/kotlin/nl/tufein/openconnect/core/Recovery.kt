package nl.tufein.openconnect.core

import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.min
import kotlin.math.sqrt

enum class MonotonyVerdict {
    /** Hard and easy days alternate, with rest in between. */
    VARIED,

    /** Days look alike: add an easy day or a rest day. */
    UNIFORM,

    /** Almost the same every day: the pattern that tires you most for the load. */
    MONOTONOUS,
}

/**
 * Foster's monotony and strain of the last seven days. [monotony] is the mean daily load divided by its standard
 * deviation (capped at [MonotonyCalculator.MAX_MONOTONY]), [strain] the week's load times that, and [usualStrain] the average
 * strain of the four weeks before when they had training.
 */
data class MonotonyResult(
    val monotony: Double,
    val strain: Double,
    val weekLoad: Double,
    val restDays: Int,
    val verdict: MonotonyVerdict,
    val usualStrain: Double?,
)

/** Works on the daily training load of [TrainingLoadCalculator]. An estimate, not medical advice. */
object MonotonyCalculator {
    const val DAYS = 7
    const val MAX_MONOTONY = 5.0
    const val MIN_HISTORY_DAYS = 14

    fun compute(activities: List<Activity>, today: LocalDate): MonotonyResult? {
        fun day(a: Activity) = a.startTime.atZone(ZoneOffset.UTC).toLocalDate()
        val known = activities.filter { !day(it).isAfter(today) }
        if (known.isEmpty()) return null
        if (known.minOf(::day).isAfter(today.minusDays((MIN_HISTORY_DAYS - 1).toLong()))) return null
        val maxHr = activities.mapNotNull { it.maxHr ?: it.avgHr }.maxOrNull() ?: 0
        val perDay = known.groupBy(::day).mapValues { (_, list) -> list.sumOf { TrainingLoadCalculator.activityLoad(it, maxHr) } }

        fun week(end: LocalDate): Triple<Double, Double, Int>? {
            val loads = (0 until DAYS).map { perDay[end.minusDays(it.toLong())] ?: 0.0 }
            val total = loads.sum()
            if (total <= 0.0) return null
            val mean = total / DAYS
            val sd = sqrt(loads.sumOf { (it - mean) * (it - mean) } / DAYS)
            val monotony = if (sd < 1e-9) MAX_MONOTONY else min(MAX_MONOTONY, mean / sd)
            return Triple(monotony, total, loads.count { it == 0.0 })
        }

        val (monotony, total, rest) = week(today) ?: return null
        val usual = (1..4).mapNotNull { w -> week(today.minusDays(DAYS * w.toLong()))?.let { (m, t, _) -> m * t } }
        val verdict = when {
            monotony < 1.5 -> MonotonyVerdict.VARIED
            monotony < 2.0 -> MonotonyVerdict.UNIFORM
            else -> MonotonyVerdict.MONOTONOUS
        }
        return MonotonyResult(monotony, total * monotony, total, rest, verdict, usual.takeIf { it.isNotEmpty() }?.average())
    }
}

enum class SleepDebtLevel { NONE, SMALL, BIG }

/** [debtHours] is how far the last [SleepNeedCalculator.WINDOW] nights fall short of the base need, never below zero. */
data class SleepNeed(
    val needTonightHours: Double,
    val lastNightHours: Double?,
    val debtHours: Double,
    val nights: Int,
    val level: SleepDebtLevel,
)

/** A sleep target for tonight in the spirit of WHOOP's sleep need: a base, a little more after a hard day, a part of the debt. */
object SleepNeedCalculator {
    const val BASE_HOURS = 8.0
    const val WINDOW = 7
    const val MIN_NIGHTS = 4
    private const val MAX_PAYBACK = 1.0

    /** [todayLoad] is the training load of today so far; tonight's need is [BASE_HOURS] plus 15 or 30 minutes after 150 or 300, plus a quarter of the debt up to an hour. */
    fun compute(sleeps: List<SleepSummary>, todayLoad: Double, today: LocalDate): SleepNeed? {
        val from = today.minusDays((WINDOW - 1).toLong())
        val nights = sleeps.filter { it.date in from..today && it.durationS > 0 }
        if (nights.size < MIN_NIGHTS) return null
        val debt = maxOf(0.0, nights.sumOf { BASE_HOURS - it.hours })
        val extra = when {
            todayLoad >= 300 -> 0.5
            todayLoad >= 150 -> 0.25
            else -> 0.0
        }
        val level = when {
            debt < 1.0 -> SleepDebtLevel.NONE
            debt < 4.0 -> SleepDebtLevel.SMALL
            else -> SleepDebtLevel.BIG
        }
        return SleepNeed(BASE_HOURS + extra + min(MAX_PAYBACK, debt / 4), nights.firstOrNull { it.date == today }?.hours, debt, nights.size, level)
    }
}

object Recovery {
    /** The training load of one day: the sessions that started on [day] added up. */
    fun dayLoad(activities: List<Activity>, day: LocalDate): Double {
        val maxHr = activities.mapNotNull { it.maxHr ?: it.avgHr }.maxOrNull() ?: 0
        return activities.filter { it.startTime.atZone(ZoneOffset.UTC).toLocalDate() == day }
            .sumOf { TrainingLoadCalculator.activityLoad(it, maxHr) }
    }
}
