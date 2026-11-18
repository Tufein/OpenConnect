package nl.tufein.openconnect.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeviceComparisonTest {
    private val day = LocalDate.parse("2026-11-17")

    private fun series(source: InsightSource, vararg values: Pair<ChartMetric, Double>, on: LocalDate = day) =
        DaySeries(source, listOf(DayRecord(on, values.toMap())))

    private fun only(result: List<ComparedMeasure>, metric: ChartMetric) = result.single { it.metric == metric }

    @Test
    fun `only measures that at least two devices have are compared, in a fixed order, with the devices in the order given`() {
        val watch = series(InsightSource.GARMIN, ChartMetric.STEPS to 9_000.0, ChartMetric.SLEEP to 7.5, ChartMetric.RESTING_HR to 52.0, ChartMetric.BODY_BATTERY to 80.0)
        val ring = series(InsightSource.OURA, ChartMetric.SLEEP to 7.2, ChartMetric.STEPS to 9_300.0)
        val result = DeviceComparison.of(day, listOf(watch, ring))
        assertEquals(listOf(ChartMetric.SLEEP, ChartMetric.STEPS), result.map { it.metric }, "resting heart rate has one device only")
        assertEquals(listOf(InsightSource.GARMIN, InsightSource.OURA), only(result, ChartMetric.SLEEP).values.map { it.source })
        assertEquals(listOf(7.5, 7.2), only(result, ChartMetric.SLEEP).values.map { it.value })
    }

    @Test
    fun `sleep within half an hour is close, further apart is not`() {
        fun gap(hours: Double) = only(DeviceComparison.of(day, listOf(series(InsightSource.GARMIN, ChartMetric.SLEEP to 7.0), series(InsightSource.OURA, ChartMetric.SLEEP to 7.0 + hours))), ChartMetric.SLEEP)
        assertEquals(Agreement.CLOSE, gap(0.25).agreement)
        assertEquals(Agreement.CLOSE, gap(0.5).agreement, "exactly half an hour still counts as close")
        assertEquals(Agreement.APART, gap(0.75).agreement)
        assertEquals(0.75, gap(0.75).spread, 1e-9)
    }

    @Test
    fun `steps are close within a tenth of the larger count`() {
        fun gap(other: Double) = only(DeviceComparison.of(day, listOf(series(InsightSource.GARMIN, ChartMetric.STEPS to 9_000.0), series(InsightSource.HUME, ChartMetric.STEPS to other))), ChartMetric.STEPS)
        assertEquals(Agreement.CLOSE, gap(9_800.0).agreement, "800 apart of 9,800 is under a tenth")
        assertEquals(Agreement.APART, gap(11_000.0).agreement, "2,000 apart of 11,000 is nearly a fifth")
        assertEquals(Agreement.CLOSE, gap(9_000.0).agreement)
    }

    @Test
    fun `resting heart rate is close within 3 bpm and blood oxygen within 2 points`() {
        val hr = DeviceComparison.of(day, listOf(series(InsightSource.GARMIN, ChartMetric.RESTING_HR to 52.0), series(InsightSource.WHOOP, ChartMetric.RESTING_HR to 55.0)))
        assertEquals(Agreement.CLOSE, only(hr, ChartMetric.RESTING_HR).agreement)
        val far = DeviceComparison.of(day, listOf(series(InsightSource.GARMIN, ChartMetric.RESTING_HR to 52.0), series(InsightSource.WHOOP, ChartMetric.RESTING_HR to 56.0)))
        assertEquals(Agreement.APART, only(far, ChartMetric.RESTING_HR).agreement)
        val o2 = DeviceComparison.of(day, listOf(series(InsightSource.OURA, ChartMetric.SPO2 to 96.0), series(InsightSource.HUME, ChartMetric.SPO2 to 98.0)))
        assertEquals(Agreement.CLOSE, only(o2, ChartMetric.SPO2).agreement)
        val o2far = DeviceComparison.of(day, listOf(series(InsightSource.OURA, ChartMetric.SPO2 to 96.0), series(InsightSource.HUME, ChartMetric.SPO2 to 99.0)))
        assertEquals(Agreement.APART, only(o2far, ChartMetric.SPO2).agreement)
    }

    @Test
    fun `with three devices the spread is the largest gap and one outlier makes the measure apart`() {
        val sets = listOf(
            series(InsightSource.GARMIN, ChartMetric.SLEEP to 7.4),
            series(InsightSource.OURA, ChartMetric.SLEEP to 7.5),
            series(InsightSource.WHOOP, ChartMetric.SLEEP to 6.5),
        )
        val sleep = only(DeviceComparison.of(day, sets), ChartMetric.SLEEP)
        assertEquals(1.0, sleep.spread, 1e-9)
        assertEquals(Agreement.APART, sleep.agreement)
        assertEquals(3, sleep.values.size)
    }

    @Test
    fun `measures that devices work out differently are never compared, even when two devices report them`() {
        val a = series(InsightSource.GARMIN, ChartMetric.HRV to 55.0, ChartMetric.READINESS to 70.0, ChartMetric.SLEEP_SCORE to 80.0, ChartMetric.LOWEST_HR to 48.0, ChartMetric.RESPIRATION to 14.0)
        val b = series(InsightSource.WHOOP, ChartMetric.HRV to 80.0, ChartMetric.READINESS to 40.0, ChartMetric.SLEEP_SCORE to 90.0, ChartMetric.LOWEST_HR to 45.0, ChartMetric.RESPIRATION to 15.0)
        assertTrue(DeviceComparison.of(day, listOf(a, b)).isEmpty(), "HRV, scores and breathing are each device's own way of counting")
    }

    @Test
    fun `one device, an empty day or readings that are zero or missing give nothing to compare`() {
        val watch = series(InsightSource.GARMIN, ChartMetric.STEPS to 9_000.0)
        assertTrue(DeviceComparison.of(day, listOf(watch)).isEmpty())
        assertTrue(DeviceComparison.of(day, emptyList()).isEmpty())
        val ring = series(InsightSource.OURA, ChartMetric.STEPS to 0.0)
        assertTrue(DeviceComparison.of(day, listOf(watch, ring)).isEmpty(), "a zero is a device that was not worn, not a measurement")
        val other = series(InsightSource.OURA, ChartMetric.STEPS to 9_100.0, on = day.minusDays(1))
        assertTrue(DeviceComparison.of(day, listOf(watch, other)).isEmpty(), "the ring has nothing on this day")
        assertTrue(DeviceComparison.of(day, listOf(watch, series(InsightSource.OURA, ChartMetric.STEPS to Double.NaN))).isEmpty())
    }
}
