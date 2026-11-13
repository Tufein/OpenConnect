package nl.tufein.openconnect.core

import java.time.LocalDate

/** What the home-screen widget shows: today's readiness and steps, and how far this week's activity goal is. */
data class WidgetSummary(
    val readiness: Int?,
    val steps: Int?,
    val stepsGoal: Int?,
    val weekActivities: Int,
    val weekActivityGoal: Int,
) {
    companion object {
        /** [today] is the day the data is looked up for; the newest daily summary of the last week is used when today has none yet. */
        fun of(repo: Repository, stats: StatsService, goals: WeeklyGoals, today: LocalDate): WidgetSummary {
            val day = repo.listDaily(today.minusDays(6), today).lastOrNull()
            val sleep = day?.let { repo.listSleep(it.date.minusDays(1), it.date).lastOrNull() }
            val readiness = day?.let {
                ReadinessCalculator.compute(it, sleep, repo.listDaily(it.date.minusDays(ReadinessCalculator.BASELINE_DAYS.toLong()), it.date.minusDays(1)))
            }
            return WidgetSummary(readiness?.score, day?.steps, day?.stepsGoal, stats.weeklySummary(today).activityCount, goals.activities)
        }
    }
}
