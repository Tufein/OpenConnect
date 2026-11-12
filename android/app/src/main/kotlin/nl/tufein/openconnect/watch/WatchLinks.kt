package nl.tufein.openconnect.watch

import nl.tufein.openconnect.core.watch.HeartRateMeasurement
import nl.tufein.openconnect.core.watch.WatchSource

/** A watch the phone can see on its USB port. [id] is whatever [WatchLink.open] needs to find it again. */
data class WatchInfo(val id: String, val name: String)

class WatchLinkException(val reason: Reason, message: String? = null) : Exception(message) {
    enum class Reason { NOT_FOUND, NO_PERMISSION, CANNOT_OPEN }
}

/** An open connection to a watch: read through [source], then [close] to let the USB device go. */
class OpenedWatch(val source: WatchSource, private val onClose: () -> Unit) : AutoCloseable {
    override fun close() = onClose()
}

/** The phone's USB side. The real one is [UsbWatchLink]; tests use a fake. */
interface WatchLink {
    fun attached(): List<WatchInfo>

    /** Asks for USB permission if needed, then connects. Throws [WatchLinkException]. */
    suspend fun open(info: WatchInfo): OpenedWatch
}

data class BleSensor(val address: String, val name: String)

interface HeartRateListener {
    fun onConnected()

    fun onMeasurement(measurement: HeartRateMeasurement)

    /** [error] is null when the connection was closed on purpose. */
    fun onDisconnected(error: String?)
}

/** The phone's Bluetooth side. The real one is [BleHeartRateLink]; tests use a fake. */
interface HeartRateLink {
    /** Reports each sensor that offers the Heart Rate service. Close the result to stop scanning. */
    fun scan(onFound: (BleSensor) -> Unit, onError: (String) -> Unit): AutoCloseable

    /** Connects and streams measurements to [listener]. Close the result to disconnect. */
    fun connect(sensor: BleSensor, listener: HeartRateListener): AutoCloseable
}
