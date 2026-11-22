package nl.tufein.openconnect.core

import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class WorkoutBookTest {
    private fun workout(
        name: String = "4 × 1 km",
        warmup: Int = 600,
        fast: Int = 240,
        recovery: Int = 90,
        repeats: Int = 4,
        cooldown: Int = 300,
    ) = OwnWorkout("own-1", name, warmup, fast, recovery, repeats, cooldown)

    @Test
    fun aFreshWorkoutHasTheShapePeopleExpect() {
        val workout = workout()
        assertNull(workout.problem)
        assertEquals(600 + 4 * (240 + 90) + 300, workout.totalS)

        val plan = workout.toWorkout()
        assertEquals("4 × 1 km", plan.name)
        assertEquals(3, plan.blocks.size)
        assertEquals(StepIntensity.WARMUP, plan.blocks[0].steps.single().intensity)
        assertEquals(4, plan.blocks[1].repeat)
        assertEquals(2, plan.blocks[1].steps.size)
        assertEquals(240, (plan.blocks[1].steps[0].length as StepLength.Time).seconds)
        assertEquals(90, (plan.blocks[1].steps[1].length as StepLength.Time).seconds)
        assertEquals(StepIntensity.COOLDOWN, plan.blocks[2].steps.single().intensity)
    }

    @Test
    fun zeroWarmupAndCooldownLeaveThemOut() {
        val plan = workout(warmup = 0, cooldown = 0).toWorkout()
        assertEquals(1, plan.blocks.size)
        assertEquals(4, plan.blocks[0].repeat)
    }

    @Test
    fun everyValueIsBounded() {
        assertEquals(OwnWorkout.Problem.NAME, workout(name = "  ").problem)
        assertEquals(OwnWorkout.Problem.WARMUP, workout(warmup = -1).problem)
        assertEquals(OwnWorkout.Problem.WARMUP, workout(warmup = 3_601).problem)
        assertEquals(OwnWorkout.Problem.FAST, workout(fast = 29).problem)
        assertEquals(OwnWorkout.Problem.FAST, workout(fast = 3_601).problem)
        assertEquals(OwnWorkout.Problem.RECOVERY, workout(recovery = 1_801).problem)
        assertEquals(OwnWorkout.Problem.REPEATS, workout(repeats = 0).problem)
        assertEquals(OwnWorkout.Problem.REPEATS, workout(repeats = 51).problem)
        assertEquals(OwnWorkout.Problem.COOLDOWN, workout(cooldown = -1).problem)
    }

    @Test
    fun idsAreDistinctPerMoment() {
        val a = OwnWorkout.id(Instant.ofEpochMilli(1_700_000_000_000))
        val b = OwnWorkout.id(Instant.ofEpochMilli(1_700_000_000_001))
        assertEquals("own-1700000000000", a)
        assertTrue(a != b)
    }

    private fun book() = WorkoutBook(
        listOf(
            workout(),
            OwnWorkout("own-2", "Heuvels | steil", 300, 120, 60, 6, 300, StepIntensity.ACTIVE),
        ),
    )

    @Test
    fun encodeAndDecodeKeepEveryField() {
        val back = WorkoutBook.decode(book().encode())
        assertEquals(2, back.workouts.size)
        assertEquals("4 × 1 km", back.workouts[0].name)
        assertEquals(4, back.workouts[0].repeats)
        assertEquals(240, back.workouts[0].fastS)
        assertEquals("Heuvels | steil", back.workouts[1].name)
        assertEquals(StepIntensity.ACTIVE, back.workouts[1].intensity)
        assertEquals(6, back.workouts[1].repeats)
    }

    @Test
    fun newlineAndPercentSurviveTheRoundTrip() {
        val odd = WorkoutBook(listOf(workout(name = "100%\nkort")))
        assertEquals("100%\nkort", WorkoutBook.decode(odd.encode()).workouts.single().name)
    }

    @Test
    fun rubbishLinesAreSkippedNotFatal() {
        val text = "not a workout\nW|only|two\n" + workout().let { WorkoutBook(listOf(it)).encode() } + "\nW|bad|name|-5|240|90|4|300|5"
        val back = WorkoutBook.decode(text)
        assertEquals(1, back.workouts.size)
        assertEquals("4 × 1 km", back.workouts.single().name)
    }

    @Test
    fun emptyTextIsAnEmptyBook() {
        assertEquals(0, WorkoutBook.decode("").workouts.size)
        assertEquals("", WorkoutBook().encode())
    }

    @Test
    fun plusReplacesByIdAndWithoutRemoves() {
        val one = WorkoutBook().plus(workout())
        val two = one.plus(workout(name = "Andere naam"))
        assertEquals(1, two.workouts.size)
        assertEquals("Andere naam", two.workouts.single().name)
        assertEquals(0, two.without("own-1").workouts.size)
    }
}
