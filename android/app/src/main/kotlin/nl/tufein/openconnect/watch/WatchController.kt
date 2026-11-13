package nl.tufein.openconnect.watch

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.tufein.openconnect.core.Repository
import nl.tufein.openconnect.core.SyncLog
import nl.tufein.openconnect.core.SyncLogEntry
import nl.tufein.openconnect.core.Sport
import nl.tufein.openconnect.core.watch.HeartRateMeasurement
import nl.tufein.openconnect.core.watch.LiveHeartRate
import nl.tufein.openconnect.core.watch.WatchSyncProgress
import nl.tufein.openconnect.core.watch.WatchSyncReport
import nl.tufein.openconnect.core.watch.WatchSyncService
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

enum class SyncFailure { NOT_FOUND, NO_PERMISSION, CANNOT_OPEN, NO_FILES, NO_FILES_IN_FOLDER, NOT_PAIRED, OTHER }

/** Which card started the sync, so its result shows there. */
enum class SyncSource { USB, FOLDER, GARMIN_BLE }

/** The Bluetooth sync with a Garmin watch: looking for watches, the one remembered, and how far a connection is. */
data class GarminBleUi(
    val searching: Boolean = false,
    val found: List<GarminBleWatch> = emptyList(),
    val remembered: GarminBleWatch? = null,
    val step: GarminStep? = null,
    val error: String? = null,
)

sealed interface SyncStatus {
    data object Idle : SyncStatus

    data class Running(val device: String, val done: Int, val total: Int, val current: String) : SyncStatus

    data class Done(val device: String, val report: WatchSyncReport) : SyncStatus

    data class Failed(val reason: SyncFailure, val detail: String? = null) : SyncStatus
}

/** [RECONNECTING]: the sensor went away and the phone is trying to reach it again; the recording goes on. */
enum class LivePhase { IDLE, SCANNING, CONNECTING, STREAMING, RECONNECTING, STOPPED }

data class LiveUi(
    val phase: LivePhase = LivePhase.IDLE,
    val sensors: List<BleSensor> = emptyList(),
    val sensorName: String? = null,
    val bpm: Int? = null,
    val min: Int? = null,
    val avg: Int? = null,
    val max: Int? = null,
    val rmssdMs: Int? = null,
    val seconds: Long = 0,
    val hasData: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
    /** How many times in a row the phone has tried to reach the sensor again; 0 while it is connected. */
    val reconnectAttempt: Int = 0,
)

data class WatchUi(
    val watches: List<WatchInfo> = emptyList(),
    val sync: SyncStatus = SyncStatus.Idle,
    val live: LiveUi = LiveUi(),
    val syncSource: SyncSource = SyncSource.USB,
    val garmin: GarminBleUi = GarminBleUi(),
)

/**
 * Everything the Watch screen does: find a watch on USB (or read a folder of watch files) and copy its data, and
 * show a live heart rate from a Bluetooth sensor (a Garmin watch in "broadcast heart rate" mode, or a chest strap).
 * A sensor that drops out is reconnected a few times, and what was recorded so far is kept.
 * It has no Android types in it: the phone's USB, folders and Bluetooth sit behind [WatchLink], [FolderLink] and
 * [HeartRateLink].
 */
