package nl.tufein.openconnect.health

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.tufein.openconnect.core.Repository
import nl.tufein.openconnect.core.health.HealthAccess
import nl.tufein.openconnect.core.health.HealthConnectGateway
import nl.tufein.openconnect.core.health.HealthConnectSync
import java.io.IOException
import java.time.ZoneId

interface HealthSettings {
    /** Send activities to Health Connect after every sync. Off until the person turns it on. */
    var auto: Boolean
}

sealed interface HealthMessage {
    data class Exported(val sent: Int, val skipped: Int) : HealthMessage

    data class Imported(val days: Int, val nights: Int) : HealthMessage

    data class Failed(val detail: String?) : HealthMessage
}

data class HealthUi(
    val access: HealthAccess = HealthAccess.UNAVAILABLE,
    val busy: Boolean = false,
    val auto: Boolean = false,
    val message: HealthMessage? = null,
)

/** The Health Connect card: what the phone allows, the two buttons, and the switch that sends after every sync. */
class HealthConnectController(
    repo: Repository,
    private val gateway: HealthConnectGateway,
    private val settings: HealthSettings,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    zone: () -> ZoneId = { ZoneId.systemDefault() },
    /** Told after steps or sleep were added, so the screens reload. */
    private val onDataChanged: () -> Unit = {},
) {
    private val sync = HealthConnectSync(repo, gateway, zone())
    private val _state = MutableStateFlow(HealthUi(auto = settings.auto))
    val state: StateFlow<HealthUi> = _state.asStateFlow()

    /** Asks the phone again what Health Connect allows; call after the permission screen closed. */
    fun refresh() {
        scope.launch(io) {
            val access = try {
                gateway.access()
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

    fun exportNow() = run { sync.export().let { HealthMessage.Exported(it.sent, it.skipped) } }

    fun importNow() = run {
        val r = sync.import()
        if (r.daysFilled + r.nightsFilled > 0) onDataChanged()
        HealthMessage.Imported(r.daysFilled, r.nightsFilled)
    }

    /** After a sync: sends new activities when the switch is on and Health Connect is ready. Silent otherwise. */
    fun autoExport() {
        if (!settings.auto) return
        scope.launch(io) {
            if (gateway.access() != HealthAccess.READY) return@launch
            runCatching { sync.export() }
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
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                HealthMessage.Failed(e.message)
            }
            _state.update { it.copy(busy = false, message = message) }
        }
    }
}
