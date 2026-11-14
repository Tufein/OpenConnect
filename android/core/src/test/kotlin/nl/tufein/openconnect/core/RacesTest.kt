package nl.tufein.openconnect.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RacesTest {
    private val today = LocalDate.of(2026, 11, 14)
    private fun effort(km: Double, seconds: Double) = Effort(km * 1000.0, seconds)

    @Test
    fun riegelScalesATimeByTheDistanceRatioToThePowerOnePointZeroSix() {
        // 5 km in 20:00 gives 10 km in 1200 * 2^1.06 = 2501.6 s (41:42).
        assertEquals(2501.6, RacePredictor.scale(5000.0, 1200.0, 10_000.0), 0.5)
        // Running the same distance changes nothing; a shorter distance is faster.
        assertEquals(1200.0, RacePredictor.scale(5000.0, 1200.0, 5000.0), 1e-9)
        assertTrue(RacePredictor.scale(10_000.0, 2500.0, 5000.0) < 1250.0)
    }

    @Test
    fun theSameDistanceIsYourBestAndIsNotScaled() {
        val prediction = RacePredictor.predict(listOf(effort(5.0, 1200.0), effort(10.0, 2600.0)), RaceDistance.K5)!!
        assertEquals(1200.0, prediction.seconds, 0.0)
        assertEquals(Reliability.EXACT, prediction.reliability)
    }

    @Test
    fun theEffortClosestInDistanceIsTheBase() {
        val efforts = listOf(effort(1.0, 200.0), effort(5.0, 1200.0), effort(21.0975, 6000.0))
        val ten = RacePredictor.predict(efforts, RaceDistance.K10)!!
        assertEquals(5000.0, ten.basedOn.distanceM, 0.0) // twice 5K is nearer than a half marathon, 2.1 times
        val marathon = RacePredictor.predict(efforts, RaceDistance.MARATHON)!!
        assertEquals(21_097.5, marathon.basedOn.distanceM, 0.0)
        assertEquals(RacePredictor.scale(21_097.5, 6000.0, 42_195.0), marathon.seconds, 1e-6)
    }

    @Test
    fun aKilometreAloneSaysNothingAboutAnyRace() {
        assertNull(RacePredictor.predict(listOf(effort(1.0, 190.0)), RaceDistance.K5))
        assertEquals(emptyList(), RacePredictor.predictions(emptyList()))
    }

    @Test
    fun theFurtherAPredictionStretchesTheLessItIsTrusted() {
        assertEquals(Reliability.GOOD, RacePredictor.reliability(10_000.0, 21_097.5))
        assertEquals(Reliability.GOOD, RacePredictor.reliability(21_097.5, 42_195.0))
        assertEquals(Reliability.ROUGH, RacePredictor.reliability(5000.0, 21_097.5))
        assertEquals(Reliability.ROUGH, RacePredictor.reliability(10_000.0, 42_195.0))
        assertEquals(Reliability.WEAK, RacePredictor.reliability(5000.0, 42_195.0))
        assertEquals(Reliability.ROUGH, RacePredictor.reliability(21_097.5, 5000.0))
    }

    @Test
    fun allFourDistancesAreInOrderWhenThereIsARunToStartFrom() {
        val all = RacePredictor.predictions(listOf(effort(5.0, 1500.0)))
        assertEquals(RaceDistance.entries, all.map { it.distance })
        assertTrue(all.zipWithNext().all { (a, b) -> a.seconds < b.seconds })
        assertEquals(1500.0 / 5.0, all.first().paceSecondsPerKm, 1e-9)
    }

    @Test
    fun aGoalSurvivesBeingStoredAndBadTextIsRefused() {
        val goal = RaceGoal(LocalDate.of(2027, 2, 12), RaceDistance.HALF, 6300)
        assertEquals("2027-02-12|half|6300", goal.encode())
        assertEquals(goal, RaceGoal.decode(goal.encode()))
        for (bad in listOf("", "2027-02-12|half", "2027-02-30|half|6300", "2027-02-12|ultra|6300", "2027-02-12|half|abc", "2027-02-12|half|59", "2027-02-12|half|99999", "a|b|c|d")) {
            assertNull(RaceGoal.decode(bad), bad)
        }
    }

    @Test
    fun aStarterGoalIsTwelveWeeksAwayAndRoundedToHalfAMinute() {
        val predictions = RacePredictor.predictions(listOf(effort(5.0, 1213.0)))
        val goal = RaceGoal.starter(RaceDistance.K5, today, predictions)
        assertEquals(today.plusWeeks(12), goal.date)
        assertEquals(1200, goal.targetSeconds) // 1213 s is 40.4 half minutes, which rounds to 40
        assertEquals(1230, RaceGoal.starter(RaceDistance.K5, today, RacePredictor.predictions(listOf(effort(5.0, 1216.0)))).targetSeconds)
    }

    @Test
    fun withoutRunsAStarterUsesAUsualTime() {
        val goal = RaceGoal.starter(RaceDistance.MARATHON, today, emptyList())
        assertEquals(4 * 3600, goal.targetSeconds)
    }

    @Test
    fun theVerdictTolerates2PercentEitherWay() {
        val goal = RaceGoal(today.plusDays(50), RaceDistance.K10, 3000)
        fun verdictFor(predicted: Double): RaceVerdict {
            val p = RacePrediction(RaceDistance.K10, predicted, effort(10.0, predicted), Reliability.EXACT)
            return Races.outlook(goal, listOf(p), today).verdict
        }
        assertEquals(RaceVerdict.AHEAD, verdictFor(2940.0))
        assertEquals(RaceVerdict.ON_TRACK, verdictFor(2941.0))
        assertEquals(RaceVerdict.ON_TRACK, verdictFor(3060.0))
        assertEquals(RaceVerdict.BEHIND, verdictFor(3061.0))
    }

    @Test
    fun theOutlookCountsDaysAndComparesAgainstThePredictionForTheSameDistance() {
        val goal = RaceGoal(today.plusDays(84), RaceDistance.K10, 3000)
        val state = Races.state(listOf(effort(5.0, 1200.0)), goal, today)
        val outlook = assertNotNull(state.outlook)
        assertEquals(84, outlook.daysLeft)
        assertEquals(RaceDistance.K10, outlook.predicted?.distance)
        assertEquals(RaceVerdict.AHEAD, outlook.verdict) // 41:42 against 50:00
        assertTrue(outlook.gapSeconds!! < 0)
        assertEquals(300.0, goal.targetPaceSecondsPerKm(), 1e-9)
    }

    @Test
    fun withoutDataTheVerdictIsNoDataAndAPastRaceHasNegativeDays() {
        val past = RaceGoal(today.minusDays(3), RaceDistance.K5, 1500)
        val outlook = Races.state(emptyList(), past, today).outlook!!
        assertEquals(RaceVerdict.NO_DATA, outlook.verdict)
        assertNull(outlook.gapSeconds)
        assertEquals(-3, outlook.daysLeft)
        assertNull(Races.state(emptyList(), null, today).outlook)
    }
}
