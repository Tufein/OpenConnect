package nl.tufein.openconnect.core

import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DemoStatsTest {
    private val today = LocalDate.parse("2026-05-10") // a Sunday

    @Test
    fun demoDataIsDeterministicAndCoversEveryDay() {
        val a = DemoData.generate(today, days = 30)
        val b = DemoData.generate(today, days = 30)
        assertEquals(a, b)
        assertEquals(31, a.dailies.size)
        assertEquals(31, a.sleeps.size)
        assertTrue(a.activities.size in 18..26, "was ${a.activities.size}")
        assertNotEquals(a, DemoData.generate(today, days = 30, seed = 7))
    }

    private fun assertNotEquals(a: Any, b: Any) = assertTrue(a != b)

    @Test
    fun demoActivitiesAreInternallyConsistent() {
        val data = DemoData.generate(today, days = 14)
        for (a in data.activities) {
            assertTrue(a.id.startsWith("demo:"))
            assertEquals("demo", a.source)
            assertTrue(a.durationS > 0)
            assertTrue(a.track.size > 100)
            assertEquals(a.startTime, a.track.first().time)
            if (a.sport == Sport.STRENGTH) {
                assertTrue(!a.hasGps)
                assertEquals(0.0, a.distanceM)
            } else {
                assertTrue(a.hasGps)
                assertTrue(a.distanceM > 1000, "${a.name} ${a.distanceM}")
                assertEquals(a.track.last().distanceM, a.distanceM)
            }
            assertTrue(a.maxHr!! >= a.avgHr!!)
        }
    }

    @Test
    fun demoRunsAreAroundPlausiblePaces() {
        val runs = DemoData.generate(today, days = 60).activities.filter { it.sport == Sport.RUNNING }
        assertTrue(runs.isNotEmpty())
        for (run in runs) {
            val pace = run.paceSPerKm!!
            assertTrue(pace in 300.0..400.0, "pace $pace")
        }
    }

    @Test
    fun weekStartIsMonday() {
        assertEquals(LocalDate.parse("2026-05-04"), weekStart(today))
        assertEquals(LocalDate.parse("2026-05-04"), weekStart(LocalDate.parse("2026-05-04")))
    }

    @Test
    fun weeklySummaryTotalsAndAverages() {
        val repo = InMemoryRepository()
        val monday = LocalDate.parse("2026-05-04")
        repo.upsertActivity(TestData.activity("a:1", Sport.RUNNING, start = Instant.parse("2026-05-04T07:00:00Z"), distanceM = 5000.0, durationS = 1500.0))
        repo.upsertActivity(TestData.activity("a:2", Sport.CYCLING, start = Instant.parse("2026-05-06T07:00:00Z"), distanceM = 20_000.0, durationS = 3600.0))
        repo.upsertActivity(TestData.activity("a:3", Sport.RUNNING, start = Instant.parse("2026-05-11T00:00:00Z"))) // next week
        for (i in 0 until 7) {
            repo.upsertDaily(TestData.daily(monday.plusDays(i.toLong()), steps = 8000 + i * 1000))
            repo.upsertSleep(TestData.sleep(monday.plusDays(i.toLong()), hours = 7.0, score = 70 + i))
        }
        val week = StatsService(repo).weeklySummary(LocalDate.parse("2026-05-07"))
        assertEquals(monday, week.weekStart)
        assertEquals(LocalDate.parse("2026-05-10"), week.weekEnd)
        assertEquals(2, week.activityCount)
        assertEquals(25_000.0, week.totalDistanceM)
        assertEquals(5100.0, week.totalDurationS)
        assertEquals(840, week.totalCalories)
        assertEquals(1, week.bySport.getValue("running").count)
        assertEquals(20_000.0, week.bySport.getValue("cycling").distanceM)
        assertEquals(11_000.0, week.avgSteps)
        assertEquals(5, week.goalDays) // 10 000+ steps on Wed..Sun
        assertEquals(7, week.daysWithData)
        assertEquals(7.0, week.avgSleepHours)
        assertEquals(73.0, week.avgSleepScore)
        assertEquals(52.0, week.avgRestingHr)
        assertEquals(30 * 7, week.intensityMinutes)
    }

    @Test
    fun emptyWeekHasNullAverages() {
        val week = StatsService(InMemoryRepository()).weeklySummary(today)
        assertEquals(0, week.activityCount)
        assertNull(week.avgSteps)
        assertNull(week.avgSleepHours)
    }

    @Test
    fun trendHasOneRowPerDayWithGaps() {
        val repo = InMemoryRepository()
        val d1 = LocalDate.parse("2026-05-01")
        repo.upsertDaily(TestData.daily(d1, steps = 5000))
        repo.upsertActivity(TestData.activity("a:1", start = Instant.parse("2026-05-03T07:00:00Z"), distanceM = 4000.0))
        val rows = StatsService(repo).trendSeries(d1, d1.plusDays(3))
        assertEquals(4, rows.size)
        assertEquals(5000, rows[0].steps)
        assertNull(rows[1].steps)
        assertEquals(4000.0, rows[2].distanceM)
    }

    @Test
    fun overviewComparesLastSevenDaysWithTheWeekBefore() {
        val repo = InMemoryRepository()
        for (i in 0..13) repo.upsertDaily(TestData.daily(today.minusDays(i.toLong()), steps = if (i < 7) 10_000 else 8_000))
        val stats = StatsService(repo) { today.atStartOfDay().toInstant(java.time.ZoneOffset.UTC).plusSeconds(3600) }
        val overview = stats.overview()
        assertEquals(25.0, overview.stepsChangePct)
        assertEquals(today, overview.today!!.date)
        assertEquals(30, overview.trend.size)
        assertNotNull(overview.thisWeek)
    }

    @Test
    fun personalRecordsPerSport() {
        val repo = InMemoryRepository()
        repo.upsertActivity(TestData.activity("a:1", Sport.RUNNING, start = Instant.parse("2026-05-01T07:00:00Z"), distanceM = 5000.0, durationS = 1500.0))
        repo.upsertActivity(TestData.activity("a:2", Sport.RUNNING, start = Instant.parse("2026-05-02T07:00:00Z"), distanceM = 10_000.0, durationS = 2800.0))
        repo.upsertActivity(TestData.activity("a:3", Sport.CYCLING, start = Instant.parse("2026-05-03T07:00:00Z"), distanceM = 30_000.0, durationS = 4000.0))
        val records = personalRecords(repo)
        val running = records.getValue(Sport.RUNNING)
        assertEquals("a:2", running.getValue("longest_distance").activityId)
        assertEquals("a:2", running.getValue("fastest_pace").activityId) // 280 s/km beats 300
        assertTrue("fastest_pace" !in records.getValue(Sport.CYCLING))
        assertEquals(30_000.0, records.getValue(Sport.CYCLING).getValue("longest_distance").value)
    }
}
