package nl.tufein.openconnect.core

import java.time.LocalDate
import java.time.ZoneOffset

/**
 * One day of the fitness, fatigue and form picture. [load] is that day's training load (see [TrainingLoadCalculator]),
 * [fitness] a slow average of it (42 days), [fatigue] a fast one (7 days). The units are OpenConnect's own, not TSS.
 */
data class FormPoint(val date: LocalDate, val load: Double, val fitness: Double, val fatigue: Double) {
    /** What is left in the tank: fitness minus fatigue. Above zero you are rested, below zero you are carrying fatigue. */
    val form: Double get() = fitness - fatigue
}

enum class FormStatus {
    /** Clearly rested: a good day for a race or a hard session. */
    FRESH,

    /** Fitness and fatigue are in line. */
    BALANCED,

    /** Carrying some fatigue: the normal, productive state in a block of training. */
    BUILDING,

    /** Far more fatigue than fitness: time to back off. */
    STRAINED,
    ;

    companion object {
        /** Form against fitness, so a big engine and a small one are judged by the same rule. */
        fun of(fitness: Double, form: Double): FormStatus {
            val ratio = if (fitness <= 0.0) 0.0 else form / fitness
            return when {
                ratio >= 0.20 -> FRESH
                ratio >= -0.05 -> BALANCED
                ratio >= -0.30 -> BUILDING
                else -> STRAINED
            }
        }
    }
}

/** The last days of [FormPoint]s, oldest first. */
data class FormSeries(val points: List<FormPoint>) {
    val latest: FormPoint get() = points.last()

    /** The state today, or null while there is too little fitness for the ratio to mean anything. */
    val status: FormStatus?
        get() = latest.takeIf { it.fitness >= FormCalculator.MIN_FITNESS }?.let { FormStatus.of(it.fitness, it.form) }
}

/**
 * The classic "fitness, fatigue, form" picture (known from TrainingPeaks and Intervals.icu) worked out from the training load
 * OpenConnect already computes: minutes times an effort weight from heart rate. It needs no power meter. It is an estimate,
 * not Garmin's Training Status and not medical advice.
 */
object FormCalculator {
    const val FITNESS_DAYS = 42
    const val FATIGUE_DAYS = 7
    const val MIN_HISTORY_DAYS = 14
    const val DEFAULT_SHOWN_DAYS = 90

    /** Below this much fitness (about ten minutes of easy training a day) the form ratio is left out. */
    const val MIN_FITNESS = 10.0

    /**
     * The picture for the last [shownDays] days up to [today]. The averages start on the day of the first activity, and
     * days without an activity count as zero load. Returns null with fewer than [MIN_HISTORY_DAYS] days of history.
     */
    fun compute(activities: List<Activity>, today: LocalDate, shownDays: Int = DEFAULT_SHOWN_DAYS): FormSeries? {
        fun day(a: Activity) = a.startTime.atZone(ZoneOffset.UTC).toLocalDate()
        val known = activities.filter { !day(it).isAfter(today) }
        if (known.isEmpty()) return null
        val first = known.minOf(::day)
        if (first.isAfter(today.minusDays((MIN_HISTORY_DAYS - 1).toLong()))) return null

        val maxHr = activities.mapNotNull { it.maxHr ?: it.avgHr }.maxOrNull() ?: 0
        val perDay = known.groupBy(::day).mapValues { (_, list) -> list.sumOf { TrainingLoadCalculator.activityLoad(it, maxHr) } }

        val points = ArrayList<FormPoint>()
        var fitness = 0.0
        var fatigue = 0.0
        var date = first
        while (!date.isAfter(today)) {
            val load = perDay[date] ?: 0.0
            fitness += (load - fitness) / FITNESS_DAYS
            fatigue += (load - fatigue) / FATIGUE_DAYS
            points += FormPoint(date, load, fitness, fatigue)
            date = date.plusDays(1)
        }
        return FormSeries(points.takeLast(shownDays.coerceAtLeast(1)))
    }
}

enum class AdviceLevel {
    /** Take the day off or keep it very light. */
    REST,

    /** An easy day. */
    EASY,

    /** Train as planned. */
    NORMAL,

    /** Rested and ready: a good day for a hard session. */
    PUSH,
}

/** What holds the advice down: how ready you are, the fatigue built up, both, or nothing. */
enum class AdviceLimit { READINESS, FORM, BOTH, NONE }

data class TodayAdvice(val level: AdviceLevel, val limitedBy: AdviceLimit)

/**
 * "How hard should today be?": the weaker of the readiness estimate and the form status decides, like a Strain Target.
 * It is a suggestion from OpenConnect's own estimates, not medical advice.
 */
object TodayAdviceCalculator {
    private fun points(level: ReadinessLevel) = when (level) {
        ReadinessLevel.LOW -> 0
        ReadinessLevel.MODERATE -> 1
        ReadinessLevel.GOOD -> 2
        ReadinessLevel.EXCELLENT -> 3
    }

    private fun points(status: FormStatus) = when (status) {
        FormStatus.STRAINED -> 0
        FormStatus.BUILDING, FormStatus.BALANCED -> 2
        FormStatus.FRESH -> 3
    }

    fun compute(readiness: Readiness?, form: FormStatus?): TodayAdvice? {
        val r = readiness?.let { points(it.level) }
        val f = form?.let { points(it) }
        if (r == null && f == null) return null
        val level = minOf(r ?: Int.MAX_VALUE, f ?: Int.MAX_VALUE)
        val limit = when {
            level == 3 -> AdviceLimit.NONE
            r != null && f != null -> if (r < f) AdviceLimit.READINESS else if (f < r) AdviceLimit.FORM else AdviceLimit.BOTH
            r != null -> AdviceLimit.READINESS
            else -> AdviceLimit.FORM
        }
        return TodayAdvice(AdviceLevel.entries[level], limit)
    }
}