class WatchController(
    private val repo: Repository,
    private val usb: WatchLink,
    private val ble: HeartRateLink,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val now: () -> Instant = { Instant.now() },
    /** Called after something was written to the repository, so screens can reload. */
    private val onDataChanged: () -> Unit = {},
    private val folder: FolderLink = FolderLink { throw WatchLinkException(WatchLinkException.Reason.CANNOT_OPEN) },
    /** How many times a lost sensor is tried again before the recording is stopped. */
    private val maxReconnects: Int = 5,
    /** Garmin's own Bluetooth protocol, for syncing a watch without a cable. */
    private val garminBle: GarminBleLink = NoGarminBleLink,
    private val garminMemory: GarminWatchMemory = InMemoryGarminWatchMemory(),
    /** Told how every sync ended, for the sync log. */
    private val onSynced: (SyncLogEntry) -> Unit = {},
    /** Waits before reconnect attempt number [attempt] (1, 2, ...). Tests replace it. */
    private val reconnectDelay: suspend (attempt: Int) -> Unit = { attempt -> delay(minOf(2_000L * attempt, 10_000L)) },
) {
    private val _state = MutableStateFlow(WatchUi(garmin = GarminBleUi(remembered = garminMemory.load())))
    val state: StateFlow<WatchUi> = _state.asStateFlow()

    // ------------------------------------------------------------------------------------- USB

    fun refreshUsb() {
        _state.update { it.copy(watches = usb.attached()) }
    }

    fun syncUsb(info: WatchInfo) = startSync(info.name, SyncFailure.NO_FILES, SyncSource.USB, refreshAfter = true) { usb.open(info) }

    /** Reads the watch files in a folder the user picked ([treeUri] is what the folder picker returned). */
    fun syncFolder(treeUri: String) =
        startSync("", SyncFailure.NO_FILES_IN_FOLDER, SyncSource.FOLDER, refreshAfter = false) { folder.open(treeUri) }

    private fun startSync(
        name: String,
        whenEmpty: SyncFailure,
        source: SyncSource,
        refreshAfter: Boolean,
        open: suspend () -> OpenedWatch,
    ) {
        if (_state.value.sync is SyncStatus.Running) return
        _state.update { it.copy(sync = SyncStatus.Running(name, 0, 0, ""), syncSource = source) }
        scope.launch(io) {
            // Not inside update { }: that lambda is run again when another thread changed the state meanwhile.
            val result = runSync(whenEmpty, open)
            onSynced(logEntry(source, result))
            _state.update { it.copy(sync = result, garmin = it.garmin.copy(step = null)) }
            if (refreshAfter) refreshUsb()
        }
    }

    private fun logEntry(source: SyncSource, result: SyncStatus): SyncLogEntry {
        val key = when (source) {
            SyncSource.USB -> SyncLog.SOURCE_USB
            SyncSource.FOLDER -> SyncLog.SOURCE_FOLDER
            SyncSource.GARMIN_BLE -> SyncLog.SOURCE_BLE
        }
        return when (result) {
            is SyncStatus.Done -> {
                val r = result.report
                SyncLogEntry(now(), key, ok = true, added = r.activitiesAdded + r.daysUpdated + r.nightsUpdated, detail = if (r.cancelled) "cancelled" else "")
            }
            is SyncStatus.Failed -> SyncLogEntry(now(), key, ok = false, detail = listOfNotNull(result.reason.name, result.detail).joinToString(": "))
            else -> SyncLogEntry(now(), key, ok = false, detail = "unknown")
        }
    }

    // ------------------------------------------------------------------------ Garmin Bluetooth

    private var garminSearch: AutoCloseable? = null

    /** Looks for Garmin watches: the ones Android has paired first, then any nearby. */
    fun findGarmin() {
        stopFindGarmin()
        _state.update { it.copy(garmin = it.garmin.copy(searching = true, found = emptyList(), error = null)) }
        garminSearch = garminBle.find(
            onFound = { watch ->
                _state.update { ui ->
                    val g = ui.garmin
                    if (g.found.any { it.address == watch.address }) ui else ui.copy(garmin = g.copy(found = g.found + watch))
                }
            },
            onError = { message ->
                stopFindGarmin()
                _state.update { it.copy(garmin = it.garmin.copy(error = message)) }
            },
        )
    }

    fun stopFindGarmin() {
        garminSearch?.close()
        garminSearch = null
        _state.update { it.copy(garmin = it.garmin.copy(searching = false)) }
    }

    /** Pairs if needed, connects and copies what is new on the watch, like a USB sync. */
    fun syncGarmin(watch: GarminBleWatch) {
        stopFindGarmin()
        startSync(watch.name, SyncFailure.NO_FILES, SyncSource.GARMIN_BLE, refreshAfter = false) {
            val opened = garminBle.open(watch) { step -> _state.update { it.copy(garmin = it.garmin.copy(step = step)) } }
            val remembered = watch.copy(paired = true)
            garminMemory.save(remembered)
            _state.update { it.copy(garmin = it.garmin.copy(remembered = remembered, step = null)) }
            opened
        }
    }

    /** Forgets the remembered watch (Android's pairing stays; remove it in the phone's Bluetooth settings). */
    fun forgetGarmin() {
        garminMemory.save(null)
        _state.update { it.copy(garmin = it.garmin.copy(remembered = null)) }
    }

    private suspend fun runSync(whenEmpty: SyncFailure, open: suspend () -> OpenedWatch): SyncStatus {
        val opened = try {
            open()
        } catch (e: WatchLinkException) {
            return SyncStatus.Failed(
                when (e.reason) {
                    WatchLinkException.Reason.NOT_FOUND -> SyncFailure.NOT_FOUND
                    WatchLinkException.Reason.NO_PERMISSION -> SyncFailure.NO_PERMISSION
                    WatchLinkException.Reason.CANNOT_OPEN -> SyncFailure.CANNOT_OPEN
                    WatchLinkException.Reason.NOT_PAIRED -> SyncFailure.NOT_PAIRED
                },
                e.message,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // a link that failed in a way it did not foresee still ends as a message, never as a crash
            return SyncStatus.Failed(SyncFailure.CANNOT_OPEN, e.message)
        }
        return try {
            opened.use { watch ->
                val name = watch.source.deviceName
                val report = WatchSyncService(zone()).sync(
                    watch.source,
                    repo,
                    onProgress = { p: WatchSyncProgress ->
                        _state.update { it.copy(sync = SyncStatus.Running(name, p.done, p.total, p.current)) }
                    },
                )
                when {
                    report.filesFound == 0 -> SyncStatus.Failed(whenEmpty)
                    // Everything that was tried failed to read: the cable came out or the folder went away, nothing was read.
                    report.filesRead == 0 && report.filesSkipped == 0 && report.errors.isNotEmpty() ->
                        SyncStatus.Failed(SyncFailure.OTHER, report.errors.first())
                    else -> {
                        onDataChanged()
                        SyncStatus.Done(name, report)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SyncStatus.Failed(SyncFailure.OTHER, e.message)
        }
    }

    // ------------------------------------------------------------------------- live heart rate

    private var scan: AutoCloseable? = null
    private var connection: AutoCloseable? = null
    private var recording = LiveHeartRate()
    private var recordingStart: Instant? = null

    /** Bumped for every connection attempt and when the user stops, so late answers of an old connection are ignored. */
    private var generation = 0
    private val lock = Any()

    fun startScan() {
        stopScan()
        _state.update { it.copy(live = LiveUi(phase = LivePhase.SCANNING)) }
        scan = ble.scan(
            onFound = { sensor ->
                _state.update { ui ->
                    val live = ui.live
                    if (live.sensors.any { it.address == sensor.address }) ui else ui.copy(live = live.copy(sensors = live.sensors + sensor))
                }
            },
            onError = { message ->
                stopScan()
                _state.update { it.copy(live = it.live.copy(phase = LivePhase.STOPPED, error = message)) }
            },
        )
    }

    fun stopScan() {
        scan?.close()
        scan = null
        _state.update { ui ->
            if (ui.live.phase == LivePhase.SCANNING) ui.copy(live = ui.live.copy(phase = LivePhase.IDLE)) else ui
        }
    }

    fun connect(sensor: BleSensor) {
        scan?.close()
        scan = null
        synchronized(lock) {
            generation++
            connection?.close()
            connection = null
        }
        recording = LiveHeartRate()
        recordingStart = null
        _state.update { it.copy(live = LiveUi(phase = LivePhase.CONNECTING, sensorName = sensor.name)) }
        open(sensor, attempt = 0)
    }

    /** One connection attempt; [attempt] is 0 for the first and counts the retries after a lost connection. */
    private fun open(sensor: BleSensor, attempt: Int) {
        val mine = synchronized(lock) { ++generation }
        val link = ble.connect(
            sensor,
            object : HeartRateListener {
                /** A connection that worked before it dropped starts the count of failed attempts over. */
                private var worked = false

                override fun onConnected() {
                    if (!current(mine)) return
                    worked = true
                    _state.update { it.copy(live = it.live.copy(phase = LivePhase.STREAMING, error = null, reconnectAttempt = 0)) }
                }

                override fun onMeasurement(measurement: HeartRateMeasurement) {
                    if (current(mine)) record(measurement)
                }

                override fun onDisconnected(error: String?) {
                    if (!current(mine)) return
                    val next = if (worked) 1 else attempt + 1
                    // A null error is a connection closed on purpose; anything else is a sensor that went away.
                    if (error != null && next <= maxReconnects) reconnectLater(sensor, next) else stopWith(error)
                }
            },
        )
        synchronized(lock) {
            if (generation == mine) connection = link else link.close()
        }
    }

    private fun current(mine: Int) = synchronized(lock) { generation == mine }

    private fun stopWith(error: String?) {
        _state.update { it.copy(live = it.live.copy(phase = LivePhase.STOPPED, error = error, reconnectAttempt = 0)) }
    }

    private fun reconnectLater(sensor: BleSensor, attempt: Int) {
        val waiting = synchronized(lock) {
            connection?.close() // the lost connection is dead already; let the radio go
            connection = null
            ++generation
        }
        _state.update { it.copy(live = it.live.copy(phase = LivePhase.RECONNECTING, error = null, reconnectAttempt = attempt)) }
        scope.launch {
            reconnectDelay(attempt)
            if (current(waiting)) open(sensor, attempt) // otherwise the user stopped, or started another connection
        }
    }

    @Synchronized
    private fun record(measurement: HeartRateMeasurement) {
        val time = now()
        if (recordingStart == null) recordingStart = time
        recording.add(time, measurement)
        _state.update { ui ->
            ui.copy(
                live = ui.live.copy(
                    bpm = recording.current,
                    min = recording.min,
                    avg = recording.avg,
                    max = recording.max,
                    rmssdMs = recording.rmssdMs()?.roundToInt(),
                    seconds = recordingStart?.let { Duration.between(it, time).seconds } ?: 0,
                    hasData = recording.count > 0,
                ),
            )
        }
    }

    fun stopLive() {
        scan?.close()
        scan = null
        synchronized(lock) {
            generation++ // answers of the connection that is being closed, and any waiting retry, no longer count
            connection?.close()
            connection = null
        }
        _state.update { ui ->
            if (ui.live.phase == LivePhase.IDLE) ui else ui.copy(live = ui.live.copy(phase = LivePhase.STOPPED, reconnectAttempt = 0))
        }
    }

    /** Saves the recording as an activity. False when there is nothing to save or it was saved already. */
    fun saveLive(name: String, sport: Sport): Boolean {
        val live = _state.value.live
        if (live.saved) return false
        val activity = recording.toActivity(name, sport) ?: return false
        repo.upsertActivity(activity)
        _state.update { it.copy(live = it.live.copy(saved = true)) }
        onDataChanged()
        return true
    }

    /** Ends any scan or connection; call when the screen goes away for good. */
    fun close() {
        stopLive()
        stopFindGarmin()
    }
}
