package nl.tufein.openconnect.core

import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Vo2MaxTest {
    private val today = LocalDate.parse("2026-06-30")

    private fun run(
        daysAgo: Long,
        km: Double = 10.0,
        minutes: Double = 60.0,
        avgHr: Int? = 150,
        sport: Sport = Sport.RUNNING,
        id: String = "v-$daysAgo-$km-$minutes-${avgHr ?: 0}",
    ) = Activity(
        id = id,
        sport = sport,
        startTime = today.minusDays(daysAgo).atTime(8, 0).toInstant(ZoneOffset.UTC),
        durationS = minutes * 60,
        distanceM = km * 1000,
        avgHr = avgHr,
    )

    @Test
    fun `the oxygen cost of running follows Daniels and Gilbert`() {
        assertEquals(36.0116, Vo2Max.oxygenCost(200.0), 1e-4)
        assertEquals(-4.60 + 0.182258 * 100 + 0.000104 * 100 * 100, Vo2Max.oxygenCost(100.0), 1e-9)
    }

    @Test
    fun `the share of VO2max you can hold falls with the time and settles at 80 percent`() {
        assertEquals(0.888, Vo2Max.fractionForDuration(60.0), 1e-3)
        assertEquals(0.80, Vo2Max.fractionForDuration(100_000.0), 1e-6)
        assertTrue(Vo2Max.fractionForDuration(5.0) > Vo2Max.fractionForDuration(30.0))
    }

    @Test
    fun `a 20 minute 5k and a three hour ten minute marathon are the same VO2max of about 50`() {
        assertEquals(50.0, Vo2Max.fromRaceEffort(5000.0, 19 * 60 + 57.0), 0.5)
        assertEquals(50.0, Vo2Max.fromRaceEffort(42_195.0, 3 * 3600 + 10 * 60 + 49.0), 0.5)
    }

    @Test
    fun `predicted times for a VO2max of 50 match the published tables within one percent`() {
        fun near(expected: Double, actual: Double) = assertEquals(expected, actual, expected * 0.01)
        near(19 * 60 + 57.0, Vo2Max.predictSeconds(50.0, 5000.0))
        near(41 * 60 + 21.0, Vo2Max.predictSeconds(50.0, 10_000.0))
        near(3600 + 31 * 60 + 35.0, Vo2Max.predictSeconds(50.0, 21_097.5))
        near(3 * 3600 + 10 * 60 + 49.0, Vo2Max.predictSeconds(50.0, 42_195.0))
    }

    @Test
    fun `predicting from a result and back returns the result`() {
        for ((d, t) in listOf(5000.0 to 1500.0, 10_000.0 to 3300.0, 42_195.0 to 14_400.0)) {
            assertEquals(t, Vo2Max.predictSeconds(Vo2Max.fromRaceEffort(d, t), d), 0.5)
        }
    }

    @Test
    fun `a better VO2max predicts faster times`() {
        assertTrue(Vo2Max.predictSeconds(55.0, 10_000.0) < Vo2Max.predictSeconds(45.0, 10_000.0))
    }

    @Test
    fun `a run gives a VO2max from its pace and its heart rate`() {
        // 10 km in 60 minutes is 166.67 m/min (cost 28.665); 150 of 190 is 78.9 percent of the maximum, which is 65.5 percent of VO2max.
        val estimate = Vo2Max.estimateFromRun(run(2), maxHr = 190)!!
        assertEquals(43.74, estimate.vo2max, 0.05)
        assertEquals(today.minusDays(2), estimate.date)
    }

    @Test
    fun `short, easy, hr-less or non-running activities give no estimate`() {
        assertNull(Vo2Max.estimateFromRun(run(2, km = 2.0, minutes = 12.0), 190)) // under 15 minutes
        assertNull(Vo2Max.estimateFromRun(run(2, km = 2.0, minutes = 20.0), 190)) // under 2.5 km
        assertNull(Vo2Max.estimateFromRun(run(2, avgHr = 100), 190)) // 53 percent of max: too easy to say anything
        assertNull(Vo2Max.estimateFromRun(run(2, avgHr = 185), 190)) // 97 percent: no steady state
        assertNull(Vo2Max.estimateFromRun(run(2, avgHr = null), 190))
        assertNull(Vo2Max.estimateFromRun(run(2, sport = Sport.CYCLING), 190))
        assertNull(Vo2Max.estimateFromRun(run(2), maxHr = 0))
    }

    @Test
    fun `the current VO2max is the median of the last five estimates of the last eight weeks`() {
        // Each run is 10 km in 60 minutes; the heart rate sets the estimate. Older than 56 days is left out.
        val runs = listOf(
            run(70, avgHr = 170), // outside the window
            run(50, avgHr = 140),
            run(40, avgHr = 145),
            run(30, avgHr = 150),
            run(20, avgHr = 155),
            run(10, avgHr = 160),
            run(2, avgHr = 150),
        )
        val summary = Vo2Max.summary(runs, maxHr = 190, today = today)!!
        val all = runs.filter { it.id != runs[0].id }.mapNotNull { Vo2Max.estimateFromRun(it, 190) }
        assertEquals(6, summary.estimates.size)
        assertEquals(all.map { it.date }, summary.estimates.map { it.date })
        val lastFive = all.takeLast(5).map { it.vo2max }.sorted()
        assertEquals(lastFive[2], summary.current, 1e-9)
    }

    @Test
    fun `one estimate is not enough for a number`() {
        assertNull(Vo2Max.summary(listOf(run(2)), 190, today))
        assertNotNull(Vo2Max.summary(listOf(run(2), run(9, avgHr = 155)), 190, today))
        assertNull(Vo2Max.summary(emptyList(), 190, today))
    }
}
