package nl.tufein.openconnect.core

/**
 * What the person wrote or chose themselves and that lives outside the database: the journal, the shoes, the training
 * plan, the race, the maximum heart rate and the weekly goals. Each field is the text the settings store keeps, so the
 * app's own formats ([JournalBook], [GearBook], PlanConfig, [RaceGoal]) stay the one source of truth.
 *
 * Added to the backup in v0.56.0; before that a new phone lost all of it.
 */
data class UserData(
    val journal: String = "",
    val gear: String = "",
    val trainingPlan: String = "",
    val raceGoal: String = "",
    val maxHr: Int = 0,
    val weeklyGoals: WeeklyGoals = WeeklyGoals(),
) {
    /**
     * This phone's data with a backup's added to it, and nothing of this phone's overwritten:
     * journal days are joined (the tags of one day are united), shoes and overrides are added when this phone does not
     * have them yet, and the plan, race, maximum heart rate and goals are only taken when this phone has none of its own
     * (goals count as "own" once they differ from the defaults).
     */
    fun mergedWith(backup: UserData): UserData = UserData(
        journal = mergeJournal(journal, backup.journal),
        gear = mergeGear(gear, backup.gear),
        trainingPlan = trainingPlan.ifBlank { backup.trainingPlan },
        raceGoal = raceGoal.ifBlank { backup.raceGoal },
        maxHr = if (maxHr > 0) maxHr else backup.maxHr,
        weeklyGoals = if (weeklyGoals == WeeklyGoals()) backup.weeklyGoals else weeklyGoals,
    )

    private companion object {
        fun mergeJournal(mine: String, theirs: String): String {
            if (theirs.isBlank()) return mine
            val a = JournalBook.decode(mine).days
            val b = JournalBook.decode(theirs).days
            val days = (a.keys + b.keys).associateWith { a[it].orEmpty() + b[it].orEmpty() }
            return JournalBook(days).encode()
        }

        fun mergeGear(mine: String, theirs: String): String {
            if (theirs.isBlank()) return mine
            val a = GearBook.decode(mine)
            val b = GearBook.decode(theirs)
            val known = a.shoes.map { it.id }.toSet()
            return GearBook(a.shoes + b.shoes.filter { it.id !in known }, b.overrides + a.overrides).encode()
        }
    }
}
