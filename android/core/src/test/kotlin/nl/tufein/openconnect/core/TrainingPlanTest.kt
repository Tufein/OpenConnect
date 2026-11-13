package nl.tufein.openconnect.core

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrainingPlanTest {
    private val monday = "2026-11-16"

    private fun plan(goal: PlanGoal = PlanGoal.FIVE_K, runs: Int = 3, weeks: Int = 8, baseline: Double = 15.0) =
        TrainingPlans.build(PlanConfig(goal, runs, weeks, monday, baseline))

    @Test
    fun `a plan has one week per week and ends with the race on a Sunday`() {
        val p = plan()
        assertEquals(8, p.weeks.size)
        assertEquals(LocalDate.parse(monday), p.start)
        assertEquals(DayOfWeek.SUNDAY, p.raceDate.dayOfWeek)
        val race = p.weeks.last().sessions.last()
        assertEquals(SessionType.RACE, race.type)
        assertEquals(5.0, race.distanceKm)
        assertTrue(p.weeks.last().race)
    }

    @Test
    fun `volume grows at most 10 percent a week, with a lighter fourth week and a taper`() {
        val p = plan(baseline = 12.0, weeks = 10)
        val volumes = p.weeks.map { it.volumeKm }
        for (i in 1 until volumes.size - 1) {
            if (!p.weeks[i].recovery && !p.weeks[i - 1].recovery) {
                assertTrue(volumes[i] <= volumes[i - 1] * 1.1 + 0.5, "week ${i + 1}: $volumes")
            }
        }
        assertTrue(p.weeks[3].recovery && volumes[3] < volumes[2], volumes.toString())
        assertTrue(volumes.last() < volumes[volumes.size - 2], "taper: $volumes")
    }

    @Test
    fun `the number of runs follows the choice and every distance is at least 2 km in halves`() {
        for (runs in 2..4) {
            val p = plan(runs = runs)
            for (w in p.weeks) {
                assertEquals(runs, w.sessions.size, "week ${w.number}")
                for (s in w.sessions) {
                    assertTrue(s.distanceKm >= 2.0)
                    assertEquals(0.0, (s.distanceKm * 2) % 1.0)
                }
            }
        }
    }

    @Test
    fun `quality runs alternate between tempo and intervals and are easy in lighter weeks`() {
        val p = plan(runs = 3, weeks = 8)
        assertEquals(SessionType.TEMPO, p.weeks[0].sessions[1].type)
        assertEquals(SessionType.INTERVALS, p.weeks[1].sessions[1].type)
        assertEquals(SessionType.EASY, p.weeks[3].sessions[1].type)
        assertEquals(SessionType.LONG, p.weeks[0].sessions.last().type)
    }

    @Test
    fun `a 10 K plan builds higher than a 5 K plan, and a big runner starts below the peak`() {
        val five = plan(PlanGoal.FIVE_K, weeks = 12).weeks.maxOf { it.volumeKm }
        val ten = plan(PlanGoal.TEN_K, weeks = 12).weeks.maxOf { it.volumeKm }
        assertTrue(ten > five)
        val big = plan(PlanGoal.FIVE_K, baseline = 80.0)
        assertTrue(big.weeks.first().volumeKm < 80.0)
        assertTrue(big.weeks.first().volumeKm <= PlanGoal.FIVE_K.peakKm)
    }

    @Test
    fun `a start day that is not a Monday moves back to its Monday and out-of-range choices are limited`() {
        val p = TrainingPlans.build(PlanConfig(PlanGoal.TEN_K, runsPerWeek = 9, weeks = 99, start = "2026-11-19", baselineKm = -3.0))
        assertEquals(LocalDate.parse("2026-11-16"), p.start)
        assertEquals(12, p.weeks.size)
        assertEquals(4, p.weeks.first().sessions.size)
    }

    @Test
    fun `the stored choice survives encoding and a broken one is dropped`() {
        val config = PlanConfig(PlanGoal.TEN_K, 4, 10, monday, 21.5)
        assertEquals(config, PlanConfig.decode(config.encode()))
        assertNull(PlanConfig.decode("not json"))
        assertNull(PlanConfig.decode("""{"goal":"FIVE_K","start":"soon"}"""))
        assertEquals(plan(), plan(), "building is deterministic")
    }

    private val repo = InMemoryRepository()

    private fun run(id: String, day: String, km: Double) = Activity(
        id = id, name = id, sport = Sport.RUNNING, startTime = Instant.parse("${day}T07:00:00Z"), durationS = km * 360, distanceM = km * 1000,
    ).also(repo::upsertActivity)

    @Test
    fun `the baseline is the weekly average of the last four weeks of running`() {
        val today = LocalDate.parse("2026-11-13")
        run("a", "2026-11-10", 8.0)
        run("b", "2026-10-25", 12.0)
        run("old", "2026-09-01", 50.0)
        repo.upsertActivity(Activity(id = "bike", name = "Ride", sport = Sport.CYCLING, startTime = Instant.parse("2026-11-11T07:00:00Z"), durationS = 3600.0, distanceM = 40_000.0))
        assertEquals(5.0, TrainingPlans.baselineKm(repo, today))
    }

    @Test
    fun `progress marks weeks, finds the next run and notices a run that was done`() {
        val p = plan(weeks = 6)
        val w1 = p.weeks[0]
        val acts = w1.sessions.map { run("r${it.date}", it.date.toString(), it.distanceKm) }
        val today = p.weeks[1].start // Monday of week 2
        val progress = TrainingPlans.progress(p, acts, today)
        assertEquals(WeekState.DONE, progress.weeks[0].state)
        assertEquals(WeekState.CURRENT, progress.weeks[1].state)
        assertEquals(WeekState.UPCOMING, progress.weeks[2].state)
        assertEquals(p.weeks[1].number, progress.current?.week?.number)
        assertEquals(p.weeks[1].sessions.first().date, progress.next?.date)
        assertFalse(progress.finished)
        assertFalse(progress.notStarted)
    }

    @Test
    fun `a week without runs is behind, and before the start and after the race are told apart`() {
        val p = plan(weeks = 6)
        val later = TrainingPlans.progress(p, emptyList(), p.weeks[2].start)
        assertEquals(WeekState.BEHIND, later.weeks[0].state)
        assertTrue(TrainingPlans.progress(p, emptyList(), p.start.minusDays(3)).notStarted)
        val after = TrainingPlans.progress(p, emptyList(), p.raceDate.plusDays(2))
        assertTrue(after.finished)
        assertNull(after.next)
        assertNull(after.current)
    }

    @Test
    fun `the lock follows the grace period`() {
        var t = Instant.parse("2026-11-13T10:00:00Z")
        val gate = LockGate(enabled = true, grace = Duration.ofSeconds(60)) { t }
        assertTrue(gate.locked)
        gate.markUnlocked()
        assertFalse(gate.locked)
        gate.left(); t = t.plusSeconds(30); gate.returned()
        assertFalse(gate.locked, "back within the grace period")
        gate.left(); t = t.plusSeconds(61); gate.returned()
        assertTrue(gate.locked, "away too long")
        gate.markUnlocked(); gate.enabled = false
        assertFalse(gate.locked)
        gate.enabled = true; gate.lock()
        assertTrue(gate.locked)
    }
}
