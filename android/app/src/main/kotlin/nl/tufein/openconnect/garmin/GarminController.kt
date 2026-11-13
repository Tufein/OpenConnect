package nl.tufein.openconnect.garmin

import nl.tufein.openconnect.core.SyncLog
import nl.tufein.openconnect.core.SyncLogEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.tufein.openconnect.core.Repository
import nl.tufein.openconnect.core.garmin.GarminApiException
import nl.tufein.openconnect.core.garmin.GarminHttp
import nl.tufein.openconnect.core.garmin.GarminProgress
import nl.tufein.openconnect.core.garmin.GarminSyncReport
import nl.tufein.openconnect.core.garmin.GarminSyncService
import nl.tufein.openconnect.core.garmin.PersonalApiClient
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean

/** What the Garmin screen remembers between visits. The key is kept encrypted by the real implementation. */
interface GarminSettings {
    /** False when this phone cannot keep a secret safely; then no key is accepted at all. */
    val canStoreKey: Boolean

    var key: String?

    /** How many days a manual sync covers: 7, 30 or 90. */
    var days: Int

    var autoSync: Boolean

    /** Epoch milliseconds of the last sync that ran to the end, 0 when there was none. */
    var lastSyncMs: Long
}

enum class KeyCheck {
    /** Nothing known: no key, or it was saved earlier and not tried since. */
    UNKNOWN,
    CHECKING,
    ACCEPTED,
    REJECTED,

    /** Garmin could not be reached, so the key is neither accepted nor refused. */
    UNREACHABLE,
}

/** Why a pasted key was not saved. */
enum class KeyProblem { MALFORMED, CANNOT_STORE }

/** How a sync that ran without the screen (from the background job) ended, in terms the job understands. */
enum class BackgroundOutcome {
    /** There was nothing to do: no key, auto-sync off, synced a moment ago, or a sync already running. */
    SKIPPED,
    SYNCED,

    /** No connection, a busy Garmin or a crash: try again later. */
    RETRY,

    /** Garmin refuses the key, so trying again would only repeat it. */
    KEY_REJECTED,
}

sealed interface GarminSyncUi {
    data object Idle : GarminSyncUi

    data class Running(val done: Int, val total: Int, val label: String, val cancelling: Boolean = false) : GarminSyncUi

    data class Done(val report: GarminSyncReport) : GarminSyncUi

    data class Failed(val detail: String?) : GarminSyncUi
}

data class GarminUi(
    val hasKey: Boolean = false,
    val canStoreKey: Boolean = true,
    val keyCheck: KeyCheck = KeyCheck.UNKNOWN,
    val keyProblem: KeyProblem? = null,
    val days: Int = 30,
    val autoSync: Boolean = false,
    val lastSync: Instant? = null,
    val sync: GarminSyncUi = GarminSyncUi.Idle,
)

/**
 * Everything the Garmin screen does: keep the owner's Personal API key, check it, and pull their data from Garmin
 * into the app. It has no Android types in it: the network sits behind [GarminHttp], the key behind [GarminSettings].
 */
