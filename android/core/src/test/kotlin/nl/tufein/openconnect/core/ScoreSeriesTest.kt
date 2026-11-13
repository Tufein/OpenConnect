package nl.tufein.openconnect.core

import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScoreSeriesTest {
    private val end = LocalDate.parse("2026-06-30")
    private val repo = InMemoryRepository()
    private val stats = StatsService(repo) { Instant.parse("2026-06-30T12:00:00Z") }

    private fun seed(day: LocalDate, battery: Int?, stress: Int?, sleepScore: Int?, hrv: Int? = null, rhr: Int? = null) {
        repo.upsertDaily(DailySummary(date = day, bodyBatteryHigh = battery, avgStress = stress, hrvMs = hrv, restingHr = rhr))
        if (sleepScore != null) repo.upsertSleep(SleepSummary(date = day, durationS = 7 * 3600, score = sleepScore))
    }

    @Test
    fun `one readiness value per day, null where the day has too little to judge`() {
        seed(end.minusDays(2), battery = 80, stress = 20, sleepScore = 85)
        // nothing at all on the day before yesterday's neighbour
        seed(end, battery = 40, stress = 60, sleepScore = 55)
        val series = stats.readinessSeries(end.minusDays(3), end)
        assertEquals(4, series.size)
        assertEquals(end.minusDays(3), series.first().first)
        assertNull(series[0].second)
        assertNotNull(series[1].second)
        assertNull(series[2].second)
        assertNotNull(series[3].second)
    }

    @Test
    fun `a rested day scores higher than a drained one`() {
        seed(end.minusDays(1), battery = 90, stress = 15, sleepScore = 90)
        seed(end, battery = 30, stress = 70, sleepScore = 50)
        val s = stats.readinessSeries(end.minusDays(1), end).map { it.second!! }
        assertTrue(s[0] > s[1], s.toString())
    }

    @Test
    fun `the baseline of a day only uses earlier days`() {
        // five steady earlier days make an HRV baseline of 50; a drop to 25 must pull that day's score down
        for (i in 6 downTo 2) seed(end.minusDays(i.toLong()), battery = 80, stress = 20, sleepScore = 80, hrv = 50)
        seed(end.minusDays(1), battery = 80, stress = 20, sleepScore = 80, hrv = 50)
        seed(end, battery = 80, stress = 20, sleepScore = 80, hrv = 25)
        val series = stats.readinessSeries(end.minusDays(1), end).map { it.second!! }
        assertTrue(series[1] < series[0], series.toString())
    }

    @Test
    fun `sleep scores come from the night's own score, null when the watch gave none`() {
        seed(end.minusDays(1), battery = 70, stress = 30, sleepScore = 82)
        seed(end, battery = 70, stress = 30, sleepScore = null)
        val s = stats.sleepScoreSeries(end.minusDays(1), end)
        assertEquals(listOf(82.0, null), s.map { it.second })
    }
}
