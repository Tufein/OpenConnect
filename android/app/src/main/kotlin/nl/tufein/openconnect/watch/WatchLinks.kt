package nl.tufein.openconnect.watch

import nl.tufein.openconnect.core.watch.HeartRateMeasurement
import nl.tufein.openconnect.core.watch.WatchSource

/** A watch the phone can see on its USB port. [id] is whatever [WatchLink.open] needs to find it again. */
data class WatchInfo(val id: String, val name: String)

class WatchLinkException(val reason: Reason, message: String? = null) : Exception(message) {
    enum class Reason { NOT_FOUND, NO_PERMISSION, CANNOT_OPEN, NOT_PAIRED }
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

/**
 * A folder the user picked, read as if it were a watch: a copy of the watch's `GARMIN` folder, a folder another app
 * exports watch files to, or the watch itself when Android's file picker shows it as a storage device.
 * [treeUri] is what the picker returned. The real one is [SafFolderLink]; tests use a fake.
 */
fun interface FolderLink {
    /** Throws [WatchLinkException]. */
    suspend fun open(treeUri: String): OpenedWatch
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

/** A Garmin watch the phone can reach over Bluetooth. [paired] is Android's pairing ("bond"), not Garmin Connect's. */
data class GarminBleWatch(val address: String, val name: String, val paired: Boolean)

/** Where a Bluetooth sync with a Garmin watch is, before the files start coming. */
enum class GarminStep { PAIRING, CONNECTING, HANDSHAKE }

/**
 * The phone's Bluetooth side for syncing a Garmin watch directly, with Garmin's own protocol (the one Gadgetbridge
 * worked out). The real one is [AndroidGarminBleLink]; tests use a fake.
 */
interface GarminBleLink {
    /** Reports paired Garmin watches, then Garmin watches nearby. Close the result to stop looking. */
    fun find(onFound: (GarminBleWatch) -> Unit, onError: (String) -> Unit): AutoCloseable

    /** Pairs when needed, connects and finishes the handshake. Throws [WatchLinkException]. */
    suspend fun open(watch: GarminBleWatch, onStep: (GarminStep) -> Unit = {}): OpenedWatch
}

/**
 * The two switches of the Bluetooth sync, both off until the person turns them on: tell the watch its files were
 * synced (like Garmin Connect does), and look for the watch in the background about every three hours.
 */
interface GarminBleOptions {
    var archiveAfterRead: Boolean
    var background: Boolean
}

class InMemoryGarminBleOptions(override var archiveAfterRead: Boolean = false, override var background: Boolean = false) : GarminBleOptions

/** Remembers the last watch that synced over Bluetooth, for a one-tap sync next time. */
interface GarminWatchMemory {
    fun load(): GarminBleWatch?

    fun save(watch: GarminBleWatch?)
}

class InMemoryGarminWatchMemory(private var watch: GarminBleWatch? = null) : GarminWatchMemory {
    override fun load() = watch

    override fun save(watch: GarminBleWatch?) {
        this.watch = watch
    }
}

object NoGarminBleLink : GarminBleLink {
    override fun find(onFound: (GarminBleWatch) -> Unit, onError: (String) -> Unit): AutoCloseable {
        onError("Bluetooth is not available")
        return AutoCloseable { }
    }

    override suspend fun open(watch: GarminBleWatch, onStep: (GarminStep) -> Unit): OpenedWatch =
        throw WatchLinkException(WatchLinkException.Reason.CANNOT_OPEN, "Bluetooth is not available")
}
