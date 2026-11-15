package nl.tufein.openconnect.core

import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RaceShapeTest {
    private val today = LocalDate.parse("2026-06-30")

    private fun run(daysAgo: Long, km: Double, sport: Sport = Sport.RUNNING, id: String = "r-$daysAgo-$km") = Activity(
        id = id,
        sport = sport,
        startTime = today.minusDays(daysAgo).atTime(8, 0).toInstant(ZoneOffset.UTC),
        durationS = km * 330,
        distanceM = km * 1000,
    )

    @Test
    fun `no runs in the last six weeks means no shape`() {
        assertNull(RaceShapeCalculator.compute(emptyList(), RaceDistance.HALF, today))
        assertNull(RaceShapeCalculator.compute(listOf(run(43, 20.0)), RaceDistance.HALF, today)) // 43 days ago is outside
        assertNull(RaceShapeCalculator.compute(listOf(run(3, 20.0, Sport.CYCLING)), RaceDistance.HALF, today))
    }

    @Test
    fun `the window is the last 42 days including today`() {
        assertEquals(RaceShapeCalculator.WINDOW_DAYS, 42)
        assertEquals(10.0, RaceShapeCalculator.compute(listOf(run(41, 10.0)), RaceDistance.K10, today)!!.longestRunKm, 1e-9)
        assertEquals(10.0, RaceShapeCalculator.compute(listOf(run(0, 10.0)), RaceDistance.K10, today)!!.longestRunKm, 1e-9)
    }

    @Test
    fun `weekly volume and the longest run are compared with what the distance asks`() {
        // 120 km in six weeks is 20 km a week; the longest run is 15 km. A marathon asks 55 and 30.
        val runs = listOf(run(30, 15.0), run(20, 35.0), run(10, 35.0), run(2, 35.0))
        val shape = RaceShapeCalculator.compute(runs, RaceDistance.MARATHON, today)!!
        assertEquals(20.0, shape.weeklyKm, 1e-9)
        assertEquals(35.0, shape.longestRunKm, 1e-9)
        assertEquals(20.0 / 55.0, shape.volumeShare, 1e-9)
        assertEquals(1.0, shape.longRunShare, 1e-9) // 35 km is more than 30: capped at full
        assertEquals((20.0 / 55.0 + 1.0) / 2, shape.shape, 1e-9)
        assertEquals(ShapeVerdict.BUILDING, shape.verdict)
    }

    @Test
    fun `the same training is a different shape for a different distance`() {
        // Nine runs of 11 km in six weeks: 16.5 km a week, longest 11 km.
        val runs = (0 until 9).map { run(it * 4L + 1, 11.0) }
        val k5 = RaceShapeCalculator.compute(runs, RaceDistance.K5, today)!!
        val marathon = RaceShapeCalculator.compute(runs, RaceDistance.MARATHON, today)!!
        assertEquals(ShapeVerdict.READY, k5.verdict)
        assertEquals(ShapeVerdict.FAR, marathon.verdict)
        assertEquals((16.5 / 20.0 + 1.0) / 2, k5.shape, 1e-9)
    }

    @Test
    fun `verdicts follow the shape`() {
        fun verdict(shape: Double) = RaceShape(RaceDistance.HALF, 0.0, 0.0, 40.0, 18.0, shape, shape).verdict
        assertEquals(ShapeVerdict.READY, verdict(0.9))
        assertEquals(ShapeVerdict.ALMOST, verdict(0.89))
        assertEquals(ShapeVerdict.ALMOST, verdict(0.7))
        assertEquals(ShapeVerdict.BUILDING, verdict(0.69))
        assertEquals(ShapeVerdict.BUILDING, verdict(0.4))
        assertEquals(ShapeVerdict.FAR, verdict(0.39))
    }
}
