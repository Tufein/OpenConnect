package nl.tufein.openconnect.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/** The distances OpenConnect predicts and lets you set a race goal for. */
enum class RaceDistance(val key: String, val meters: Double, val defaultSeconds: Int) {
    K5("5k", 5000.0, 30 * 60),
    K10("10k", 10_000.0, 60 * 60),
    HALF("half", 21_097.5, 2 * 3600),
    MARATHON("marathon", 42_195.0, 4 * 3600),
    ;

    val km: Double get() = meters / 1000.0

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key }
    }
}

/** How far a prediction stretches: from the very distance (exact) to a long way beyond what you have run (weak). */
enum class Reliability { EXACT, GOOD, ROUGH, WEAK }

/** A predicted time for [distance], and the best effort it was worked out from. */
data class RacePrediction(val distance: RaceDistance, val seconds: Double, val basedOn: Effort, val reliability: Reliability) {
    val paceSecondsPerKm: Double get() = seconds / distance.km
}

/**
 * Predicts race times from best efforts with Riegel's formula, `t2 = t1 * (d2 / d1) ^ 1.06`. It is a rule of thumb that
 * holds best for distances close to the one you have run, so every prediction says how far it stretches. Garmin's own
 * Race Predictor is not offered to apps; this is OpenConnect's own estimate.
 */
object RacePredictor {
    const val RIEGEL_EXPONENT = 1.06

    /** Efforts shorter than this say little about endurance; a fast kilometre must not predict a marathon. */
    const val MIN_BASE_M = 4000.0

    fun scale(baseMeters: Double, baseSeconds: Double, targetMeters: Double): Double =
        baseSeconds * (targetMeters / baseMeters).pow(RIEGEL_EXPONENT)

    /** Within 1 percent of the same distance it is exact, up to 2.5 times it is good, up to 4.5 times rough. */
    fun reliability(baseMeters: Double, targetMeters: Double): Reliability {
        val ratio = max(targetMeters / baseMeters, baseMeters / targetMeters)
        return when {
            ratio < 1.01 -> Reliability.EXACT
            ratio <= 2.5 -> Reliability.GOOD
            ratio <= 4.5 -> Reliability.ROUGH
            else -> Reliability.WEAK
        }
    }

    /** The prediction from the effort closest to [distance] (on a log scale), or null without an effort of 4 km or more. */
    fun predict(efforts: List<Effort>, distance: RaceDistance): RacePrediction? {
        val base = efforts
            .filter { it.distanceM >= MIN_BASE_M && it.seconds > 0 }
            .minByOrNull { abs(ln(distance.meters / it.distanceM)) } ?: return null
        val reliability = reliability(base.distanceM, distance.meters)
        val seconds = if (reliability == Reliability.EXACT) base.seconds else scale(base.distanceM, base.seconds, distance.meters)
        return RacePrediction(distance, seconds, base, reliability)
    }

    fun predictions(efforts: List<Effort>): List<RacePrediction> = RaceDistance.entries.mapNotNull { predict(efforts, it) }
}

/** A race the user is aiming at. Stored as `date|distance|seconds`, which is all it needs. */
data class RaceGoal(val date: LocalDate, val distance: RaceDistance, val targetSeconds: Int) {
    fun encode(): String = "$date|${distance.key}|$targetSeconds"

    fun targetPaceSecondsPerKm(): Double = targetSeconds / distance.km

    companion object {
        const val MIN_SECONDS = 10 * 60
        const val MAX_SECONDS = 8 * 3600

        fun decode(text: String): RaceGoal? {
            val parts = text.split("|")
            if (parts.size != 3) return null
            val date = runCatching { LocalDate.parse(parts[0]) }.getOrNull() ?: return null
            val distance = RaceDistance.of(parts[1]) ?: return null
            val seconds = parts[2].toIntOrNull()?.takeIf { it in MIN_SECONDS..MAX_SECONDS } ?: return null
            return RaceGoal(date, distance, seconds)
        }

        /** A first goal twelve weeks out: the predicted time rounded to 30 seconds, or a usual time when there is none. */
        fun starter(distance: RaceDistance, today: LocalDate, predictions: List<RacePrediction>): RaceGoal {
            val predicted = predictions.firstOrNull { it.distance == distance }?.seconds
            val seconds = predicted?.let { (it / 30.0).roundToInt() * 30 } ?: distance.defaultSeconds
            return RaceGoal(today.plusWeeks(12), distance, seconds.coerceIn(MIN_SECONDS, MAX_SECONDS))
        }
    }
}

enum class RaceVerdict { AHEAD, ON_TRACK, BEHIND, NO_DATA }

/** Where you stand against a goal: days left (negative once the race is over), the prediction and a plain verdict. */
data class RaceOutlook(
    val goal: RaceGoal,
    val daysLeft: Long,
    val predicted: RacePrediction?,
    val verdict: RaceVerdict,
) {
    /** Seconds the prediction is slower than the target; negative means faster. Null without a prediction. */
    val gapSeconds: Double? get() = predicted?.let { it.seconds - goal.targetSeconds }
}

/** Everything the Plan tab shows about racing. */
data class RaceState(val predictions: List<RacePrediction> = emptyList(), val outlook: RaceOutlook? = null)

object Races {
    /** Within 2 percent of the target counts as on track. */
    const val TOLERANCE = 0.02

    fun outlook(goal: RaceGoal, predictions: List<RacePrediction>, today: LocalDate): RaceOutlook {
        val predicted = predictions.firstOrNull { it.distance == goal.distance }
        val verdict = when {
            predicted == null -> RaceVerdict.NO_DATA
            predicted.seconds <= goal.targetSeconds * (1 - TOLERANCE) -> RaceVerdict.AHEAD
            predicted.seconds <= goal.targetSeconds * (1 + TOLERANCE) -> RaceVerdict.ON_TRACK
            else -> RaceVerdict.BEHIND
        }
        return RaceOutlook(goal, ChronoUnit.DAYS.between(today, goal.date), predicted, verdict)
    }

    fun state(efforts: List<Effort>, goal: RaceGoal?, today: LocalDate): RaceState {
        val predictions = RacePredictor.predictions(efforts)
        return RaceState(predictions, goal?.let { outlook(it, predictions, today) })
    }
}
