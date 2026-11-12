package nl.tufein.openconnect.core.watch

import nl.tufein.openconnect.core.DailySummary
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DailyAggregatorTest {
    private val amsterdam = ZoneId.of("Europe/Amsterdam")
    private val d10 = LocalDate.parse("2026-06-10")
    private val d11 = LocalDate.parse("2026-06-11")

    private fun fixture(name: String): ByteArray =
        checkNotNull(DailyAggregatorTest::class.java.getResourceAsStream("/watch/$name")) { "missing fixture $name" }.readBytes()

    private val samples: List<MetricSample> =
        listOf("monitoring.fit", "hsa.fit", "sleep.fit").flatMap { WatchFit.parse(fixture(it), amsterdam).samples }

    @Test
    fun `a day is summed up from counters and readings of all files`() {
        val day = DailyAggregator.summarize(d10, samples)
        assertEquals(10_200, day.steps) // walking 9000 + running 1200: the highest counter of each type
        assertEquals(8_160.0, day.distanceM)
        assertEquals(490, day.activeKcal)
        assertEquals(20, day.floorsClimbed) // 61 m of ascent at about 3 m a floor
        assertEquals(25, day.moderateIntensityMin)
        assertEquals(12, day.vigorousIntensityMin)
        assertEquals(54, day.restingHr)
        assertEquals(58, day.minHr)
        assertEquals(110, day.maxHr)
        assertEquals(36, day.avgStress) // (20+30+60+50 from Monitor, 25+30+40 from HSA) / 7
        assertEquals(60, day.maxStress)
        assertEquals(90, day.bodyBatteryHigh)
        assertEquals(60, day.bodyBatteryLow)
        assertEquals(96, day.spo2Avg)
        assertEquals(94, day.spo2Min)
        assertEquals(15.0, day.respirationAvg)
        assertNull(day.hrvMs, "the night's HRV belongs to the morning it ends on")
    }

    @Test
    fun `the counters start again after midnight and the night's hrv lands on the morning`() {
        val day = DailyAggregator.summarize(d11, samples)
        assertEquals(40, day.steps)
        assertEquals(30.0, day.distanceM)
        assertEquals(55, day.minHr)
        assertEquals(10, day.avgStress)
        assertEquals(52, day.hrvMs) // the watch's own last-night average wins over the mean of the readings
    }

    @Test
    fun `without the watch summary the hrv readings are averaged`() {
        val readings = samples.filter { it.metric == Metric.HRV }
        assertEquals(53, DailyAggregator.summarize(d11, readings).hrvMs) // (45 + 55 + 60) / 3 = 53.3
    }

    @Test
    fun `a day without readings keeps what was already known`() {
        val known = DailySummary(date = d10, steps = 7_777, stepsGoal = 10_000, restingHr = 51, bodyBatteryHigh = 80)
        val onlyHsa = WatchFit.parse(fixture("hsa.fit"), amsterdam).samples
        val day = DailyAggregator.summarize(d10, onlyHsa, existing = known)
        assertEquals(7_777, day.steps)
        assertEquals(10_000, day.stepsGoal)
        assertEquals(51, day.restingHr)
        assertEquals(90, day.bodyBatteryHigh) // but a new reading replaces the old value
        assertEquals(60, day.bodyBatteryLow)
    }

    @Test
    fun `an empty day is an empty summary`() {
        assertEquals(DailySummary(date = d10), DailyAggregator.summarize(d10, emptyList()))
    }
}
