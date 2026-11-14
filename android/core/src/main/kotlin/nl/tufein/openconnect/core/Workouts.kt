package nl.tufein.openconnect.core

import nl.tufein.openconnect.core.formats.Fit
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** How hard a step is meant to be; [fit] is the number FIT uses (`intensity`). */
enum class StepIntensity(val fit: Int) { ACTIVE(0), REST(1), WARMUP(2), COOLDOWN(3), RECOVERY(4), INTERVAL(5) }

/** How long a step lasts: a time, a distance, or until the runner presses lap. */
sealed interface StepLength {
    data class Time(val seconds: Int) : StepLength
    data class Distance(val meters: Int) : StepLength
    data object Open : StepLength
}

/** A pace window in seconds per kilometre; [fast] is the smaller number. */
data class PaceRange(val fast: Int, val slow: Int)

data class WorkoutStep(
    val name: String,
    val intensity: StepIntensity,
    val length: StepLength,
    val pace: PaceRange? = null,
)

/** Steps that are done [repeat] times in a row (1 means once). */
data class WorkoutBlock(val repeat: Int, val steps: List<WorkoutStep>)

data class Workout(val name: String, val blocks: List<WorkoutBlock>)

/** Paces in seconds per kilometre that targets are worked out from. */
data class TrainingPaces(val easy: Double, val tempo: Double, val interval: Double) {
    companion object {
        /** From the predicted 10 km pace: tempo a little slower than it, intervals about 5 km pace; null without one. */
        fun from(predictions: List<RacePrediction>): TrainingPaces? {
            val p10 = predictions.firstOrNull { it.distance == RaceDistance.K10 }?.paceSecondsPerKm ?: return null
            return TrainingPaces(easy = p10 * 1.25, tempo = p10 * 1.03, interval = p10 * 0.96)
        }
    }
}

/** One step as FIT writes it (workout_step); null is FIT's "invalid" marker. */
internal data class FitStep(
    val name: String,
    val durationType: Int,
    val durationValue: Long?,
    val targetType: Int?,
    val targetValue: Long?,
    val low: Long?,
    val high: Long?,
    val intensity: Int?,
)

/** A workout file, ready to be copied to a watch. */
class WorkoutFile(val name: String, val bytes: ByteArray)

/** What happened when workouts were copied to a watch. */
data class WorkoutTransferResult(val sent: List<String>, val alreadyThere: List<String>)

object Workouts {
    private const val DURATION_TIME = 0
    private const val DURATION_DISTANCE = 1
    private const val DURATION_OPEN = 5
    private const val DURATION_REPEAT = 6
    private const val TARGET_SPEED = 0
    private const val TARGET_OPEN = 2

    /** Warm-up and cool-down are fixed times; what is left of a session's distance is the work. */
    const val WARMUP_S = 600
    const val COOLDOWN_S = 300
    private const val FRAME_KM = 2.5
    const val REP_M = 400
    const val RECOVERY_S = 90
    private const val KM_PER_REP = 0.6
    private const val PACE_MARGIN_S = 8

    private fun range(pace: Double?): PaceRange? =
        pace?.let { PaceRange((it - PACE_MARGIN_S).roundToInt(), (it + PACE_MARGIN_S).roundToInt()) }

    private fun label(type: SessionType) = when (type) {
        SessionType.EASY -> "Easy"
        SessionType.LONG -> "Long"
        SessionType.TEMPO -> "Tempo"
        SessionType.INTERVALS -> "Intervals"
        SessionType.RACE -> "Race"
    }

