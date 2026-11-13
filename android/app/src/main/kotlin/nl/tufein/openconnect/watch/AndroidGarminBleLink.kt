// OpenConnect, SPDX-License-Identifier: AGPL-3.0-or-later
package nl.tufein.openconnect.watch

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.tufein.openconnect.core.watch.ble.ExecutorScheduler
import nl.tufein.openconnect.core.watch.ble.GarminBleException
import nl.tufein.openconnect.core.watch.ble.GarminBleSession
import nl.tufein.openconnect.core.watch.ble.GarminBleWatchSource
import nl.tufein.openconnect.core.watch.ble.MultiLink
import nl.tufein.openconnect.core.watch.ble.PhoneIdentity
import nl.tufein.openconnect.core.watch.ble.asFileAccess
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Syncs a Garmin watch over Bluetooth Low Energy with Garmin's own protocol: Android pairs ("bonds") with the watch,
 * we open a GATT connection, raise the MTU, find the multi-link characteristics, switch on notifications and hand the
 * rest to [GarminBleSession], which has no Android types and is tested against a pretend watch.
 *
 * Like the other links this needs a real phone and watch to exercise.
 */
@SuppressLint("MissingPermission")
class AndroidGarminBleLink(private val context: Context) : GarminBleLink {
    companion object {
        /** Names Garmin gives its watches and bike computers ("fenix 8 - 47mm", "Forerunner 965", ...). */
        val GARMIN_NAME = Regex(
            "^(fenix|forerunner|venu|v[ií]voactive|v[ií]vomove|v[ií]vosmart|v[ií]vosport|instinct|epix|enduro|descent|" +
                "edge|approach|lily|marq|quatix|tactix|d2|bounce|swim|bike|index|hrm|cirqa|garmin)",
            RegexOption.IGNORE_CASE,
        )
        private val CLIENT_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val PAIR_TIMEOUT_S = 90L
        private const val CONNECT_TIMEOUT_S = 30L
        private const val HANDSHAKE_TIMEOUT_MS = 30_000L
    }

    private val adapter: BluetoothAdapter? get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private fun nameOf(device: BluetoothDevice, fallback: String? = null): String? =
        runCatching { device.name }.getOrNull() ?: fallback

    override fun find(onFound: (GarminBleWatch) -> Unit, onError: (String) -> Unit): AutoCloseable {
        val adapter = adapter?.takeIf { it.isEnabled }
        if (adapter == null) {
            onError("Bluetooth is off or not available")
            return AutoCloseable { }
        }
        val reported = HashSet<String>()
        try {
            for (device in adapter.bondedDevices.orEmpty()) {
                val name = nameOf(device) ?: continue
                if (GARMIN_NAME.containsMatchIn(name) && reported.add(device.address)) onFound(GarminBleWatch(device.address, name, paired = true))
            }
        } catch (e: SecurityException) {
            onError("Bluetooth permission is missing")
            return AutoCloseable { }
        }
        val scanner = adapter.bluetoothLeScanner ?: return AutoCloseable { }
        val main = Handler(Looper.getMainLooper())
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = nameOf(result.device, result.scanRecord?.deviceName) ?: return
                if (!GARMIN_NAME.containsMatchIn(name)) return
                val paired = runCatching { result.device.bondState == BluetoothDevice.BOND_BONDED }.getOrDefault(false)
                main.post { if (reported.add(result.device.address)) onFound(GarminBleWatch(result.device.address, name, paired)) }
            }