class GarminController(
    private val settings: GarminSettings,
    private val repo: Repository,
    http: GarminHttp,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val now: () -> Instant = { Instant.now() },
    /** Called after something was written to the repository, so screens can reload. */
    private val onDataChanged: () -> Unit = {},
    /** Told whether a background sync job is wanted, which is when there is a key and auto-sync is on. */
    private val onBackgroundWanted: (Boolean) -> Unit = {},
    /** Told how every sync ended, for the sync log. */
    private val onSynced: (SyncLogEntry) -> Unit = {},
) {
    companion object {
        val DAY_CHOICES = listOf(7, 30, 90)

        /** A sync in the background (when the app opens, or the job) covers this many days: the recent ones are what changes. */
        const val AUTO_SYNC_DAYS = 7

        /** The app syncs by itself at most this often: when it is opened, every ten minutes while open, and in the background. */
        val AUTO_SYNC_EVERY: Duration = Duration.ofMinutes(10)

        private val KEY_SHAPE = Regex("[A-Za-z0-9._~+/=-]{20,512}")

        /**
         * The key as Garmin shows it, or null when what was pasted cannot be one: spaces and line breaks from the
         * clipboard are removed, as are quotes and a leading `ApiKey`, since all of those get pasted along sometimes.
         */
        fun normalizeKey(pasted: String): String? {
            var text = pasted.trim().trim('"', '\'')
            if (text.startsWith("ApiKey ", ignoreCase = true)) text = text.substring(7)
            text = text.filterNot(Char::isWhitespace)
            return text.takeIf { KEY_SHAPE.matches(it) }
        }
    }

    private val client = PersonalApiClient(http, apiKey = { settings.key }, clock = now)
    private val running = AtomicBoolean(false)
    private val cancelRequested = AtomicBoolean(false)

    private val _state = MutableStateFlow(snapshot())
    val state: StateFlow<GarminUi> = _state.asStateFlow()

    init {
        notifyBackground()
    }

    private fun notifyBackground() = onBackgroundWanted(!settings.key.isNullOrBlank() && settings.autoSync)

    private fun snapshot() = GarminUi(
        hasKey = !settings.key.isNullOrBlank(),
        canStoreKey = settings.canStoreKey,
        days = settings.days.takeIf { it in DAY_CHOICES } ?: 30,
        autoSync = settings.autoSync,
        lastSync = settings.lastSyncMs.takeIf { it > 0 }?.let(Instant::ofEpochMilli),
    )

    // ------------------------------------------------------------------------------------------ the key

    /** Saves a pasted key and, in the background, asks Garmin whether it is accepted. */
    fun saveKey(pasted: String) {
        if (!settings.canStoreKey) {
            _state.update { it.copy(keyProblem = KeyProblem.CANNOT_STORE) }
            return
        }
        val key = normalizeKey(pasted)
        if (key == null) {
            _state.update { it.copy(keyProblem = KeyProblem.MALFORMED) }
            return
        }
        settings.key = key
        client.invalidate()
        _state.update { it.copy(hasKey = true, keyProblem = null, keyCheck = KeyCheck.CHECKING) }
        notifyBackground()
        scope.launch(io) { checkKey() }
    }

    /** Asks Garmin again whether the saved key is accepted. */
    fun recheckKey() {
        if (!_state.value.hasKey) return
        _state.update { it.copy(keyCheck = KeyCheck.CHECKING) }
        scope.launch(io) { checkKey() }
    }

    private fun checkKey() {
        val result = try {
            client.verify()
            KeyCheck.ACCEPTED
        } catch (e: GarminApiException) {
            if (e.kind == GarminApiException.Kind.KEY_REJECTED) KeyCheck.REJECTED else KeyCheck.UNREACHABLE
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            KeyCheck.UNREACHABLE
        }
        _state.update { it.copy(keyCheck = result) }
    }

    fun forgetKey() {
        settings.key = null
        client.invalidate()
        _state.update { it.copy(hasKey = false, keyCheck = KeyCheck.UNKNOWN, keyProblem = null) }
        notifyBackground()
    }

    // --------------------------------------------------------------------------------------- settings

    fun setDays(days: Int) {
        if (days !in DAY_CHOICES) return
        settings.days = days
        _state.update { it.copy(days = days) }
    }

    fun setAutoSync(on: Boolean) {
        settings.autoSync = on
        _state.update { it.copy(autoSync = on) }
        notifyBackground()
    }

    // ------------------------------------------------------------------------------------------- sync

    fun sync() = start(_state.value.days)

    /** Called when the app opens and by the timer: syncs when the user switched that on and the last sync is [gap] ago. */
    fun autoSyncIfDue(gap: Duration = AUTO_SYNC_EVERY) {
        val ui = _state.value
        if (!ui.autoSync || !ui.hasKey) return
        if (due(ui, gap)) start(AUTO_SYNC_DAYS)
    }

    fun cancel() {
        cancelRequested.set(true)
        _state.update { ui -> (ui.sync as? GarminSyncUi.Running)?.let { ui.copy(sync = it.copy(cancelling = true)) } ?: ui }
    }

    private fun due(ui: GarminUi, gap: Duration = AUTO_SYNC_EVERY): Boolean {
        val last = ui.lastSync
        return last == null || Duration.between(last, now()) >= gap
    }

    /**
     * A sync without the screen, for the background job: the same short sync as when the app opens, run to the end on
     * the caller's thread, with the outcome the job needs to decide whether to try again.
     */
    suspend fun syncInBackground(): BackgroundOutcome {
        val ui = _state.value
        if (!ui.autoSync || !ui.hasKey || !due(ui)) return BackgroundOutcome.SKIPPED
        if (!running.compareAndSet(false, true)) return BackgroundOutcome.SKIPPED
        cancelRequested.set(false)
        _state.update { it.copy(sync = GarminSyncUi.Running(0, 1, "")) }
        val outcome = withContext(io) { execute(AUTO_SYNC_DAYS) }
        val result = when (outcome) {
            is GarminSyncUi.Done -> when (outcome.report.stopped?.kind) {
                null -> if (outcome.report.cancelled) BackgroundOutcome.SKIPPED else BackgroundOutcome.SYNCED
                GarminApiException.Kind.KEY_REJECTED -> BackgroundOutcome.KEY_REJECTED
                else -> BackgroundOutcome.RETRY
            }
            else -> BackgroundOutcome.RETRY
        }
        // A key Garmin refuses is no use to a job that runs on its own: it stops until a new key is saved.
        if (result == BackgroundOutcome.KEY_REJECTED) onBackgroundWanted(false)
        return result
    }

    private fun start(days: Int) {
        if (!_state.value.hasKey) return
        if (!running.compareAndSet(false, true)) return
        cancelRequested.set(false)
        _state.update { it.copy(sync = GarminSyncUi.Running(0, 1, "")) }
        scope.launch(io) { execute(days) }
    }

    /** Runs one sync to the end and publishes the result. The caller has taken [running]; it is released here. */
    private fun execute(days: Int): GarminSyncUi {
        val outcome = try {
            val report = GarminSyncService(client, repo, zone(), now).sync(
                days,
                onProgress = { p: GarminProgress ->
                    _state.update { ui -> ui.copy(sync = GarminSyncUi.Running(p.done, p.total, p.label, cancelRequested.get())) }
                },
                isCancelled = { cancelRequested.get() },
            )
            finish(report)
            GarminSyncUi.Done(report)
        } catch (e: CancellationException) {
            running.set(false)
            throw e
        } catch (e: Exception) {
            GarminSyncUi.Failed(e.message)
        }
        onSynced(
            when (outcome) {
                is GarminSyncUi.Done -> outcome.report.let { r ->
                    SyncLogEntry(
                        now(), SyncLog.SOURCE_ACCOUNT, ok = r.stopped == null,
                        added = r.activitiesAdded + r.daysUpdated + r.nightsUpdated,
                        detail = r.stopped?.kind?.name ?: if (r.cancelled) "cancelled" else "",
                    )
                }
                is GarminSyncUi.Failed -> SyncLogEntry(now(), SyncLog.SOURCE_ACCOUNT, ok = false, detail = outcome.detail.orEmpty())
                else -> SyncLogEntry(now(), SyncLog.SOURCE_ACCOUNT, ok = false, detail = "unknown")
            },
        )
        _state.update { it.copy(sync = outcome, lastSync = settings.lastSyncMs.takeIf { ms -> ms > 0 }?.let(Instant::ofEpochMilli)) }
        running.set(false)
        return outcome
    }

    private fun finish(report: GarminSyncReport) {
        val stopped = report.stopped?.kind
        if (stopped == null && !report.cancelled) settings.lastSyncMs = now().toEpochMilli()
        val check = when {
            stopped == GarminApiException.Kind.KEY_REJECTED -> KeyCheck.REJECTED
            stopped == GarminApiException.Kind.NETWORK -> KeyCheck.UNREACHABLE
            report.endpoints.any { it.records > 0 } -> KeyCheck.ACCEPTED
            else -> null
        }
        if (check != null) _state.update { it.copy(keyCheck = check) }
        if (report.daysUpdated + report.nightsUpdated + report.activitiesAdded > 0) onDataChanged()
    }
}
