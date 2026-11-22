package nl.tufein.openconnect.health

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.tufein.openconnect.core.Repository
import nl.tufein.openconnect.core.SyncLog
import nl.tufein.openconnect.core.SyncLogEntry
import nl.tufein.openconnect.core.runCatchingUnlessCancelled
import nl.tufein.openconnect.core.health.HealthAccess
import nl.tufein.openconnect.core.health.HealthConnectGateway
import nl.tufein.openconnect.core.health.ExportLedger
import nl.tufein.openconnect.core.health.HealthConnectSync
import nl.tufein.openconnect.core.health.HealthImportReport
import nl.tufein.openconnect.core.health.InMemoryExportLedger
import java.io.IOException
import java.time.Instant
import java.time.ZoneId

interface HealthSettings {
    /** Send activities to Health Connect after every sync. Off until the person turns it on. */
    var auto: Boolean

    /** Fill missing days, nights and weights from Health Connect in the background. Off until the person turns it on. */
    var autoImport: Boolean
}

sealed interface HealthMessage {
    data class Exported(val sent: Int, val skipped: Int) : HealthMessage

    data class Imported(val days: Int, val nights: Int, val weights: Int = 0) : HealthMessage

    data class Failed(val detail: String?) : HealthMessage
}

data class HealthUi(
    val access: HealthAccess = HealthAccess.UNAVAILABLE,
    val busy: Boolean = false,
    val auto: Boolean = false,
    val autoImport: Boolean = false,
    val message: HealthMessage? = null,
)

/** The Health Connect card: what the phone allows, the buttons, and the two switches (send after a sync, fill gaps in the background). */
class HealthConnectController(
    repo: Repository,
    private val gateway: HealthConnectGateway,
    private val settings: HealthSettings,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    zone: () -> ZoneId = { ZoneId.systemDefault() },
    /** Told after steps, sleep or weight were added, so the screens reload. */
    private val onDataChanged: () -> Unit = {},
    /** What was already sent, so the automatic export sends only what is new or edited. The app keeps it between runs. */
    ledger: ExportLedger = InMemoryExportLedger(),
    /** A background fill that brought something in, for the sync log. Nothing is logged when there was nothing to fill. */
    private val onSynced: (SyncLogEntry) -> Unit = {},
    /** Whether the background job should be scheduled; the app starts or stops it. */
    private val onBackgroundWanted: (Boolean) -> Unit = {},
) {
    private val sync = HealthConnectSync(repo, gateway, zone(), ledger)
    private val _state = MutableStateFlow(HealthUi(auto = settings.auto, autoImport = settings.autoImport))
    val state: StateFlow<HealthUi> = _state.asStateFlow()

    /** Only one fill at a time: the background job and opening the app can both ask. */
    private val filling = java.util.concurrent.atomic.AtomicBoolean(false)
    private val lastAutomatic = java.util.concurrent.atomic.AtomicReference<Instant?>(null)

    init {
        // The worker itself checks the switch and the permission, so scheduling it while it is on is harmless.
        onBackgroundWanted(settings.autoImport)
    }

    /** Asks the phone again what Health Connect allows; call after the permission screen closed. */
    fun refresh() {
        scope.launch(io) {
            val access = try {
                gateway.access()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                HealthAccess.UNAVAILABLE
            }
            _state.update { it.copy(access = access) }
        }
    }

    fun setAuto(on: Boolean) {
        settings.auto = on
        _state.update { it.copy(auto = on) }
    }

    fun setAutoImport(on: Boolean) {
        settings.autoImport = on
        _state.update { it.copy(autoImport = on) }
        onBackgroundWanted(on)
    }

    fun exportNow() = run { sync.export().let { HealthMessage.Exported(it.sent, it.skipped) } }

    fun importNow() = run {
        val r = sync.import()
        if (r.daysFilled + r.nightsFilled + r.weightsFilled > 0) onDataChanged()
        HealthMessage.Imported(r.daysFilled, r.nightsFilled, r.weightsFilled)
    }

    /**
     * Fills the gaps from Health Connect for someone who turned that on, without touching the card: used by the
     * background job and when the app opens. Returns null when it is off, the phone does not allow reading, a run is
     * already going, or something failed — a background run has no one to complain to.
     */
    suspend fun importInBackground(days: Int = 30): HealthImportReport? {
        if (!settings.autoImport) return null
        if (!filling.compareAndSet(false, true)) return null
        return try {
            if (gateway.access() != HealthAccess.READY) {
                null
            } else {
                val r = sync.import(days)
                val added = r.daysFilled + r.nightsFilled + r.weightsFilled
                if (added > 0) {
                    onDataChanged()
                    onSynced(SyncLogEntry(Instant.now(), SyncLog.SOURCE_HEALTH, ok = true, added = added))
                }
                r
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onSynced(SyncLogEntry(Instant.now(), SyncLog.SOURCE_HEALTH, ok = false, detail = e.message ?: "Health Connect refused"))
            null
        } finally {
            filling.set(false)
        }
    }

    /** On opening the app: fills when the switch is on and [gap] has passed since the last automatic attempt. */
    fun autoImportIfDue(gap: java.time.Duration) {
        if (!settings.autoImport) return
        val last = lastAutomatic.get()
        if (last != null && java.time.Duration.between(last, Instant.now()) < gap) return
        lastAutomatic.set(Instant.now())
        scope.launch(io) { importInBackground() }
    }

    /** After a sync: sends new or edited activities when the switch is on and Health Connect is ready. Silent otherwise. */
    fun autoExport() {
        if (!settings.auto) return
        scope.launch(io) {
            if (gateway.access() != HealthAccess.READY) return@launch
            runCatchingUnlessCancelled { sync.export(onlyNew = true) }
        }
    }

    private fun run(block: suspend () -> HealthMessage) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, message = null) }
        scope.launch(io) {
            val message = try {
                if (gateway.access() != HealthAccess.READY) {
                    HealthMessage.Failed(null)
                } else {
                    block()
                }
            } catch (e: IOException) {
                HealthMessage.Failed(e.message)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                HealthMessage.Failed(e.message)
            }
            _state.update { it.copy(busy = false, message = message) }
        }
    }
}
