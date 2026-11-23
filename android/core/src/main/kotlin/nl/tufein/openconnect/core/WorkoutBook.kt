package nl.tufein.openconnect.core

/**
 * A workout you build yourself, in the shape most people actually use: a warm-up, a number of fast repeats each
 * followed by a rest, and a cool-down. [toWorkout] turns it into the same [Workout] the plan uses, so the existing FIT
 * export and the upload to a watch work unchanged.
 *
 * Durations are seconds and every limit is checked in [problem]; a workout that does not pass is never saved or sent.
 */
data class OwnWorkout(
    val id: String,
    val name: String,
    val warmupS: Int = 600,
    val fastS: Int = 240,
    val recoveryS: Int = 90,
    val repeats: Int = 4,
    val cooldownS: Int = 300,
    val intensity: StepIntensity = StepIntensity.INTERVAL,
    /** When set, the fast step is this many metres instead of [fastS] seconds. */
    val fastMeters: Int? = null,
    /** The pace the fast step asks for, seconds per kilometre; both or neither. */
    val paceFastS: Int? = null,
    val paceSlowS: Int? = null,
) {
    /**
     * How long the fast step takes: from the distance and its pace when it is a distance step, else [fastS]. A distance
     * step without a pace falls back to [fastS], which then only shapes the estimate.
     */
    val fastStepS: Int
        get() {
            val meters = fastMeters ?: return fastS
            val pace = paceSlowS ?: return fastS
            return ((meters.toLong() * pace) / 1_000).toInt()
        }

    val totalS: Int get() = warmupS + repeats * (fastStepS + recoveryS) + cooldownS

    val problem: Problem?
        get() {
            val fastPace = paceFastS
            val slowPace = paceSlowS
            val meters = fastMeters
            return when {
                name.isBlank() -> Problem.NAME
                warmupS !in 0..MAX_WARMUP_S -> Problem.WARMUP
                meters == null && fastS !in MIN_FAST_S..MAX_FAST_S -> Problem.FAST
                meters != null && meters !in MIN_FAST_M..MAX_FAST_M -> Problem.FAST_METERS
                (fastPace == null) != (slowPace == null) -> Problem.PACE
                fastPace != null && slowPace != null &&
                    (fastPace !in MIN_PACE_S..MAX_PACE_S || slowPace !in MIN_PACE_S..MAX_PACE_S || fastPace > slowPace) -> Problem.PACE
                recoveryS !in 0..MAX_RECOVERY_S -> Problem.RECOVERY
                repeats !in 1..MAX_REPEATS -> Problem.REPEATS
                cooldownS !in 0..MAX_COOLDOWN_S -> Problem.COOLDOWN
                else -> null
            }
        }

    /** The plan-shaped workout, with a block that repeats the fast/rest pair [repeats] times. */
    fun toWorkout(): Workout {
        val length = fastMeters?.let { StepLength.Distance(it.coerceIn(MIN_FAST_M, MAX_FAST_M)) }
            ?: StepLength.Time(fastS.coerceIn(MIN_FAST_S, MAX_FAST_S))
        val pace = if (paceFastS != null && paceSlowS != null) PaceRange(paceFastS, paceSlowS) else null
        val blocks = buildList {
            if (warmupS > 0) add(WorkoutBlock(1, listOf(WorkoutStep("Warm-up", StepIntensity.WARMUP, StepLength.Time(warmupS)))))
            add(
                WorkoutBlock(
                    repeats.coerceIn(1, MAX_REPEATS),
                    listOf(
                        WorkoutStep(name.trim(), intensity, length, pace),
                        WorkoutStep("Rest", StepIntensity.RECOVERY, StepLength.Time(recoveryS.coerceIn(0, MAX_RECOVERY_S))),
                    ),
                ),
            )
            if (cooldownS > 0) add(WorkoutBlock(1, listOf(WorkoutStep("Cool-down", StepIntensity.COOLDOWN, StepLength.Time(cooldownS)))))
        }
        return Workout(name.trim(), blocks)
    }

    enum class Problem { NAME, WARMUP, FAST, FAST_METERS, PACE, RECOVERY, REPEATS, COOLDOWN }

    companion object {
        const val MAX_WARMUP_S = 3_600
        const val MIN_FAST_S = 30
        const val MAX_FAST_S = 3_600
        const val MIN_FAST_M = 100
        const val MAX_FAST_M = 50_000

        /** Seconds per kilometre: 2:00 is a sprint, 20:00 a walk. */
        const val MIN_PACE_S = 120
        const val MAX_PACE_S = 1_200
        const val MAX_RECOVERY_S = 1_800
        const val MAX_REPEATS = 50
        const val MAX_COOLDOWN_S = 3_600

        fun id(now: java.time.Instant): String = "own-${now.toEpochMilli()}"
    }
}

/**
 * The workouts saved on this phone, kept as lines in the settings so they travel in the backup without a new table.
 * One workout per line; a broken line is skipped instead of losing the rest, the same way [GearBook] does it.
 */
data class WorkoutBook(val workouts: List<OwnWorkout> = emptyList()) {
    fun plus(workout: OwnWorkout): WorkoutBook =
        WorkoutBook(workouts.filterNot { it.id == workout.id } + workout)

    fun without(id: String): WorkoutBook = WorkoutBook(workouts.filterNot { it.id == id })

    fun encode(): String = workouts.joinToString("\n") { w ->
        listOf(
            "W",
            esc(w.id),
            esc(w.name),
            w.warmupS.toString(),
            w.fastS.toString(),
            w.recoveryS.toString(),
            w.repeats.toString(),
            w.cooldownS.toString(),
            w.intensity.fit.toString(),
            w.fastMeters?.toString().orEmpty(),
            w.paceFastS?.toString().orEmpty(),
            w.paceSlowS?.toString().orEmpty(),
        ).joinToString("|")
    }

    companion object {
        fun decode(text: String): WorkoutBook {
            val list = text.lineSequence().mapNotNull { line(it) }.toList()
            return WorkoutBook(list)
        }

        private fun line(raw: String): OwnWorkout? {
            val parts = raw.trim().split('|')
            if (parts.size < 9 || parts[0] != "W") return null
            return OwnWorkout(
                id = unesc(parts[1]),
                name = unesc(parts[2]),
                warmupS = parts[3].toIntOrNull() ?: return null,
                fastS = parts[4].toIntOrNull() ?: return null,
                recoveryS = parts[5].toIntOrNull() ?: return null,
                repeats = parts[6].toIntOrNull() ?: return null,
                cooldownS = parts[7].toIntOrNull() ?: return null,
                intensity = StepIntensity.entries.firstOrNull { it.fit == parts[8].toIntOrNull() } ?: StepIntensity.INTERVAL,
                // The three after the intensity came in v1.8.0; a line from before then simply has them empty.
                fastMeters = parts.getOrNull(9)?.toIntOrNull(),
                paceFastS = parts.getOrNull(10)?.toIntOrNull(),
                paceSlowS = parts.getOrNull(11)?.toIntOrNull(),
            ).takeIf { it.problem == null }
        }

        private fun esc(value: String): String =
            value.replace("%", "%25").replace("|", "%7C").replace("\n", "%0A").replace("\r", "%0D")

        private fun unesc(value: String): String =
            value.replace("%0A", "\n").replace("%0D", "\r").replace("%7C", "|").replace("%25", "%")
    }
}

/** `OC_own-<millis>.fit`: safe on any watch file system and unique per work-out. */
fun OwnWorkout.fitFile(created: java.time.Instant): WorkoutFile =
    WorkoutFile("OC_$id.fit", Workouts.toFit(toWorkout(), created))
