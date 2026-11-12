package nl.tufein.openconnect.watch

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.mtp.MtpConstants
import android.mtp.MtpDevice
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import nl.tufein.openconnect.core.watch.MtpEntry
import nl.tufein.openconnect.core.watch.MtpTransport
import nl.tufein.openconnect.core.watch.MtpWatchSource
import java.io.IOException
import kotlin.coroutines.resume

/**
 * A Garmin watch on the phone's USB port, read over MTP with the platform's own `android.mtp.MtpDevice`.
 * Needs a phone that can act as a USB host (most can, with a USB-C cable or an OTG adapter).
 *
 * This is the part that cannot be tried without a phone and a watch; everything it feeds (the folder
 * walk in [MtpWatchSource], the FIT readers, the sync) is covered by tests.
 */
class UsbWatchLink(private val context: Context) : WatchLink {
    private val usb = context.getSystemService(Context.USB_SERVICE) as UsbManager

    companion object {
        /** Garmin's USB vendor id, 0x091E. */
        const val GARMIN_VENDOR_ID = 0x091E
    }

    override fun attached(): List<WatchInfo> =
        usb.deviceList.values
            .filter { it.vendorId == GARMIN_VENDOR_ID }
            .sortedBy { it.deviceName }
            .map { WatchInfo(it.deviceName, label(it)) }

    private fun label(device: UsbDevice): String =
        (if (usb.hasPermission(device)) device.productName else null)?.takeIf { it.isNotBlank() } ?: "Garmin"

    override suspend fun open(info: WatchInfo): OpenedWatch {
        val device = usb.deviceList[info.id] ?: throw WatchLinkException(WatchLinkException.Reason.NOT_FOUND)
        if (!usb.hasPermission(device) && !requestPermission(device)) {
            throw WatchLinkException(WatchLinkException.Reason.NO_PERMISSION)
        }
        val connection = usb.openDevice(device) ?: throw WatchLinkException(WatchLinkException.Reason.CANNOT_OPEN)
        val mtp = MtpDevice(device)
        if (!mtp.open(connection)) {
            connection.close()
            throw WatchLinkException(WatchLinkException.Reason.CANNOT_OPEN, "The watch did not answer as an MTP device")
        }
        return OpenedWatch(MtpWatchSource(AndroidMtpTransport(mtp), label(device))) { mtp.close() }
    }

    private suspend fun requestPermission(device: UsbDevice): Boolean = suspendCancellableCoroutine { continuation ->
        val action = "${context.packageName}.USB_PERMISSION"
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                runCatching { context.unregisterReceiver(this) }
                if (continuation.isActive) continuation.resume(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
        // The system fills in the result, so this has to be mutable from Android 12 on.
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val pending = PendingIntent.getBroadcast(context, 0, Intent(action).setPackage(context.packageName), flags)
        continuation.invokeOnCancellation { runCatching { context.unregisterReceiver(receiver) } }
        usb.requestPermission(device, pending)
    }
}

/** [MtpTransport] on top of the platform's MTP client. */
private class AndroidMtpTransport(private val mtp: MtpDevice) : MtpTransport {
    override fun storageIds(): List<Int> = mtp.storageIds?.toList().orEmpty()

    override fun children(storageId: Int, parent: Long?): List<MtpEntry> {
        val handles = mtp.getObjectHandles(storageId, 0, parent?.toInt() ?: ROOT_HANDLE) ?: return emptyList()
        return handles.toList().mapNotNull { handle ->
            val info = mtp.getObjectInfo(handle) ?: return@mapNotNull null
            MtpEntry(
                handle = handle.toLong(),
                name = info.name.orEmpty(),
                isFolder = info.format == MtpConstants.FORMAT_ASSOCIATION,
                size = info.compressedSizeLong,
            )
        }
    }

    override fun read(handle: Long, size: Long): ByteArray {
        if (size > MAX_FILE_BYTES) throw IOException("File on the watch is larger than ${MAX_FILE_BYTES / (1024 * 1024)} MB")
        return mtp.getObject(handle.toInt(), size.toInt()) ?: throw IOException("The watch did not return the file")
    }

    private companion object {
        /** `MtpDevice.getObjectHandles` takes -1 for "the objects at the root of the storage". */
        const val ROOT_HANDLE = -1
        const val MAX_FILE_BYTES = 64L * 1024 * 1024
    }
}
