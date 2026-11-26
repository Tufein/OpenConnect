package nl.tufein.openconnect.watch

import nl.tufein.openconnect.core.watch.HeartRateMeasurement
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The watch's own heart rate is a second live source next to a chest strap or Garmin's broadcast mode. */
class GarminHeartRateLinkTest {
    private val fenix = GarminBleWatch("AA:BB:CC:DD:EE:FF", "fenix 8", paired = true)
    private val watch = BleSensor(fenix.address, fenix.name)

    private class Listener : HeartRateListener {
        val measurements = CopyOnWriteArrayList<HeartRateMeasurement>()
        @Volatile var connected = false
        @Volatile var disconnected: String? = null

        override fun onConnected() {
            connected = true
        }

        override fun onMeasurement(measurement: HeartRateMeasurement) {
            measurements += measurement
        }

        override fun onDisconnected(error: String?) {
            disconnected = error
        }
    }

    private fun waitUntil(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) error("timed out waiting for $what")
            Thread.sleep(10)
        }
    }

    @Test
    fun `the scan offers the Garmin watch as a sensor`() {
        val found = CopyOnWriteArrayList<BleSensor>()
        val link = GarminHeartRateLink(FakeGarminBle(nearby = listOf(fenix)))
        link.scan(onFound = { found += it }, onError = { error("unexpected error: $it") })
        assertEquals(listOf(watch), found)
    }

    @Test
    fun `a connection turns the watch's beats into measurements and stops cleanly`() {
        val stopped = AtomicBoolean(false)
        val garmin = FakeGarminBle(nearby = listOf(fenix))
        garmin.heartRate = { _, onBpm, _ ->
            onBpm(72, 55)
            AutoCloseable { stopped.set(true) }
        }
        val listener = Listener()
        val connection = GarminHeartRateLink(garmin).connect(watch, listener)

        waitUntil("the connection") { listener.connected }
        waitUntil("the beat") { listener.measurements.isNotEmpty() }
        assertTrue(listener.connected, "the connection is announced")
        assertEquals(72, listener.measurements.single().bpm)
        assertEquals(emptyList(), listener.measurements.single().rrIntervalsMs, "the watch does not send beat intervals")
        assertEquals(listOf(fenix), garmin.heartRateOpened)

        connection.close()
        waitUntil("the connection to close") { stopped.get() }
        assertNull(listener.disconnected, "closing on purpose is not an error")
    }

    @Test
    fun `a Garmin address goes to the watch and another address to a standard sensor`() {
        val standard = FakeBle()
        val garmin = FakeGarminBle(nearby = listOf(fenix))
        garmin.heartRate = { _, _, _ -> AutoCloseable { } }
        val combined = CombinedHeartRateLink(standard, GarminHeartRateLink(garmin))

        val found = CopyOnWriteArrayList<BleSensor>()
        combined.scan(onFound = { found += it }, onError = { })
        assertEquals(listOf(watch), found)

        val watchListener = Listener()
        combined.connect(watch, watchListener)
        waitUntil("the Garmin stream") { garmin.heartRateOpened.isNotEmpty() }
        assertTrue(standard.connected == null, "the standard radio is not used for the watch")

        val strap = BleSensor("11:22:33:44:55:66", "H9")
        combined.connect(strap, Listener())
        assertEquals(strap, standard.connected)
        assertFalse(garmin.heartRateOpened.contains(GarminBleWatch(strap.address, strap.name, paired = true)))
    }
}
