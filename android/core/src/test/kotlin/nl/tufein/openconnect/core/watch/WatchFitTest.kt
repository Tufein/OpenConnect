package nl.tufein.openconnect.core.watch

import nl.tufein.openconnect.core.formats.FormatError
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The fixtures in resources/watch were written by Garmin's own FIT SDK (see
 * src/test/fixture-generator/WatchFixtures.java), so these tests read files our code did not produce.
 * The watch in the fixture is on UTC+2, so 2026-06-10T22:10Z is 00:10 on the 11th for it.
 */
class WatchFitTest {
    private val amsterdam = ZoneId.of("Europe/Amsterdam")
    private val d10 = LocalDate.parse("2026-06-10")
    private val d11 = LocalDate.parse("2026-06-11")

    private fun fixture(name: String): ByteArray =
        checkNotNull(WatchFitTest::class.java.getResourceAsStream("/watch/$name")) { "missing fixture $name" }.readBytes()

    private fun WatchFileData.series(metric: Metric, day: LocalDate? = null): List<Pair<String, Double>> =
        samples.filter { it.metric == metric && (day == null || it.day == day) }
            .sortedBy { it.time }
            .map { it.time.toString().removeSuffix(":00Z").removeSuffix("Z") to it.value }

    // ------------------------------------------------------------------ monitoring file

    @Test
    fun `monitoring file reads the utc offset the watch wrote`() {
        val data = WatchFit.parse(fixture("monitoring.fit"), fallbackZone = ZoneOffset.UTC)
        assertEquals(7200, data.utcOffsetSeconds)
    }

    @Test
    fun `steps are cumulative per activity type and start again after local midnight`() {
        val data = WatchFit.parse(fixture("monitoring.fit"), fallbackZone = ZoneOffset.UTC)
        assertEquals(
            listOf("2026-06-10T04:00" to 500.0, "2026-06-10T04:15" to 1500.0, "2026-06-10T10:00" to 6000.0, "2026-06-10T21:30" to 9000.0),
            data.series(Metric.STEPS_WALKING, d10),
        )
        assertEquals(listOf("2026-06-10T22:10" to 40.0), data.series(Metric.STEPS_WALKING, d11))
        assertEquals(
            listOf("2026-06-10T04:15:10" to 200.0, "2026-06-10T21:30:10" to 1200.0),
            data.series(Metric.STEPS_RUNNING, d10),
        )
    }

    @Test
    fun `distance is converted from centimetres and ascent from millimetres to metres`() {
        val data = WatchFit.parse(fixture("monitoring.fit"), fallbackZone = ZoneOffset.UTC)
        assertEquals(listOf(380.0, 1140.0, 4560.0, 6840.0), data.series(Metric.DISTANCE_WALKING, d10).map { it.second })
        assertEquals(listOf(240.0, 1320.0), data.series(Metric.DISTANCE_RUNNING, d10).map { it.second })
        assertEquals(listOf(21.0, 63.0, 250.0, 380.0), data.series(Metric.ACTIVE_KCAL_WALKING, d10).map { it.second })
        assertEquals(listOf(30.0, 110.0), data.series(Metric.ACTIVE_KCAL_RUNNING, d10).map { it.second })
        assertEquals(listOf(61.0), data.series(Metric.ASCENT, d10).map { it.second })
        assertEquals(listOf(10.0, 25.0), data.series(Metric.MODERATE_MIN, d10).map { it.second })
        assertEquals(listOf(5.0, 12.0), data.series(Metric.VIGOROUS_MIN, d10).map { it.second })
    }

    @Test
    fun `heart rate messages that only carry 16 bits of time get their full time back`() {
        val data = WatchFit.parse(fixture("monitoring.fit"), fallbackZone = ZoneOffset.UTC)
        val hr = data.samples.filter { it.metric == Metric.HEART_RATE }.sortedBy { it.time }
        assertEquals(
            listOf(
                "2026-06-10T04:00:30Z" to 62.0,
                "2026-06-10T04:05:00Z" to 58.0,
                "2026-06-10T10:00:30Z" to 90.0,
                "2026-06-10T10:05:00Z" to 110.0,
                "2026-06-10T21:31:00Z" to 70.0,
                "2026-06-10T22:10:30Z" to 55.0,
            ),
            hr.map { it.time.toString() to it.value },
        )
        assertEquals(d11, hr.last().day)
    }

    @Test
    fun `16-bit times that start over still land in the right order`() {
        // wrap.fit: a full timestamp 36 s before the low 16 bits roll over, then messages 20, 60 and 400 s later
        val full = 1_150_025_692L + 631_065_600L
        assertEquals(65_500L, 1_150_025_692L and 0xFFFF)
        val data = WatchFit.parse(fixture("wrap.fit"), fallbackZone = ZoneOffset.UTC)
        val hr = data.samples.filter { it.metric == Metric.HEART_RATE }.sortedBy { it.time }
        assertEquals(
            listOf(full + 20 to 60.0, full + 60 to 61.0, full + 400 to 62.0),
            hr.map { it.time.epochSecond to it.value },
        )
        assertEquals(listOf(100.0), data.samples.filter { it.metric == Metric.STEPS_WALKING }.map { it.value })
    }

