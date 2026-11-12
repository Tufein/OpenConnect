package nl.tufein.openconnect.core.watch

import nl.tufein.openconnect.core.Sport
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Test vectors follow the Bluetooth SIG Heart Rate Service 1.0 (characteristic 0x2A37). */
class HeartRateTest {
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `a plain 8-bit value`() {
        val m = assertNotNull(HeartRateMeasurement.parse(bytes(0x00, 72)))
        assertEquals(72, m.bpm)
        assertEquals(SensorContact.UNSUPPORTED, m.contact)
        assertTrue(m.rrIntervalsMs.isEmpty())
        assertNull(m.energyKj)
    }

    @Test
    fun `a 16-bit value is little endian`() {
        assertEquals(300, assertNotNull(HeartRateMeasurement.parse(bytes(0x01, 0x2C, 0x01))).bpm)
        assertEquals(0x01FF, assertNotNull(HeartRateMeasurement.parse(bytes(0x01, 0xFF, 0x01))).bpm)
    }

    @Test
    fun `sensor contact status comes from bits one and two`() {
        assertEquals(SensorContact.UNSUPPORTED, assertNotNull(HeartRateMeasurement.parse(bytes(0x00, 60))).contact)
        assertEquals(SensorContact.UNSUPPORTED, assertNotNull(HeartRateMeasurement.parse(bytes(0x02, 60))).contact)
        assertEquals(SensorContact.NOT_DETECTED, assertNotNull(HeartRateMeasurement.parse(bytes(0x04, 60))).contact)
        assertEquals(SensorContact.DETECTED, assertNotNull(HeartRateMeasurement.parse(bytes(0x06, 60))).contact)
    }

    @Test
    fun `rr intervals are in 1024ths of a second and become milliseconds`() {
        // flags 0x10: rr present; 0x0400 = 1024 ticks = 1000 ms, 0x0420 = 1056 ticks = 1031.25 ms
        val m = assertNotNull(HeartRateMeasurement.parse(bytes(0x10, 60, 0x00, 0x04, 0x20, 0x04)))
        assertEquals(listOf(1000.0, 1031.25), m.rrIntervalsMs)
    }

    @Test
    fun `energy expended sits between the value and the intervals`() {
        // flags 0x18: energy and rr present; 10 kJ; 0x0300 = 768 ticks = 750 ms
        val m = assertNotNull(HeartRateMeasurement.parse(bytes(0x18, 80, 0x0A, 0x00, 0x00, 0x03)))
        assertEquals(80, m.bpm)
        assertEquals(10, m.energyKj)
        assertEquals(listOf(750.0), m.rrIntervalsMs)
    }

    @Test
    fun `all flags at once with a 16-bit value`() {
        // flags 0x1F: 16-bit, contact detected, energy, rr
        val m = assertNotNull(HeartRateMeasurement.parse(bytes(0x1F, 150, 0x00, 0x05, 0x00, 0x00, 0x02)))
        assertEquals(150, m.bpm)
        assertEquals(SensorContact.DETECTED, m.contact)
        assertEquals(5, m.energyKj)
        assertEquals(listOf(500.0), m.rrIntervalsMs)
    }

    @Test
    fun `short or empty packets are refused, a dangling byte is ignored`() {
        assertNull(HeartRateMeasurement.parse(ByteArray(0)))
        assertNull(HeartRateMeasurement.parse(bytes(0x00)))
        assertNull(HeartRateMeasurement.parse(bytes(0x01, 70))) // 16-bit value needs two bytes
        assertNull(HeartRateMeasurement.parse(bytes(0x08, 70, 0x01))) // energy needs two bytes
        assertEquals(emptyList(), assertNotNull(HeartRateMeasurement.parse(bytes(0x10, 70, 0x00))).rrIntervalsMs)
    }

    // ------------------------------------------------------------------------------ live session

    private val start = Instant.parse("2026-06-10T08:00:00Z")

    private fun live(vararg bpm: Int, rr: List<Double> = emptyList()): LiveHeartRate {
        val session = LiveHeartRate()
        bpm.forEachIndexed { i, v ->
            session.add(start.plusSeconds(i.toLong()), HeartRateMeasurement(v, SensorContact.DETECTED, if (i == 0) rr else emptyList(), null))
        }
        return session
    }

    @Test
    fun `a live session tracks current, lowest, highest and average`() {
        val session = live(60, 70, 80, 0, 90) // the 0 is the strap saying "no reading"
        assertEquals(90, session.current)
        assertEquals(60, session.min)
        assertEquals(90, session.max)
        assertEquals(75, session.avg)
        assertEquals(4, session.count)
    }

    @Test
    fun `an empty session has nothing`() {
        val session = LiveHeartRate()
        assertNull(session.current)
        assertNull(session.min)
        assertNull(session.avg)
        assertNull(session.rmssdMs())
        assertNull(session.toActivity("x", Sport.OTHER))
    }

    @Test
    fun `rmssd is the root mean square of successive differences`() {
        // differences 10, -20, 10: mean of squares 200, root 14.14
        assertEquals(14.14, assertNotNull(live(60, rr = listOf(1000.0, 1010.0, 990.0, 1000.0)).rmssdMs()), 0.01)
        assertNull(live(60, rr = listOf(1000.0)).rmssdMs()) // one interval has no difference
    }

    @Test
    fun `impossible intervals are left out of rmssd`() {
        // 200 ms and 3000 ms are artefacts (missed or doubled beats); only the pairs between sane values count
        val rmssd = assertNotNull(live(60, rr = listOf(1000.0, 1010.0, 200.0, 1000.0, 3000.0, 990.0)).rmssdMs())
        assertEquals(10.0, rmssd, 0.01) // 1000 -> 1010 only
    }

    @Test
    fun `a session can be saved as an activity`() {
        val session = live(100, 110, 120, 130)
        val activity = assertNotNull(session.toActivity("Live heart rate", Sport.CYCLING))
        assertEquals(Sport.CYCLING, activity.sport)
        assertEquals("Live heart rate", activity.name)
        assertEquals(start, activity.startTime)
        assertEquals(3.0, activity.durationS)
        assertEquals(115, activity.avgHr)
        assertEquals(130, activity.maxHr)
        assertEquals(listOf(100, 110, 120, 130), activity.track.map { it.hr })
        assertEquals("ble", activity.source)
        assertTrue(activity.id.startsWith("ble:"))
    }
}
