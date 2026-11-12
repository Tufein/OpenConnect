package nl.tufein.openconnect.watch

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.tufein.openconnect.core.Repository
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

enum class SyncFailure { NOT_FOUND, NO_PERMISSION, CANNOT_OPEN, NO_FILES, OTHER }

sealed interface SyncStatus {
    data object Idle : SyncStatus

    data class Running(val device: String, val done: Int, val total: Int, val current: String) : SyncStatus

    data class Done(val device: String, val report: WatchSyncReport) : SyncStatus

    data class Failed(val reason: SyncFailure, val detail: String? = null) : SyncStatus
}

enum class LivePhase { IDLE, SCANNING, CONNECTING, STREAMING, STOPPED }

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
)

data class WatchUi(
    val watches: List<WatchInfo> = emptyList(),
    val sync: SyncStatus = SyncStatus.Idle,
    val live: LiveUi = LiveUi(),
)

/**
 * Everything the Watch screen does: find a watch on USB and copy its data, and show a live heart rate
 * from a Bluetooth sensor (a Garmin watch in "broadcast heart rate" mode, or a chest strap).
 * It has no Android types in it: the phone's USB and Bluetooth sit behind [WatchLink] and [HeartRateLink].
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
) {
    private val _state = MutableStateFlow(WatchUi())
    val state: StateFlow<WatchUi> = _state.asStateFlow()

    // ------------------------------------------------------------------------------------- USB

    fun refreshUsb() {
        _state.update { it.copy(watches = usb.attached()) }
    }

    fun syncUsb(info: WatchInfo) {
        if (_state.value.sync is SyncStatus.Running) return
        _state.update { it.copy(sync = SyncStatus.Running(info.name, 0, 0, "")) }
        scope.launch(io) {
            // Not inside update { }: that lambda is run again when another thread changed the state meanwhile.
            val result = runSync(info)
            _state.update { it.copy(sync = result) }
            refreshUsb()
        }
    }

    private suspend fun runSync(info: WatchInfo): SyncStatus {
        val opened = try {
            usb.open(info)
        } catch (e: WatchLinkException) {
            return SyncStatus.Failed(
                when (e.reason) {
                    WatchLinkException.Reason.NOT_FOUND -> SyncFailure.NOT_FOUND
                    WatchLinkException.Reason.NO_PERMISSION -> SyncFailure.NO_PERMISSION
                    WatchLinkException.Reason.CANNOT_OPEN -> SyncFailure.CANNOT_OPEN
                },
            )
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
                    report.filesFound == 0 -> SyncStatus.Failed(SyncFailure.NO_FILES)
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
        connection?.close()
        recording = LiveHeartRate()
        recordingStart = null
        _state.update { it.copy(live = LiveUi(phase = LivePhase.CONNECTING, sensorName = sensor.name)) }
        connection = ble.connect(
            sensor,
            object : HeartRateListener {
                override fun onConnected() {
                    _state.update { it.copy(live = it.live.copy(phase = LivePhase.STREAMING)) }
                }

                override fun onMeasurement(measurement: HeartRateMeasurement) = record(measurement)

                override fun onDisconnected(error: String?) {
                    _state.update { it.copy(live = it.live.copy(phase = LivePhase.STOPPED, error = error)) }
                }
            },
        )
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
        connection?.close()
        connection = null
        _state.update { ui ->
            if (ui.live.phase == LivePhase.IDLE) ui else ui.copy(live = ui.live.copy(phase = LivePhase.STOPPED))
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
    }
}