    @Test
    fun `resting heart rate stress spo2 and respiration are read, unmeasured stress is dropped`() {
        val data = WatchFit.parse(fixture("monitoring.fit"), fallbackZone = ZoneOffset.UTC)
        assertEquals(listOf(54.0), data.series(Metric.RESTING_HR).map { it.second })
        assertEquals(listOf(20.0, 30.0, 60.0, 50.0), data.series(Metric.STRESS, d10).map { it.second }) // the -2 is gone
        assertEquals(listOf(10.0), data.series(Metric.STRESS, d11).map { it.second })
        assertEquals(listOf(96.0, 94.0, 98.0), data.series(Metric.SPO2).map { it.second })
        assertEquals(listOf(14.5, 15.5), data.series(Metric.RESPIRATION).map { it.second })
    }

    @Test
    fun `a file without an offset uses the fallback zone for the calendar day`() {
        // sleep.fit has no monitoring_info: 2026-06-10T23:00Z is already the 11th in Amsterdam, still the 10th in UTC
        val amsterdamData = WatchFit.parse(fixture("sleep.fit"), fallbackZone = amsterdam)
        val utcData = WatchFit.parse(fixture("sleep.fit"), fallbackZone = ZoneOffset.UTC)
        assertNull(amsterdamData.utcOffsetSeconds)
        assertEquals(d11, amsterdamData.samples.first { it.metric == Metric.HRV }.day)
        assertEquals(d10, utcData.samples.first { it.metric == Metric.HRV }.day)
    }

    // ------------------------------------------------------------------ sleep file

    @Test
    fun `sleep stages are added up from the level changes`() {
        val sleep = assertNotNull(WatchFit.parse(fixture("sleep.fit"), fallbackZone = amsterdam).sleep)
        assertEquals(d11, sleep.date) // the day the sleep ends on, in the watch's local time
        assertEquals(80 * 60, sleep.deepS)
        assertEquals(220 * 60, sleep.lightS)
        assertEquals(130 * 60, sleep.remS)
        assertEquals(15 * 60, sleep.awakeS)
        assertEquals(430 * 60, sleep.durationS) // time asleep, without the awake minutes
        assertEquals(82, sleep.score)
    }

    @Test
    fun `hrv values and the last night summary are read with their scale of 128`() {
        val data = WatchFit.parse(fixture("sleep.fit"), fallbackZone = amsterdam)
        assertEquals(listOf(45.0, 55.0, 60.0), data.series(Metric.HRV, d11).map { it.second })
        assertEquals(listOf(52.0), data.series(Metric.HRV_LAST_NIGHT, d11).map { it.second })
    }

    // ------------------------------------------------------------------ all-day health (HSA) file

    @Test
    fun `hsa arrays are spread over their processing interval and invalid readings are dropped`() {
        val data = WatchFit.parse(fixture("hsa.fit"), fallbackZone = amsterdam)
        assertEquals(
            listOf(
                "2026-06-10T04:00" to 90.0, "2026-06-10T04:03" to 88.0, "2026-06-10T04:06" to 85.0, "2026-06-10T04:09" to 80.0,
                "2026-06-10T10:00" to 70.0, "2026-06-10T10:03" to 65.0, "2026-06-10T10:06" to 60.0, // the -16 (no reading) is gone
            ),
            data.series(Metric.BODY_BATTERY, d10),
        )
        assertEquals(listOf(25.0, 30.0, 40.0), data.series(Metric.STRESS, d10).map { it.second })
        assertEquals(
            listOf("2026-06-10T04:00" to 61.0, "2026-06-10T04:01" to 59.0, "2026-06-10T04:03" to 64.0), // the 0 is gone
            data.series(Metric.HEART_RATE, d10),
        )
        assertNull(data.sleep)
    }

    // ------------------------------------------------------------------ robustness

    @Test
    fun `a corrupt file is refused with a readable error`() {
        val bytes = fixture("monitoring.fit")
        bytes[40] = (bytes[40].toInt() xor 0x55).toByte()
        val error = assertFailsWith<FormatError> { WatchFit.parse(bytes, fallbackZone = amsterdam) }
        assertTrue("CRC" in error.message.orEmpty())
        assertFailsWith<FormatError> { WatchFit.parse(ByteArray(5), fallbackZone = amsterdam) }
        assertFailsWith<FormatError> { WatchFit.parse(bytes.copyOf(100), fallbackZone = amsterdam) }
    }

    @Test
    fun `a file with no watch data gives an empty result instead of an error`() {
        val activity = checkNotNull(WatchFitTest::class.java.getResourceAsStream("/fixtures/python-run.fit")).readBytes()
        val data = WatchFit.parse(activity, fallbackZone = amsterdam)
        assertTrue(data.samples.isEmpty())
        assertNull(data.sleep)
    }
}
