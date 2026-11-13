package nl.tufein.openconnect.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AchievementsTest {
    private val repo = InMemoryRepository()
    private var counter = 0

    private fun activity(
        sport: Sport = Sport.RUNNING,
        start: String = "2026-05-02T09:00:00Z",
        km: Double = 5.0,
        climbM: Double = 0.0,
    ) {
        counter++
        repo.upsertActivity(
            Activity(
                id = "a:$counter",
                name = "Activity $counter",
                sport = sport,
                startTime = Instant.parse(start),
                durationS = km * 360,
                distanceM = km * 1000,
                elevationGainM = climbM,
                track = emptyList(),
            ),
        )
    }

    private fun trophies(zone: ZoneId = ZoneId.of("UTC")) = AchievementsCalculator.compute(repo, zone).trophies.associateBy { it.key }

    private fun Trophy.assertUnlocked(on: String? = null) {
        assertTrue(unlocked, "$key should be unlocked ($current of $target)")
        if (on != null) assertEquals(LocalDate.parse(on), unlockedOn, "$key unlocked on")
    }

    private fun Trophy.assertLocked(current: Double) {
        assertFalse(unlocked, "$key should be locked")
        assertEquals(current, this.current, 0.001, "$key progress")
        assertNull(unlockedOn)
    }

    @Test
    fun anEmptyAppHasEveryTrophyLockedAtZero() {
        val result = AchievementsCalculator.compute(repo, ZoneId.of("UTC"))
        assertEquals(TrophyKey.entries.toList(), result.trophies.map { it.key })
        assertTrue(result.trophies.none { it.unlocked })
        assertTrue(result.trophies.all { it.current == 0.0 && it.progress == 0.0 })
        assertEquals(1, result.player.level)
        assertEquals(0, result.player.xp)
    }

    @Test
    fun theFirstActivityUnlocksOnItsDay() {
        activity(start = "2026-05-09T09:00:00Z")
        activity(start = "2026-05-02T09:00:00Z")
        trophies().getValue(TrophyKey.FIRST_ACTIVITY).assertUnlocked(on = "2026-05-02")
    }

    @Test
    fun distanceMilestonesPerSport() {
        activity(Sport.RUNNING, km = 4.9)
        activity(Sport.CYCLING, km = 49.0)
        trophies().getValue(TrophyKey.RUN_5K).assertLocked(4.9)
        trophies().getValue(TrophyKey.RIDE_50K).assertLocked(49.0)
        assertEquals(0.98, trophies().getValue(TrophyKey.RUN_5K).progress, 0.001)

        activity(Sport.RUNNING, km = 10.0, start = "2026-05-12T09:00:00Z")
        activity(Sport.CYCLING, km = 60.0, start = "2026-05-14T09:00:00Z")
        val t = trophies()
        t.getValue(TrophyKey.RUN_5K).assertUnlocked(on = "2026-05-12")
        t.getValue(TrophyKey.RUN_10K).assertUnlocked(on = "2026-05-12")
        t.getValue(TrophyKey.RUN_HALF).assertLocked(10.0)
        t.getValue(TrophyKey.RIDE_50K).assertUnlocked(on = "2026-05-14")
    }

    @Test
    fun aWalkDoesNotCountAsARun() {
        activity(Sport.WALKING, km = 25.0)
        trophies().getValue(TrophyKey.RUN_HALF).assertLocked(0.0)
    }

    @Test
    fun totalDistanceUnlocksWhenTheRunningSumCrossesTheTarget() {
        activity(Sport.RUNNING, km = 60.0, start = "2026-05-01T09:00:00Z")
        activity(Sport.CYCLING, km = 30.0, start = "2026-05-03T09:00:00Z")
        trophies().getValue(TrophyKey.DISTANCE_100).assertLocked(90.0)
        activity(Sport.WALKING, km = 15.0, start = "2026-05-05T09:00:00Z")
        val t = trophies()
        t.getValue(TrophyKey.DISTANCE_100).assertUnlocked(on = "2026-05-05")
        t.getValue(TrophyKey.DISTANCE_500).assertLocked(105.0)
    }

    @Test
    fun climbingAddsUpTowardsEverest() {
        activity(climbM = 5000.0, start = "2026-05-01T09:00:00Z")
        activity(climbM = 4000.0, start = "2026-05-08T09:00:00Z")
        trophies().getValue(TrophyKey.CLIMB_EVEREST).assertUnlocked(on = "2026-05-08")
    }

    @Test
    fun stepGoalStreaksNeedConsecutiveDays() {
        val start = LocalDate.parse("2026-05-01")
        // 6 good days, a miss, then 7 good days: the longest streak is 7, reached on the 7th day of the second run.
        for (i in 0 until 6) repo.upsertDaily(TestData.daily(start.plusDays(i.toLong()), steps = 10_500))
        repo.upsertDaily(TestData.daily(start.plusDays(6), steps = 4_000))
        for (i in 7 until 14) repo.upsertDaily(TestData.daily(start.plusDays(i.toLong()), steps = 12_000))
        val t = trophies()
        t.getValue(TrophyKey.STEP_STREAK_7).assertUnlocked(on = "2026-05-14")
        t.getValue(TrophyKey.STEP_STREAK_30).assertLocked(7.0)
    }

    @Test
    fun aDayWithoutDataBreaksAStreak() {
        val start = LocalDate.parse("2026-05-01")
        for (i in listOf(0, 1, 2, 4, 5, 6)) repo.upsertDaily(TestData.daily(start.plusDays(i.toLong()), steps = 11_000))
        trophies().getValue(TrophyKey.STEP_STREAK_7).assertLocked(3.0)
    }

    @Test
    fun theStepGoalOfTheDayIsUsedWhenThereIsOne() {
        val start = LocalDate.parse("2026-05-01")
        // 8000 steps beats a personal goal of 7500, even though the usual default is 10 000.
        for (i in 0 until 7) repo.upsertDaily(TestData.daily(start.plusDays(i.toLong()), steps = 8_000).copy(stepsGoal = 7_500))
        trophies().getValue(TrophyKey.STEP_STREAK_7).assertUnlocked(on = "2026-05-07")
    }

    @Test
    fun aMillionStepsInTotal() {
        val start = LocalDate.parse("2026-01-01")
        for (i in 0 until 100) repo.upsertDaily(TestData.daily(start.plusDays(i.toLong()), steps = 10_000))
        trophies().getValue(TrophyKey.STEPS_MILLION).assertUnlocked(on = "2026-04-10")
    }

    @Test
    fun sleepStreakNeedsSevenHoursSevenNightsRunning() {
        val start = LocalDate.parse("2026-05-01")
        for (i in 0 until 7) repo.upsertSleep(TestData.sleep(start.plusDays(i.toLong()), hours = if (i == 3) 6.9 else 7.2))
        trophies().getValue(TrophyKey.SLEEP_STREAK_7).assertLocked(3.0)
        repo.upsertSleep(TestData.sleep(start.plusDays(3), hours = 7.0))
        trophies().getValue(TrophyKey.SLEEP_STREAK_7).assertUnlocked(on = "2026-05-07")
    }

    @Test
    fun threeDifferentSports() {
        activity(Sport.RUNNING, start = "2026-05-01T09:00:00Z")
        activity(Sport.YOGA, start = "2026-05-02T09:00:00Z")
        trophies().getValue(TrophyKey.VARIETY_3).assertLocked(2.0)
        activity(Sport.SWIMMING, start = "2026-05-03T09:00:00Z")
        trophies().getValue(TrophyKey.VARIETY_3).assertUnlocked(on = "2026-05-03")
    }

    @Test
    fun aWeekWith150IntensityMinutes() {
        // Garmin counts vigorous minutes double: 20 moderate + 5 vigorous = 30 a day.
        val monday = LocalDate.parse("2026-05-04")
        for (i in 0 until 4) repo.upsertDaily(TestData.daily(monday.plusDays(i.toLong())))
        trophies().getValue(TrophyKey.WEEK_150).assertLocked(120.0)
        repo.upsertDaily(TestData.daily(monday.plusDays(4)))
        trophies().getValue(TrophyKey.WEEK_150).assertUnlocked(on = "2026-05-08")
        // Minutes in another week do not add up: the same five days spread over two weeks stay below 150.
        val spread = InMemoryRepository()
        for (i in listOf(0, 1, 2, 6, 7)) spread.upsertDaily(TestData.daily(monday.plusDays(i.toLong())))
        val split = AchievementsCalculator.compute(spread, ZoneId.of("UTC")).trophies.first { it.key == TrophyKey.WEEK_150 }
        assertFalse(split.unlocked)
        assertEquals(120.0, split.current, 0.001)
    }

    @Test
    fun earlyBirdUsesTheLocalClock() {
        // 05:30 UTC is early for a clock set to UTC, but 07:30 in Amsterdam in May, which is no longer early.
        for (i in 1..5) activity(start = "2026-05-0${i}T05:30:00Z")
        trophies(ZoneId.of("UTC")).getValue(TrophyKey.EARLY_BIRD).assertUnlocked(on = "2026-05-05")
        trophies(ZoneId.of("Europe/Amsterdam")).getValue(TrophyKey.EARLY_BIRD).assertLocked(0.0)
    }

    @Test
    fun consistencyMeansThreeActivitiesInFourWeeksRunning() {
        fun week(mondayStart: String, count: Int) {
            for (i in 0 until count) {
                activity(start = Instant.parse(mondayStart).plusSeconds(86_400L * i).toString())
            }
        }
        week("2026-05-04T09:00:00Z", 3)
        week("2026-05-11T09:00:00Z", 4)
        week("2026-05-18T09:00:00Z", 2) // a thin week breaks it
        week("2026-05-25T09:00:00Z", 3)
        week("2026-06-01T09:00:00Z", 3)
        week("2026-06-08T09:00:00Z", 3)
        trophies().getValue(TrophyKey.CONSISTENT_4).assertLocked(3.0)
        week("2026-06-15T09:00:00Z", 3)
        // It unlocks on the day the third activity of that last week was done.
        trophies().getValue(TrophyKey.CONSISTENT_4).assertUnlocked(on = "2026-06-17")
    }

    @Test
    fun levelsComeFromExperiencePoints() {
        // 10 xp per km, 1 per 500 steps, 5 per activity, 100 per trophy.
        activity(Sport.WALKING, km = 2.0, start = "2026-05-01T09:00:00Z") // 20 + 5, and the first-activity trophy (100)
        repo.upsertDaily(TestData.daily(LocalDate.parse("2026-05-01"), steps = 5_000)) // 10 xp
        val player = AchievementsCalculator.compute(repo, ZoneId.of("UTC")).player
        assertEquals(135, player.xp)
        assertEquals(2, player.level) // level 2 starts at 100, level 3 at 400
        assertEquals(35, player.xpIntoLevel)
        assertEquals(300, player.xpForNext)
        assertNotNull(player)

        assertEquals(1, PlayerLevel.of(0).level)
        assertEquals(1, PlayerLevel.of(99).level)
        assertEquals(2, PlayerLevel.of(100).level)
        assertEquals(3, PlayerLevel.of(400).level)
        assertEquals(4, PlayerLevel.of(900).level)
        assertEquals(0, PlayerLevel.of(100).xpIntoLevel)
    }
}
