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
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.tufein.openconnect.core.watch.ble.ExecutorScheduler
import nl.tufein.openconnect.core.watch.ble.GarminBleException
import nl.tufein.openconnect.core.watch.ble.GarminBleSession
import nl.tufein.openconnect.core.watch.ble.GarminBleWatchSource
import nl.tufein.openconnect.core.watch.ble.MultiLink
import nl.tufein.openconnect.core.watch.ble.NotificationOptions
import nl.tufein.openconnect.core.watch.ble.PhoneIdentity
import nl.tufein.openconnect.core.watch.ble.PostResult
import nl.tufein.openconnect.core.watch.ble.WatchNotification
import nl.tufein.openconnect.core.watch.ble.asFileAccess
import nl.tufein.openconnect.notify.NotifyListener
import nl.tufein.openconnect.notify.WatchNotifyConnection
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Syncs a Garmin watch over Bluetooth Low Energy with Garmin's own protocol: Android pairs ("bonds") with the watch,
 * we open a GATT connection, raise the MTU, find the multi-link characteristics, switch on notifications and hand the
 * rest to [GarminBleSession], which has no Android types and is tested against a pretend watch.
 *
 * Like the other links this needs a real phone and watch to exercise.
 */
@SuppressLint("MissingPermission")
class AndroidGarminBleLink(
    private val context: Context,
    /** Asked at every sync, so the switch in the app takes effect at once. */
    private val archiveAfterRead: () -> Boolean = { false },
    /**
     * Where an open notification connection and the syncs find out about each other. The watch has room for one phone,
     * so a sync reads through the connection that is open instead of making a second one.
     */
    val registry: LiveWatchRegistry = LiveWatchRegistry(),
    /** The words and app names a notification connection needs; asked for each new connection, so a language change counts. */
    private val notificationOptions: () -> NotificationOptions = { NotificationOptions() },
    /** One line at a time about what the link is doing (Bluetooth steps, messages from the watch), for the log on the card. */
    private val diagnostics: (String) -> Unit = {},
    /** The charge the watch reports when it connects, 0..100; the battery card keeps it with the time. */
    private val onBattery: (Int) -> Unit = {},
) : GarminBleLink {
    companion object {
        private val CLIENT_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val PAIR_TIMEOUT_S = 90L
        private const val CONNECT_TIMEOUT_S = 30L
        private const val HANDSHAKE_TIMEOUT_MS = 30_000L

        /** A second question after this pause, because a Garmin can settle on a percent higher just after connecting. */
        private const val SECOND_BATTERY_ASK_MS = 2_000L

        /** A notification connection stays up all day; ask again now and then so its battery card does not fossilise. */
        private const val BATTERY_REFRESH_MS = 15 * 60_000L

        /** How long a sync waits after SYNC_COMPLETE for a battery answer the watch had not sent during the sync. */
        private const val BATTERY_GRACE_MS = 5_000L

        /** A write Android has not confirmed after this long is given up on; the link's own resending covers what was lost. */
        private const val WRITE_STUCK_MS = 4_000L

        /** This many stuck writes in a row and the connection is treated as lost, so the forwarder reconnects. */
        private const val MAX_STUCK_WRITES = 3
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
                val watch = garminCandidate(device.address, nameOf(device), null, paired = true) ?: continue
                if (reported.add(device.address)) onFound(watch)
            }
        } catch (e: SecurityException) {
            onError("Bluetooth permission is missing")
            return AutoCloseable { }
        }
        val scanner = adapter.bluetoothLeScanner ?: return AutoCloseable { }
        val main = Handler(Looper.getMainLooper())
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = nameOf(result.device, result.scanRecord?.deviceName)
                val paired = runCatching { result.device.bondState == BluetoothDevice.BOND_BONDED }.getOrDefault(false)
                // every Garmin family: the product number in Garmin's manufacturer data, then the name (see GarminScan.kt)
                val watch = garminCandidate(result.device.address, name, result.scanRecord?.manufacturerSpecificData, paired) ?: return
                main.post { if (reported.add(watch.address)) onFound(watch) }
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
        // Notifications are being forwarded: the watch is connected to us already, so read through that connection.
        registry.sharedFor(watch.address)?.let { live ->
            // The connection is already up for notifications, so establish() did not ask the watch again. Ask now: the
            // card otherwise keeps the charge from the day the notification connection opened.
            live.refreshBattery()
            return@withContext OpenedWatch(GarminBleWatchSource(live.files, live.name, archiveAfterRead())) {
                runCatching { live.finishSync() } // "sync complete", but the link stays: notifications still need it
            }
        }
        val sync = registry.beginSync() // the notification side waits for us while we hold the link
        val link = try {
            establish(watch, onStep, notifications = null, log = { diagnostics("sync: $it") })
        } catch (e: Throwable) {
            sync.close()
            throw e
        }
        // Gadgetbridge asks again when a sync starts: Garmin may only settle its gauge at that boundary.
        val batteryBeforeSync = link.session.batteryReportedAt
        askBattery(link.session)
        OpenedWatch(GarminBleWatchSource(link.session.asFileAccess(), link.name, archiveAfterRead())) {
            runCatching { link.session.finishSync() }
            // The answer can come just after SYNC_COMPLETE. Do not close the link before it had a chance to arrive;
            // a fresh answer during the sync means there is nothing to wait for.
            if (link.session.batteryReportedAt == batteryBeforeSync) {
                askBattery(link.session)
                runCatching { link.session.awaitBattery(BATTERY_GRACE_MS) }
            }
            Thread.sleep(300) // let "sync complete" leave before the link goes
            link.shutdown()
            sync.close()
        }
    }

    override suspend fun openHeartRate(
        watch: GarminBleWatch,
        onBpm: (bpm: Int, restingBpm: Int?) -> Unit,
        onClosed: (String?) -> Unit,
        onStep: (GarminStep) -> Unit,
    ): AutoCloseable = withContext(Dispatchers.IO) {
        // Notifications already keep a connection open: use it instead of asking the watch for a second phone.
        registry.sharedFor(watch.address)?.let { live ->
            return@withContext live.startHeartRate(onBpm, onClosed)
        }
        val sync = registry.beginSync()
        val link = try {
            establish(watch, onStep, notifications = null, log = { diagnostics("heart rate: $it") }, heartRate = true)
        } catch (e: Throwable) {
            sync.close()
            throw e
        }
        try {
            link.session.awaitHeartRate(5_000)
            val stream = link.session.startHeartRate(onBpm, onClosed)
            AutoCloseable {
                stream.close()
                link.shutdown()
                sync.close()
            }
        } catch (e: Throwable) {
            link.shutdown()
            sync.close()
            throw when (e) {
                is WatchLinkException -> e
                else -> WatchLinkException(WatchLinkException.Reason.CANNOT_OPEN, e.message ?: "The watch did not start its heart rate")
            }
        }
    }

    override suspend fun openNotifications(
        watch: GarminBleWatch,
        listener: NotifyListener,
        onStep: (GarminStep) -> Unit,
    ): WatchNotifyConnection = withContext(Dispatchers.IO) {
        val link = establish(watch, onStep, notificationOptions(), log = listener::onLog, heartRate = true)
        val notifier = link.session.notifier
        if (notifier == null) {
            link.shutdown()
            throw WatchLinkException(WatchLinkException.Reason.CANNOT_OPEN, "Notifications are not available")
        }
        notifier.onDismissedOnWatch = listener::onDismissedOnWatch
        notifier.onChange = listener::onStatus
        notifier.onUndelivered = listener::onUndelivered
        val ended = java.util.concurrent.atomic.AtomicBoolean(false)
        val batteryHandler = Handler(Looper.getMainLooper())
        val periodicBattery = object : Runnable {
            override fun run() {
                if (ended.get() || link.session.isEnded) return
                askBattery(link.session)
                batteryHandler.postDelayed(this, BATTERY_REFRESH_MS)
            }
        }
        val published = registry.publish(
            LiveWatchRegistry.Live(
                address = watch.address,
                name = link.name,
                files = link.session.asFileAccess(),
                usable = { !ended.get() },
                finishSync = { link.session.finishSync() },
                refreshBattery = { askBattery(link.session) },
                startHeartRate = { onBpm, onEnded -> link.session.startHeartRate(onBpm, onEnded) },
            ),
        )
        link.session.onEnded = { error ->
            ended.set(true)
            batteryHandler.removeCallbacks(periodicBattery)
            published.close()
            // Closing it ourselves is not news; a lost link is.
            if (error.reason != GarminBleException.Reason.CLOSED) listener.onLost(error.message)
        }
        batteryHandler.postDelayed(periodicBattery, BATTERY_REFRESH_MS)
        if (link.session.isEnded) { // it ended before the callback was in place
            ended.set(true)
            published.close()
            link.shutdown()
            throw WatchLinkException(WatchLinkException.Reason.CANNOT_OPEN, "The watch disconnected")
        }
        object : WatchNotifyConnection {
            override val watchName: String = link.name

            override fun post(notification: WatchNotification): PostResult = notifier.post(notification)

            override fun remove(id: Int) = notifier.remove(id)

            override fun close() {
                ended.set(true)
                batteryHandler.removeCallbacks(periodicBattery)
                published.close()
                link.shutdown()
            }
        }
    }

    /** One connection that is ready: paired, connected, handshake done. [shutdown] ends it and can be called twice. */
    private inner class Established(
        val session: GarminBleSession,
        private val connection: GattConnection,
        private val scheduler: ExecutorScheduler,
        val name: String,
    ) {
        fun shutdown() {
            session.close()
            connection.close()
            scheduler.shutdown()
        }
    }

    /** Pairs when needed, connects, raises the MTU and finishes the handshake. Throws [WatchLinkException]. */
    private fun establish(
        watch: GarminBleWatch,
        onStep: (GarminStep) -> Unit,
        notifications: NotificationOptions?,
        log: (String) -> Unit,
        heartRate: Boolean = false,
    ): Established {
        val adapter = adapter?.takeIf { it.isEnabled }
            ?: throw WatchLinkException(WatchLinkException.Reason.CANNOT_OPEN, "Bluetooth is off")
        val device = try {
            adapter.getRemoteDevice(watch.address)
        } catch (e: IllegalArgumentException) {
            throw WatchLinkException(WatchLinkException.Reason.NOT_FOUND, "Unknown address")
        }
        log("Android's pairing state with the watch: ${bondText(device.bondState)}")
        val firstConnect = try {
            if (device.bondState != BluetoothDevice.BOND_BONDED) {
                log("pairing with the watch")
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
        val connection = GattConnection(device, log)
        val session = GarminBleSession(
            pipe = connection::write,
            phone = PhoneIdentity(
                name = runCatching { adapter.name }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Cercis",
                manufacturer = Build.MANUFACTURER ?: "Android",
                model = Build.DEVICE ?: "phone",
            ),
            scheduler = scheduler,
            firstConnect = firstConnect,
            log = log,
            notifications = notifications,
            heartRate = heartRate,
        )
        // The watch pushes later charges itself; every one of them must reach the battery card, not only the answer
        // to the request we sent during the handshake. Keep the Bluetooth callback thread free of preference writes.
        session.onBatteryReported = { level -> CompletableFuture.runAsync { onBattery(level) } }
        connection.onNotification = session::onNotification
        connection.onLost = { session.onDisconnected() }
        try {
            val mtu = connection.connect()
            session.maxWrite = MultiLink.maxWrite(mtu)
            log("Bluetooth is up (MTU $mtu); starting the handshake")
            onStep(GarminStep.HANDSHAKE)
            session.start()
            val info = session.awaitReady(HANDSHAKE_TIMEOUT_MS)
            log("handshake done with ${info.deviceName} ${info.deviceModel}")
            // The charge comes a moment later through onBatteryReported; a watch that stays quiet must not hold up the sync.
            // Ask once more while the sync runs: Garmin settles its gauge just after connecting, and the first answer can
            // be a percent lower than the watch's own display. The card keeps the newest answer.
            Handler(Looper.getMainLooper()).postDelayed({ askBattery(session) }, SECOND_BATTERY_ASK_MS)
            // the name the watch gives itself, else the model its product number stands for, else what the scan showed
            return Established(session, connection, scheduler, info.nameOr(watch.name))
        } catch (e: Exception) {
            log("connection failed: ${e::class.java.simpleName}: ${e.message}")
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

    /** Asks a ready session for its charge; the answer arrives through [GarminBleSession.onBatteryReported]. */
    private fun askBattery(session: GarminBleSession) {
        runCatching { session.refreshBattery() }
    }

    private fun bondText(state: Int) = when (state) {
        BluetoothDevice.BOND_BONDED -> "paired"
        BluetoothDevice.BOND_BONDING -> "pairing"
        else -> "not paired"
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
        // Exported on purpose, although it is a system broadcast: before Android 13 the not-exported flag makes
        // ContextCompat guard the receiver with a permission that only this app holds, and the Bluetooth process that
        // sends the bond changes does not, so pairing would never be seen to finish. The receiver only looks at the
        // address of the watch it is pairing with.
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
    private inner class GattConnection(private val device: BluetoothDevice, private val log: (String) -> Unit) {
        var onNotification: (ByteArray) -> Unit = {}
        var onLost: () -> Unit = {}

        private val ready = CompletableFuture<Int>()
        private val main = Handler(Looper.getMainLooper())
        private var gatt: BluetoothGatt? = null
        private var send: BluetoothGattCharacteristic? = null
        private var mtu = 23
        private val queue = ArrayDeque<ByteArray>()
        private var writing = false
        private var writeStartedAt = 0L
        private var stuck = 0
        private val stuckCheck = Runnable { checkStuck() }
        private val discoveryStarted = AtomicBoolean(false)
        private val servicesHandled = AtomicBoolean(false)
        @Volatile private var closed = false

        /** Android asks for services once, however often it reports the packet size (the watch asks for one too). */
        private fun startDiscovery(g: BluetoothGatt) {
            if (discoveryStarted.compareAndSet(false, true)) {
                g.discoverServices()
            } else {
                log("Bluetooth: services are already being looked up; not asking again")
            }
        }

        /** A write that Android never confirmed would hold up every packet behind it; give up on it and go on. */
        private fun checkStuck() {
            var lost = false
            synchronized(queue) {
                if (!writing || closed) return
                if (SystemClock.elapsedRealtime() - writeStartedAt < WRITE_STUCK_MS) return // a newer write is on its way
                stuck++
                writing = false
                lost = stuck >= MAX_STUCK_WRITES
            }
            log("Bluetooth: Android did not confirm a write to the watch within ${WRITE_STUCK_MS / 1000} s (stuck $stuck in a row)")
            if (lost) {
                log("Bluetooth: writes keep getting stuck; treating the connection as lost")
                if (!closed) onLost()
                runCatching { gatt?.disconnect() }
            } else {
                pump()
            }
        }

        private val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                log("Bluetooth: connection ${if (newState == BluetoothProfile.STATE_CONNECTED) "up" else "down"} (status $status)")
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> if (!g.requestMtu(515)) startDiscovery(g)
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        ready.completeExceptionally(WatchLinkException(WatchLinkException.Reason.CANNOT_OPEN, "Connection lost (status $status)"))
                        if (!closed) onLost()
                        runCatching { g.close() }
                    }
                }
            }

            override fun onMtuChanged(g: BluetoothGatt, newMtu: Int, status: Int) {
                log("Bluetooth: packet size $newMtu (status $status)")
                if (status == BluetoothGatt.GATT_SUCCESS && newMtu >= 23) mtu = newMtu
                startDiscovery(g)
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                log("Bluetooth: ${g.services.size} services found (status $status)")
                if (!servicesHandled.compareAndSet(false, true)) {
                    log("Bluetooth: services were reported again; going on with the first")
                    return
                }
                val service = g.getService(MultiLink.SERVICE)
                val pair = MultiLink.CHARACTERISTIC_PAIRS.firstNotNullOfOrNull { (rx, tx) ->
                    val r = service?.getCharacteristic(rx)
                    val t = service?.getCharacteristic(tx)
                    if (r != null && t != null) r to t else null
                }
                if (pair == null) {
                    log("Bluetooth: Garmin's multi-link service is not there; services: ${g.services.joinToString { it.uuid.toString().take(8) }}")
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
                log("Bluetooth: receiving from the watch switched on (status $status)")
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    ready.complete(mtu)
                } else {
                    ready.completeExceptionally(WatchLinkException(WatchLinkException.Reason.NOT_PAIRED, "The watch refused us (status $status); pair it again"))
                }
            }

            override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) log("Bluetooth: a write to the watch failed (status $status)")
                main.removeCallbacks(stuckCheck)
                synchronized(queue) {
                    writing = false
                    stuck = 0
                }
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
                writeStartedAt = SystemClock.elapsedRealtime()
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
            } else {
                main.postDelayed(stuckCheck, WRITE_STUCK_MS)
            }
        }

        fun close() {
            closed = true
            main.removeCallbacks(stuckCheck)
            val g = gatt ?: return
            runCatching { g.disconnect() }
            // a link that never connected sends no "disconnected" to close it there, so close it here a moment later
            main.postDelayed({ runCatching { g.close() } }, 1_000)
        }
    }
}
