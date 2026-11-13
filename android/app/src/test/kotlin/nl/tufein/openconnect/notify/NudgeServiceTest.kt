package nl.tufein.openconnect.notify

import nl.tufein.openconnect.core.Activity
import nl.tufein.openconnect.core.InMemoryRepository
import nl.tufein.openconnect.core.Nudge
import nl.tufein.openconnect.core.NudgeKind
import nl.tufein.openconnect.core.StatsService
import nl.tufein.openconnect.core.WeeklyGoals
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NudgeServiceTest {
    private class Settings(override var enabled: Boolean = true, override var sent: List<String> = emptyList()) : NudgeSettings {
        override val goals = WeeklyGoals(intensityMinutes = 0, distanceKm = 0, activities = 3)
    }

    private val repo = InMemoryRepository()
    private val shown = ArrayList<Nudge>()
    private val zone = ZoneId.of("Europe/Amsterdam")

    // Wednesday 11 Nov 2026, evening: two of three activities done this week (Monday 9 Nov and Tuesday 10 Nov)
    private val evening = ZonedDateTime.of(2026, 11, 11, 18, 0, 0, 0, zone)

    private fun service(settings: NudgeSettings) = NudgeService(repo, StatsService(repo) { Instant.parse("2026-11-11T17:00:00Z") }, settings, shown::add)

    private fun twoActivities() {
        repo.upsertActivity(Activity(id = "a", startTime = Instant.parse("2026-11-09T08:00:00Z")))
        repo.upsertActivity(Activity(id = "b", startTime = Instant.parse("2026-11-10T08:00:00Z")))
    }

    @Test
    fun `a goal that is one activity away sends one nudge and remembers it`() {
        twoActivities()
        val settings = Settings()
        assertEquals(1, service(settings).check(evening))
        assertEquals(NudgeKind.GOAL_CLOSE, shown.single().kind)
        assertEquals(listOf(shown.single().key), settings.sent)
    }

    @Test
    fun `the next check does not repeat it, also after the service is made again`() {
        twoActivities()
        val settings = Settings()
        service(settings).check(evening)
        assertEquals(0, service(settings).check(evening.plusHours(3)))
        assertEquals(1, shown.size)
    }

    @Test
    fun `nothing is sent while the switch is off, and nothing is remembered`() {
        twoActivities()
        val settings = Settings(enabled = false)
        assertEquals(0, service(settings).check(evening))
        assertTrue(shown.isEmpty() && settings.sent.isEmpty())
    }

    @Test
    fun `nothing is sent when nothing is close`() {
        assertEquals(0, service(Settings()).check(evening))
    }
}