    /** The workout for one planned session. Targets use [paces] when there are any, otherwise the steps are open. */
    fun forSession(session: PlannedSession, paces: TrainingPaces?): Workout {
        val meters = (session.distanceKm * 1000).roundToInt()
        val warmup = WorkoutStep("Warm-up", StepIntensity.WARMUP, StepLength.Time(WARMUP_S))
        val cooldown = WorkoutStep("Cool-down", StepIntensity.COOLDOWN, StepLength.Time(COOLDOWN_S))
        val blocks = when (session.type) {
            SessionType.EASY -> listOf(WorkoutBlock(1, listOf(WorkoutStep("Easy run", StepIntensity.ACTIVE, StepLength.Distance(meters)))))
            SessionType.LONG -> listOf(WorkoutBlock(1, listOf(WorkoutStep("Long run", StepIntensity.ACTIVE, StepLength.Distance(meters)))))
            SessionType.RACE -> listOf(WorkoutBlock(1, listOf(WorkoutStep("Race", StepIntensity.ACTIVE, StepLength.Distance(meters)))))
            SessionType.TEMPO -> {
                val tempoM = (((session.distanceKm - FRAME_KM) * 10).roundToInt() * 100).coerceAtLeast(1000)
                listOf(
                    WorkoutBlock(1, listOf(warmup)),
                    WorkoutBlock(1, listOf(WorkoutStep("Tempo", StepIntensity.ACTIVE, StepLength.Distance(tempoM), range(paces?.tempo)))),
                    WorkoutBlock(1, listOf(cooldown)),
                )
            }
            SessionType.INTERVALS -> {
                val reps = ((session.distanceKm - FRAME_KM) / KM_PER_REP).roundToInt().coerceIn(4, 10)
                listOf(
                    WorkoutBlock(1, listOf(warmup)),
                    WorkoutBlock(
                        reps,
                        listOf(
                            WorkoutStep("Fast", StepIntensity.INTERVAL, StepLength.Distance(REP_M), range(paces?.interval)),
                            WorkoutStep("Jog", StepIntensity.RECOVERY, StepLength.Time(RECOVERY_S)),
                        ),
                    ),
                    WorkoutBlock(1, listOf(cooldown)),
                )
            }
        }
        val day = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH).format(session.date)
        return Workout("OC $day ${label(session.type)} ${"%.1f".format(Locale.ROOT, session.distanceKm)} km", blocks)
    }

    /** The steps as FIT lists them: a repeat is a step after its steps that points back at the first one. */
    internal fun fitSteps(workout: Workout): List<FitStep> {
        val out = ArrayList<FitStep>()
        for (block in workout.blocks) {
            val first = out.size
            for (s in block.steps) out += toFit(s)
            if (block.repeat > 1) {
                out += FitStep("Repeat", DURATION_REPEAT, first.toLong(), TARGET_OPEN, block.repeat.toLong(), null, null, null)
            }
        }
        return out
    }

    private fun speedMmPerS(secondsPerKm: Int): Long = (1_000_000.0 / secondsPerKm).roundToLong()

    private fun toFit(s: WorkoutStep): FitStep {
        val (durationType, durationValue) = when (val l = s.length) {
            is StepLength.Time -> DURATION_TIME to (l.seconds * 1000L)
            is StepLength.Distance -> DURATION_DISTANCE to (l.meters * 100L)
            StepLength.Open -> DURATION_OPEN to null
        }
        val pace = s.pace
        return if (pace == null) {
            FitStep(s.name, durationType, durationValue, TARGET_OPEN, null, null, null, s.intensity.fit)
        } else {
            // A custom speed window: target_value 0, low and high in mm/s (the slow pace is the low speed).
            FitStep(s.name, durationType, durationValue, TARGET_SPEED, 0, speedMmPerS(pace.slow), speedMmPerS(pace.fast), s.intensity.fit)
        }
    }

    /** The FIT file of a workout, as a Garmin watch reads a workout. [created] is stored in it. */
    fun toFit(workout: Workout, created: Instant): ByteArray = Fit.exportWorkout(workout.name, fitSteps(workout), created)

    /** `OC_2026-11-16_TEMPO.fit`: safe on any watch file system and sorts by date. */
    fun fileName(session: PlannedSession): String = "OC_${session.date}_${session.type.name}.fit"

    /** The planned sessions of [days] days, starting on the later of [today] and the plan's first day. */
    fun upcoming(plan: TrainingPlan, today: LocalDate, days: Int = 7): List<PlannedSession> {
        val from = maxOf(today, plan.start)
        val until = from.plusDays(days.toLong())
        return plan.weeks.flatMap { it.sessions }.filter { !it.date.isBefore(from) && it.date.isBefore(until) }.sortedBy { it.date }
    }

    fun files(sessions: List<PlannedSession>, paces: TrainingPaces?, created: Instant): List<WorkoutFile> =
        sessions.map { WorkoutFile(fileName(it), toFit(forSession(it, paces), created)) }

    /** Copies the files to the watch. A file that is already there is left alone; any other refusal is thrown. */
    fun send(source: nl.tufein.openconnect.core.watch.WatchSource, files: List<WorkoutFile>): WorkoutTransferResult {
        val sent = ArrayList<String>()
        val already = ArrayList<String>()
        for (f in files) {
            try {
                if (!source.installWorkout(f.name, f.bytes)) {
                    throw nl.tufein.openconnect.core.watch.WatchFaceException(nl.tufein.openconnect.core.watch.WatchFaceException.Kind.CANNOT_WRITE)
                }
                sent += f.name
            } catch (e: nl.tufein.openconnect.core.watch.WatchFaceException) {
                if (e.kind == nl.tufein.openconnect.core.watch.WatchFaceException.Kind.ALREADY_THERE) already += f.name else throw e
            }
        }
        return WorkoutTransferResult(sent, already)
    }
}
