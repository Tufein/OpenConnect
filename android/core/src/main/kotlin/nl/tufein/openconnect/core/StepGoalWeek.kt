package nl.tufein.openconnect.core

import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * The last seven days of steps against the step goal: on [daysReached] of [daysWithData] days you reached it, [averageSteps] is the
 * average over the days with steps, [bestDay] and [bestSteps] the busiest day. [goal] is the goal of the latest day (the watch's
 * goal, or [StepGoalWeekCalculator.DEFAULT_GOAL] when it reports none).
 */
data class StepGoalWeek(
    val daysReached: Int,
    val daysWithData: Int,
    val goal: Int,
    val averageSteps: Int,
    val bestDay: LocalDate,
    val bestSteps: Int,
)

/** A small weekly look at the step goal (Garmin and Oura both keep a count of goal days). */
object StepGoalWeekCalculator {
    const val DAYS = 7
    const val MIN_DAYS = 3
    const val DEFAULT_GOAL = 10_000

    fun compute(dailies: List<DailySummary>, today: LocalDate): StepGoalWeek? {
        val from = today.minusDays((DAYS - 1).toLong())
        val days = dailies.filter { it.date in from..today && it.steps > 0 }.sortedBy { it.date }
        if (days.size < MIN_DAYS) return null
        fun goalOf(d: DailySummary) = d.stepsGoal?.takeIf { it > 0 } ?: DEFAULT_GOAL
        val best = days.maxBy { it.steps }
        return StepGoalWeek(
            daysReached = days.count { it.steps >= goalOf(it) },
            daysWithData = days.size,
            goal = goalOf(days.last()),
            averageSteps = days.map { it.steps }.average().roundToInt(),
            bestDay = best.date,
            bestSteps = best.steps,
        )
    }
}
