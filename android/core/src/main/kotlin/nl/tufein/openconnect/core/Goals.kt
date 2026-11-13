package nl.tufein.openconnect.core

/**
 * What you want to reach each week. Defaults follow the usual recommendations: 150 intensity minutes (the World Health
 * Organization's figure for adults), 20 km and 3 activities. A goal of 0 switches that goal off.
 */
data class WeeklyGoals(
    val intensityMinutes: Int = 150,
    val distanceKm: Int = 20,
    val activities: Int = 3,
) {
    fun coerced() = WeeklyGoals(
        intensityMinutes = intensityMinutes.coerceIn(0, 1000),
        distanceKm = distanceKm.coerceIn(0, 1000),
        activities = activities.coerceIn(0, 21),
    )

    fun progress(week: WeeklySummary) = WeeklyGoalProgress(
        intensity = GoalProgress(intensityMinutes, week.intensityMinutes.toDouble()),
        distance = GoalProgress(distanceKm, week.totalDistanceM / 1000.0),
        activities = GoalProgress(activities, week.activityCount.toDouble()),
    )
}

/** [done] is the true amount, so 4 of 3 reads as 4; [fraction] stops at 1 for the bar. */
data class GoalProgress(val goal: Int, val done: Double) {
    val fraction: Double get() = if (goal <= 0) 0.0 else (done / goal).coerceIn(0.0, 1.0)
    val reached: Boolean get() = goal > 0 && done >= goal
}

data class WeeklyGoalProgress(val intensity: GoalProgress, val distance: GoalProgress, val activities: GoalProgress) {
    val reachedCount: Int get() = listOf(intensity, distance, activities).count { it.reached }
}
