/*
 * OpenConnect, SPDX-License-Identifier: AGPL-3.0-or-later
 */
package nl.tufein.openconnect.watch

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import nl.tufein.openconnect.core.watch.HeartRateMeasurement
import nl.tufein.openconnect.core.watch.SensorContact
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Reads a Garmin watch's own heart rate over its Bluetooth connection, without the watch's "broadcast heart rate"
 * mode. Garmin's real-time service gives beats per minute and sometimes a resting rate; it has no beat-to-beat
 * intervals, so the HRV field of the recording stays empty.
 */
class GarminHeartRateLink(
    private val garmin: GarminBleLink,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : HeartRateLink {
    override fun scan(onFound: (BleSensor) -> Unit, onError: (String) -> Unit): AutoCloseable =
        garmin.find(
            onFound = { watch -> onFound(BleSensor(watch.address, watch.name)) },
            onError = onError,
        )

    override fun connect(sensor: BleSensor, listener: HeartRateListener): AutoCloseable {
        val closed = AtomicBoolean(false)
        val connection = AtomicReference<AutoCloseable?>(null)
        val job = scope.launch {
            try {
                val opened = garmin.openHeartRate(
                    watch = GarminBleWatch(sensor.address, sensor.name, paired = true),
                    onBpm = { bpm, _ ->
                        if (!closed.get()) {
                            listener.onMeasurement(HeartRateMeasurement(bpm, SensorContact.UNSUPPORTED, emptyList(), null))
                        }
                    },
                    onClosed = { error -> if (!closed.get()) listener.onDisconnected(error) },
                )
                connection.set(opened)
                if (closed.get()) opened.close() else listener.onConnected()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (!closed.get()) listener.onDisconnected(e.message ?: "The watch did not start its heart rate")
            }
        }
        return AutoCloseable {
            closed.set(true)
            job.cancel()
            connection.getAndSet(null)?.close()
        }
    }
}

/**
 * Finds both standard heart-rate sensors (a chest strap, or a Garmin in broadcast mode) and Garmin watches that can
 * give their own heart rate. A watch is recognised by the address the Garmin scan reported, so a sync and a later
 * reconnect still take the Garmin path.
 */
class CombinedHeartRateLink(
    private val standard: HeartRateLink,
    private val garmin: HeartRateLink,
) : HeartRateLink {
    private val garminAddresses = ConcurrentHashMap.newKeySet<String>()

    override fun scan(onFound: (BleSensor) -> Unit, onError: (String) -> Unit): AutoCloseable {
        val standardScan = standard.scan(onFound, onError)
        val garminScan = garmin.scan(
            onFound = { sensor ->
                garminAddresses.add(sensor.address)
                onFound(sensor)
            },
            onError = onError,
        )
        return AutoCloseable {
            standardScan.close()
            garminScan.close()
        }
    }

    override fun connect(sensor: BleSensor, listener: HeartRateListener): AutoCloseable =
        if (garminAddresses.contains(sensor.address)) garmin.connect(sensor, listener) else standard.connect(sensor, listener)
}
