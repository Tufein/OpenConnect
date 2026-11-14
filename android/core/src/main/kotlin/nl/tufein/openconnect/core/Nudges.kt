package nl.tufein.openconnect.core

import java.time.DayOfWeek
import java.time.LocalDate

enum class NudgeKind { GOAL_CLOSE, WEEKLY_SUMMARY, LOAD_JUMP, SHOES_WORN }

/**
 * A notification worth sending. [key] is unique per occasion (for example one goal in one week), so remembering the keys
 * already sent is enough to never send the same nudge twice. [numbers] fill the sentence: see [Nudges].
 */
data class Nudge(val key: String, val kind: NudgeKind, val goal: String? = null, val numbers: List<Int> = emptyList())

/**
 * Decides which gentle notifications are due right now, from data the app already has. Pure, so every rule is tested.
 *
 * - GOAL_CLOSE: from [GOAL_HOUR] on, a goal at 80% or more (for activities: one missing) but not reached ([goal] is "intensity", "distance" or
 *   "activities"; [numbers] = what is still missing, rounded up).
 * - WEEKLY_SUMMARY: on Monday from [SUMMARY_HOUR], when last week had at least one activity ([numbers] = activities,
 *   kilometres, goals reached).
 * - LOAD_JUMP: once a week when the training load is OVERREACHING.
 * - SHOES_WORN: from [SUMMARY_HOUR] on, once per pair and limit, when a pair in use has reached its replace-after limit
 *   ([goal] = the pair's name, [numbers] = its kilometres, rounded; a new limit gives a new key).
 */
object Nudges {
    const val GOAL_HOUR = 17
    const val SUMMARY_HOUR = 8
    const val CLOSE_FRACTION = 0.8

    fun due(
        today: LocalDate,
        hour: Int,
        goals: WeeklyGoals,
        thisWeek: WeeklySummary,
        lastWeek: WeeklySummary,
        load: TrainingLoad?,
        alreadySent: Set<String>,
        shoesWorn: List<ShoeUsage> = emptyList(),
    ): List<Nudge> {
        val found = ArrayList<Nudge>()
        val weekStart = thisWeek.weekStart

        if (hour >= GOAL_HOUR) {
            val p = goals.progress(thisWeek)
            for ((name, g) in listOf("intensity" to p.intensity, "distance" to p.distance, "activities" to p.activities)) {
                // a goal counted in whole activities is close when one is missing (2 of 3 is only 67%)
                val close = g.done >= g.goal * CLOSE_FRACTION || (name == "activities" && g.done >= 1 && g.goal - g.done <= 1)
                if (g.goal > 0 && !g.reached && close) {
                    found += Nudge("goal-close:$weekStart:$name", NudgeKind.GOAL_CLOSE, name, listOf(Math.ceil(g.goal - g.done).toInt().coerceAtLeast(1)))
                }
            }
        }

        if (today.dayOfWeek == DayOfWeek.MONDAY && hour >= SUMMARY_HOUR && lastWeek.activityCount > 0) {
            val lastGoals = goals.progress(lastWeek)
            found += Nudge(
                "weekly:$weekStart", NudgeKind.WEEKLY_SUMMARY,
                numbers = listOf(lastWeek.activityCount, Math.round(lastWeek.totalDistanceM / 1000.0).toInt(), lastGoals.reachedCount),
            )
        }

        if (load?.status == LoadStatus.OVERREACHING) found += Nudge("load:$weekStart", NudgeKind.LOAD_JUMP)

        if (hour >= SUMMARY_HOUR) {
            for (u in shoesWorn) {
                found += Nudge("shoes:${u.shoe.id}:${u.shoe.retireKm}", NudgeKind.SHOES_WORN, u.shoe.name, listOf(Math.round(u.km).toInt()))
            }
        }

        return found.filter { it.key !in alreadySent }
    }

    /** Keeps the list of sent keys from growing for ever: the newest [KEEP] stay. */
    const val KEEP = 40

    fun remember(sent: List<String>, new: List<Nudge>): List<String> = (sent + new.map { it.key }).distinct().takeLast(KEEP)
}