            override fun onScanFailed(errorCode: Int) = onError("Bluetooth scan failed (code $errorCode)")
        }
        try {
            scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback)
        } catch (e: SecurityException) {
            onError("Bluetooth permission is missing")
            return AutoCloseable { }
        }
        return AutoCloseable { runCatching { scanner.stopScan(callback) } }
    }

    override suspend fun open(watch: GarminBleWatch, onStep: (GarminStep) -> Unit): OpenedWatch = withContext(Dispatchers.IO) {
        val adapter = adapter?.takeIf { it.isEnabled }
            ?: throw WatchLinkException(WatchLinkException.Reason.CANNOT_OPEN, "Bluetooth is off")
        val device = try {
            adapter.getRemoteDevice(watch.address)
        } catch (e: IllegalArgumentException) {
            throw WatchLinkException(WatchLinkException.Reason.NOT_FOUND, "Unknown address")
        }
        val firstConnect = try {
            if (device.bondState != BluetoothDevice.BOND_BONDED) {
                onStep(GarminStep.PAIRING)
                pair(device)
                true
            } else {
                false
            }
        } catch (e: SecurityException) {
            throw WatchLinkException(WatchLinkException.Reason.NO_PERMISSION, "Bluetooth permission is missing")
        }

        onStep(GarminStep.CONNECTING)
        val scheduler = ExecutorScheduler()
        val connection = GattConnection(device)
        val session = GarminBleSession(
            pipe = connection::write,
            phone = PhoneIdentity(
                name = runCatching { adapter.name }.getOrNull()?.takeIf { it.isNotBlank() } ?: "OpenConnect",
                manufacturer = Build.MANUFACTURER ?: "Android",
                model = Build.DEVICE ?: "phone",
            ),
            scheduler = scheduler,
            firstConnect = firstConnect,
        )
        connection.onNotification = session::onNotification
        connection.onLost = { session.onDisconnected() }
        try {
            val mtu = connection.connect()
            session.maxWrite = MultiLink.maxWrite(mtu)
            onStep(GarminStep.HANDSHAKE)
            session.start()
            val info = session.awaitReady(HANDSHAKE_TIMEOUT_MS)
            val name = info.deviceName.ifBlank { watch.name }
            OpenedWatch(GarminBleWatchSource(session.asFileAccess(), name)) {
                runCatching { session.finishSync() }
                Thread.sleep(300) // let "sync complete" leave before the link goes
                session.close()
                connection.close()
                scheduler.shutdown()
            }
        } catch (e: Exception) {
            session.close()
            connection.close()
            scheduler.shutdown()
            throw when (e) {
                is WatchLinkException -> e
                is SecurityException -> WatchLinkException(WatchLinkException.Reason.NO_PERMISSION, "Bluetooth permission is missing")
                is GarminBleException -> WatchLinkException(WatchLinkException.Reason.CANNOT_OPEN, e.message)
                else -> WatchLinkException(WatchLinkException.Reason.CANNOT_OPEN, e.message)
            }
        }
    }

    /** Asks Android to pair; the watch (and maybe the phone) show a code to confirm. */
    private fun pair(device: BluetoothDevice) {
        val done = CompletableFuture<Boolean>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val changed: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                if (changed?.address != device.address) return
                when (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)) {
                    BluetoothDevice.BOND_BONDED -> done.complete(true)
                    BluetoothDevice.BOND_NONE -> done.complete(false)
                }
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED), ContextCompat.RECEIVER_EXPORTED)
        try {
            if (!device.createBond() && device.bondState != BluetoothDevice.BOND_BONDING) {
                throw WatchLinkException(WatchLinkException.Reason.NOT_PAIRED, "Android did not start pairing")
            }
            val ok = try {
                done.get(PAIR_TIMEOUT_S, TimeUnit.SECONDS)
            } catch (e: TimeoutException) {
                false
            }
            if (!ok) throw WatchLinkException(WatchLinkException.Reason.NOT_PAIRED, "Pairing did not finish")
        } finally {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    /** One GATT connection: writes go out one at a time, notifications come in on Android's Bluetooth thread. */
    private inner class GattConnection(private val device: BluetoothDevice) {
        var onNotification: (ByteArray) -> Unit = {}
        var onLost: () -> Unit = {}

        private val ready = CompletableFuture<Int>()
        private val main = Handler(Looper.getMainLooper())
        private var gatt: BluetoothGatt? = null
        private var send: BluetoothGattCharacteristic? = null
        private var mtu = 23
        private val queue = ArrayDeque<ByteArray>()
        private var writing = false
        @Volatile private var closed = false

        private val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> if (!g.requestMtu(515)) g.discoverServices()
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        ready.completeExceptionally(WatchLinkException(WatchLinkException.Reason.CANNOT_OPEN, "Connection lost (status $status)"))
                        if (!closed) onLost()
                        runCatching { g.close() }
                    }
                }
            }

            override fun onMtuChanged(g: BluetoothGatt, newMtu: Int, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS && newMtu >= 23) mtu = newMtu
                g.discoverServices()
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                val service = g.getService(MultiLink.SERVICE)
                val pair = MultiLink.CHARACTERISTIC_PAIRS.firstNotNullOfOrNull { (rx, tx) ->
                    val r = service?.getCharacteristic(rx)
                    val t = service?.getCharacteristic(tx)
                    if (r != null && t != null) r to t else null
                }
                if (pair == null) {
                    ready.completeExceptionally(WatchLinkException(WatchLinkException.Reason.CANNOT_OPEN, "This is not a Garmin watch we can sync"))
                    g.disconnect()
                    return
                }
                send = pair.second
                g.setCharacteristicNotification(pair.first, true)
                val config = pair.first.getDescriptor(CLIENT_CONFIG)
                if (config == null) {
                    ready.complete(mtu)
                    return
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    g.writeDescriptor(config, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    @Suppress("DEPRECATION")
                    config.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    g.writeDescriptor(config)
                }
            }

            override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    ready.complete(mtu)
                } else {
                    ready.completeExceptionally(WatchLinkException(WatchLinkException.Reason.NOT_PAIRED, "The watch refused us (status $status); pair it again"))
                }
            }

            override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                synchronized(queue) { writing = false }
                pump()
            }

            override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
                onNotification(value)
            }

            @Deprecated("Used on Android 12 and older")
            @Suppress("DEPRECATION")
            override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) characteristic.value?.let(onNotification)
            }
        }

        /** Connects and returns the MTU once notifications are on. */
        fun connect(): Int {
            gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            return try {
                ready.get(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
            } catch (e: TimeoutException) {
                throw WatchLinkException(WatchLinkException.Reason.CANNOT_OPEN, "The watch did not answer; is it nearby and awake?")
            } catch (e: ExecutionException) {
                throw (e.cause as? WatchLinkException) ?: WatchLinkException(WatchLinkException.Reason.CANNOT_OPEN, e.cause?.message)
            }
        }

        fun write(packet: ByteArray) {
            if (closed) return
            synchronized(queue) { queue.addLast(packet) }
            pump()
        }

        private fun pump() {
            val g = gatt ?: return
            val characteristic = send ?: return
            val packet = synchronized(queue) {
                if (writing || queue.isEmpty() || closed) return
                writing = true
                queue.removeFirst()
            }
            val started = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    g.writeCharacteristic(characteristic, packet, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    @Suppress("DEPRECATION")
                    characteristic.value = packet
                    @Suppress("DEPRECATION")
                    g.writeCharacteristic(characteristic)
                }
            } catch (e: SecurityException) {
                false
            }
            if (!started) {
                // Android's Bluetooth stack was busy: put it back and try again shortly
                synchronized(queue) {
                    queue.addFirst(packet)
                    writing = false
                }
                main.postDelayed({ pump() }, 20)
            }
        }

        fun close() {
            closed = true
            val g = gatt ?: return
            runCatching { g.disconnect() }
            // a link that never connected sends no "disconnected" to close it there, so close it here a moment later
            main.postDelayed({ runCatching { g.close() } }, 1_000)
        }
    }
}
