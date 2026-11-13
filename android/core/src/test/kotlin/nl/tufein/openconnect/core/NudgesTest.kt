package nl.tufein.openconnect.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NudgesTest {
    private val monday = LocalDate.parse("2026-11-09")
    private val wednesday = monday.plusDays(2)
    private val goals = WeeklyGoals(intensityMinutes = 150, distanceKm = 20, activities = 3)

    private fun week(start: LocalDate = monday, activities: Int = 0, km: Double = 0.0, intensity: Int = 0) =
        WeeklySummary(weekStart = start, weekEnd = start.plusDays(6), activityCount = activities, totalDistanceM = km * 1000, intensityMinutes = intensity)

    private fun load(status: LoadStatus) = TrainingLoad(emptyList(), 0.0, 0.0, 1.0, status)

    private fun due(
        today: LocalDate = wednesday, hour: Int = 18, thisWeek: WeeklySummary = week(), lastWeek: WeeklySummary = week(monday.minusDays(7)),
        load: TrainingLoad? = null, sent: Set<String> = emptySet(),
    ) = Nudges.due(today, hour, goals, thisWeek, lastWeek, load, sent)

    @Test
    fun `a goal at 80 percent or more but not reached is a nudge in the evening, with what is missing`() {
        val nudges = due(thisWeek = week(activities = 2, km = 17.0, intensity = 100))
        assertEquals(listOf("distance", "activities"), nudges.map { it.goal })
        assertEquals(listOf(3, 1), nudges.map { it.numbers.single() })
        assertTrue(nudges.all { it.kind == NudgeKind.GOAL_CLOSE })
    }

    @Test
    fun `no goal nudge in the morning, below 80 percent, when reached, or when the goal is off`() {
        val close = week(activities = 2)
        assertTrue(due(hour = 16, thisWeek = close).isEmpty())
        assertTrue(due(thisWeek = week(activities = 1)).isEmpty())
        assertTrue(due(thisWeek = week(activities = 3)).isEmpty())
        assertTrue(Nudges.due(wednesday, 18, WeeklyGoals(0, 0, 0), close, week(), null, emptySet()).isEmpty())
    }

    @Test
    fun `the same nudge is never sent twice, but a new week gets a new one`() {
        val first = due(thisWeek = week(activities = 2)).single()
        assertTrue(due(thisWeek = week(activities = 2), sent = setOf(first.key)).isEmpty())
        val next = monday.plusDays(7)
        assertEquals(1, due(today = next.plusDays(2), thisWeek = week(next, activities = 2), sent = setOf(first.key)).size)
    }

    @Test
    fun `monday morning brings a summary of last week, only when there was one`() {
        val last = week(monday.minusDays(7), activities = 4, km = 31.4, intensity = 160)
        val n = due(today = monday, hour = 9, lastWeek = last).single()
        assertEquals(NudgeKind.WEEKLY_SUMMARY, n.kind)
        assertEquals(listOf(4, 31, 3), n.numbers) // 4 activities, 31 km, all three goals reached
        assertTrue(due(today = monday, hour = 7, lastWeek = last).isEmpty(), "too early")
        assertTrue(due(today = monday, hour = 9, lastWeek = week(monday.minusDays(7))).isEmpty(), "nothing to summarise")
        assertTrue(due(today = wednesday, hour = 9, lastWeek = last).isEmpty(), "only on Monday")
    }

    @Test
    fun `a big jump in load is mentioned once a week, other statuses are silent`() {
        assertEquals(NudgeKind.LOAD_JUMP, due(load = load(LoadStatus.OVERREACHING)).single().kind)
        for (s in listOf(LoadStatus.LIGHT, LoadStatus.STEADY, LoadStatus.HEAVY)) assertTrue(due(load = load(s)).isEmpty())
        assertTrue(due(load = load(LoadStatus.OVERREACHING), sent = setOf("load:$monday")).isEmpty())
    }

    @Test
    fun `remembering keeps the newest keys and no duplicates`() {
        var sent = emptyList<String>()
        for (i in 1..60) sent = Nudges.remember(sent, listOf(Nudge("k$i", NudgeKind.LOAD_JUMP)))
        assertEquals(Nudges.KEEP, sent.size)
        assertEquals("k60", sent.last())
        assertEquals(sent, Nudges.remember(sent, listOf(Nudge("k60", NudgeKind.LOAD_JUMP))))
    }

    @Test
    fun `the widget uses the newest day, its readiness and this week's activities`() {
        val repo = InMemoryRepository()
        val today = LocalDate.parse("2026-11-12")
        // 10 days of history for readiness, then a day with steps
        for (i in 1..10) repo.upsertDaily(DailySummary(date = today.minusDays(i.toLong() + 1), steps = 8000, restingHr = 52, hrvMs = 55, bodyBatteryHigh = 80))
        repo.upsertDaily(DailySummary(date = today.minusDays(1), steps = 6500, stepsGoal = 10_000, restingHr = 52, hrvMs = 55, bodyBatteryHigh = 80))
        repo.upsertActivity(Activity(id = "a", startTime = java.time.Instant.parse("2026-11-10T08:00:00Z")))
        val w = WidgetSummary.of(repo, StatsService(repo) { java.time.Instant.parse("2026-11-12T12:00:00Z") }, goals, today)
        assertEquals(6500, w.steps)
        assertEquals(10_000, w.stepsGoal)
        assertEquals(1, w.weekActivities)
        assertEquals(3, w.weekActivityGoal)
        assertTrue(w.readiness != null)
        assertEquals(WidgetSummary(null, null, null, 0, 3), WidgetSummary.of(InMemoryRepository(), StatsService(InMemoryRepository()), goals, today))
    }
}
