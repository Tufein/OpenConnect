package nl.tufein.openconnect.core

import nl.tufein.openconnect.core.formats.FitReader
import nl.tufein.openconnect.core.watch.InMemoryWatchSource
import nl.tufein.openconnect.core.watch.WatchFaceException
import nl.tufein.openconnect.core.watch.WatchFileInfo
import nl.tufein.openconnect.core.watch.WatchSource
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkoutsTest {
    private val day = LocalDate.of(2026, 11, 16)
    private val created = Instant.parse("2026-11-14T10:00:00Z")
    private val paces = TrainingPaces(easy = 375.0, tempo = 309.0, interval = 300.0)

    private fun session(type: SessionType, km: Double) = PlannedSession(day, type, km)

    /** What an independent reader (the importer's FIT reader) finds in a workout file. */
    private class Read(val type: Long?, val sport: Long?, val stepCount: Long?, val steps: List<Map<Int, Long?>>)

    private fun read(bytes: ByteArray): Read {
        var type: Long? = null
        var sport: Long? = null
        var count: Long? = null
        val steps = ArrayList<Map<Int, Long?>>()
        val fields = listOf(254, 1, 2, 3, 4, 5, 6, 7)
        FitReader.read(
            bytes,
            wanted = { global -> when (global) { 0 -> setOf(0); 26 -> setOf(4, 6); 27 -> fields.toSet(); else -> null } },
            onMessage = { global, v ->
                when (global) {
                    0 -> type = v[0]
                    26 -> { sport = v[4]; count = v[6] }
                    27 -> steps += fields.associateWith { v[it] }
                }
            },
        )
        return Read(type, sport, count, steps)
    }

    @Test
    fun anIntervalSessionIsAWarmUpRepeatedFastAndJogStepsAndACoolDown() {
        val workout = Workouts.forSession(session(SessionType.INTERVALS, 10.0), paces)
        assertEquals(listOf(1, 10, 1), workout.blocks.map { it.repeat }) // (10 - 2.5) / 0.6 is 12.5, capped at 10
        val file = read(Workouts.toFit(workout, created))
        assertEquals(5L, file.type) // a workout file
        assertEquals(1L, file.sport) // running
        assertEquals(5L, file.stepCount)
        assertEquals(5, file.steps.size)
        val (warmup, fast, jog, repeat, cooldown) = file.steps
        assertEquals(listOf(0L, 0L, 600_000L, 2L, 2L), listOf(warmup[254], warmup[1], warmup[2], warmup[7], warmup[3]).map { it })
        assertEquals(1L, fast[254]); assertEquals(1L, fast[1]); assertEquals(40_000L, fast[2]); assertEquals(5L, fast[7])
        assertEquals(2L, jog[254]); assertEquals(0L, jog[1]); assertEquals(90_000L, jog[2]); assertEquals(4L, jog[7])
        // The repeat step points back at the first step of the block and carries the count.
        assertEquals(3L, repeat[254]); assertEquals(6L, repeat[1]); assertEquals(1L, repeat[2]); assertEquals(10L, repeat[4])
        assertEquals(3L, cooldown[7]); assertEquals(300_000L, cooldown[2])
    }

    @Test
    fun aPaceTargetIsASpeedWindowInMillimetresPerSecond() {
        val workout = Workouts.forSession(session(SessionType.INTERVALS, 8.0), paces)
        val fast = read(Workouts.toFit(workout, created)).steps[1]
        assertEquals(0L, fast[3]) // target type: speed
        assertEquals(0L, fast[4]) // target value 0: use the custom window
        // 300 s/km +- 8 s: slowest 308 s/km is 3247 mm/s, fastest 292 s/km is 3425 mm/s.
        assertEquals(3247L, fast[5])
        assertEquals(3425L, fast[6])
    }

    @Test
    fun withoutPacesEveryTargetIsOpen() {
        val steps = Workouts.fitSteps(Workouts.forSession(session(SessionType.TEMPO, 8.0), null))
        assertTrue(steps.all { it.targetType == 2 && it.low == null && it.high == null })
    }

    @Test
    fun aTempoSessionKeepsWhatIsLeftOfTheDistanceForTheTempoPart() {
        val file = read(Workouts.toFit(Workouts.forSession(session(SessionType.TEMPO, 8.0), paces), created))
        assertEquals(3, file.steps.size)
        assertEquals(1L, file.steps[1][1]) // distance
        assertEquals(550_000L, file.steps[1][2]) // 8 km minus 2.5 km for warm-up and cool-down, in centimetres
        // A short session still gets a kilometre of tempo.
        assertEquals(100_000L, read(Workouts.toFit(Workouts.forSession(session(SessionType.TEMPO, 3.0), paces), created)).steps[1][2])
    }

    @Test
    fun anEasyLongAndRaceSessionIsOneOpenStepOfTheWholeDistance() {
        for (type in listOf(SessionType.EASY, SessionType.LONG, SessionType.RACE)) {
            val file = read(Workouts.toFit(Workouts.forSession(session(type, 5.0), paces), created))
            assertEquals(1, file.steps.size, type.name)
            assertEquals(500_000L, file.steps[0][2], type.name)
            assertEquals(2L, file.steps[0][3], type.name) // no pace window
            assertEquals(0L, file.steps[0][7], type.name) // active
        }
    }

    @Test
    fun theFileIsAValidFitFileAndTheNameIsInIt() {
        val bytes = Workouts.toFit(Workouts.forSession(session(SessionType.TEMPO, 8.0), paces), created)
        FitReader.verify(bytes) // header and CRC
        assertTrue(String(bytes, Charsets.ISO_8859_1).contains("OC 16 Nov Tempo 8.0 km"))
        assertEquals("OC 16 Nov Tempo 8.0 km", Workouts.forSession(session(SessionType.TEMPO, 8.0), paces).name)
        // The same input gives the same file.
        assertContentEquals(bytes, Workouts.toFit(Workouts.forSession(session(SessionType.TEMPO, 8.0), paces), created))
    }

    @Test
    fun aTooLongNameIsCutToTheFieldAndStillEndsWithZero() {
        val long = Workout("x".repeat(60), listOf(WorkoutBlock(1, listOf(WorkoutStep("y".repeat(40), StepIntensity.ACTIVE, StepLength.Open)))))
        val bytes = Workouts.toFit(long, created)
        FitReader.verify(bytes)
        val text = String(bytes, Charsets.ISO_8859_1)
        assertTrue(text.contains("x".repeat(31)) && !text.contains("x".repeat(32)))
        assertTrue(text.contains("y".repeat(15)) && !text.contains("y".repeat(16)))
        assertEquals(5L, read(bytes).steps[0][1]) // an open step has no length
    }

    @Test
    fun theFileNameSortsByDateAndIsPlainAscii() {
        assertEquals("OC_2026-11-16_TEMPO.fit", Workouts.fileName(session(SessionType.TEMPO, 8.0)))
        assertEquals("OC_2026-11-16_INTERVALS.fit", Workouts.fileName(session(SessionType.INTERVALS, 8.0)))
    }

    @Test
    fun paceTargetsComeFromTheTenKilometrePrediction() {
        val effort = Effort(10_000.0, 3000.0)
        val predictions = RacePredictor.predictions(listOf(effort))
        val p = TrainingPaces.from(predictions)!!
        assertEquals(309.0, p.tempo, 1e-9)
        assertEquals(288.0, p.interval, 1e-9)
        assertEquals(375.0, p.easy, 1e-9)
        assertNull(TrainingPaces.from(emptyList()))
    }

    private fun plan() = TrainingPlans.build(PlanConfig(PlanGoal.TEN_K, 3, 8, "2026-11-16", 20.0))

    @Test
    fun theNextSevenDaysStartAtThePlanWhenItHasNotStartedYet() {
        val plan = plan()
        val first = plan.weeks.first().sessions
        val upcoming = Workouts.upcoming(plan, LocalDate.of(2026, 10, 1))
        assertEquals(first.map { it.date }, upcoming.map { it.date })
        assertTrue(upcoming.all { it.date >= plan.start && it.date < plan.start.plusDays(7) })
    }

    @Test
    fun theNextSevenDaysStartTodayOnceThePlanIsRunning() {
        val plan = plan()
        val today = plan.start.plusDays(10)
        val upcoming = Workouts.upcoming(plan, today)
        assertTrue(upcoming.isNotEmpty())
        assertTrue(upcoming.all { !it.date.isBefore(today) && it.date.isBefore(today.plusDays(7)) })
        assertEquals(upcoming.sortedBy { it.date }, upcoming)
        assertEquals(emptyList(), Workouts.upcoming(plan, plan.weeks.last().end.plusDays(30)))
    }

    @Test
    fun filesAreNamedAfterTheirSession() {
        val sessions = listOf(session(SessionType.EASY, 5.0), PlannedSession(day.plusDays(2), SessionType.LONG, 12.0))
        val files = Workouts.files(sessions, paces, created)
        assertEquals(listOf("OC_2026-11-16_EASY.fit", "OC_2026-11-18_LONG.fit"), files.map { it.name })
        files.forEach { FitReader.verify(it.bytes) }
    }

    @Test
    fun sendingTwiceLeavesTheFirstCopyAndSaysSo() {
        val watch = InMemoryWatchSource("fenix 8", emptyMap())
        val files = Workouts.files(listOf(session(SessionType.EASY, 5.0)), paces, created)
        assertEquals(WorkoutTransferResult(listOf("OC_2026-11-16_EASY.fit"), emptyList()), Workouts.send(watch, files))
        assertEquals(WorkoutTransferResult(emptyList(), listOf("OC_2026-11-16_EASY.fit")), Workouts.send(watch, files))
        assertEquals(1, watch.workouts.size)
    }

    @Test
    fun aLinkThatCannotWriteIsAnError() {
        val readOnly = object : WatchSource {
            override val deviceName = "ble"
            override fun listFiles(): List<WatchFileInfo> = emptyList()
            override fun read(file: WatchFileInfo): ByteArray = ByteArray(0)
        }
        val files = Workouts.files(listOf(session(SessionType.EASY, 5.0)), paces, created)
        val e = assertFailsWith<WatchFaceException> { Workouts.send(readOnly, files) }
        assertEquals(WatchFaceException.Kind.CANNOT_WRITE, e.kind)
    }
}
