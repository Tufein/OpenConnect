package nl.tufein.openconnect.oura

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
import nl.tufein.openconnect.core.SyncLog
import nl.tufein.openconnect.core.SyncLogEntry
import nl.tufein.openconnect.core.oura.OuraAuth
import nl.tufein.openconnect.core.oura.OuraClient
import nl.tufein.openconnect.core.oura.OuraException
import nl.tufein.openconnect.core.oura.OuraHttp
import nl.tufein.openconnect.core.oura.OuraStore
import nl.tufein.openconnect.core.oura.OuraSync
import nl.tufein.openconnect.core.oura.OuraSyncReport
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean

/** What the Oura screen remembers. The real one keeps the secret and the tokens encrypted. */
interface OuraSettings : OuraStore {
    /** Epoch milliseconds of the last sync that ran to the end, 0 when there was none. */
    var lastSyncMs: Long

    /** Let the ring's night replace the watch's. */
    var preferSleep: Boolean
}

/** Why something on the Oura screen did not work, in terms the screen can explain. */
enum class OuraProblem { CANNOT_STORE, MISSING_FIELDS, LOGIN_FAILED, LOGIN_EXPIRED, FORBIDDEN, RATE_LIMITED, NETWORK, SERVER }

data class OuraUi(
    val canStore: Boolean = true,
    val hasClient: Boolean = false,
    val connected: Boolean = false,
    val clientId: String = "",
    val redirectUri: String = OuraAuth.DEFAULT_REDIRECT,
    val preferSleep: Boolean = false,
    val lastSync: Instant? = null,
    val busy: Boolean = false,
    val problem: OuraProblem? = null,
    /** What the last sync brought in, until the next one starts. */
    val last: OuraSyncReport? = null,
)

/**
 * The Oura Ring link: keep the owner's own Oura application (client id and secret), log in through the browser, and
 * pull days, nights and workouts into the app. No Android types: the network sits behind [OuraHttp], the secrets
 * behind [OuraSettings]. Not tried against the real Oura service.
 */
class OuraController(
    private val settings: OuraSettings,
    repo: Repository,
    http: OuraHttp,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val now: () -> Instant = { Instant.now() },
    private val onDataChanged: () -> Unit = {},
    private val onSynced: (SyncLogEntry) -> Unit = {},
    /** Told whether the background job is wanted, which is while a login is saved. */
    private val onBackgroundWanted: (Boolean) -> Unit = {},
) {
    companion object {
        const val FIRST_SYNC_DAYS = 30
        const val LONG_DAYS = 7
        const val SHORT_DAYS = 2

        /** After this long without a sync, the next automatic one looks a week back instead of two days. */
        val LONG_AFTER: Duration = Duration.ofHours(12)
    }

    private val auth = OuraAuth(http, settings, now)
    private val sync = OuraSync(OuraClient(http, auth), repo)
    private val running = AtomicBoolean(false)
    private val _state = MutableStateFlow(snapshot())
    val state: StateFlow<OuraUi> = _state.asStateFlow()

    init {
        onBackgroundWanted(auth.connected)
    }

    private fun snapshot(busy: Boolean = false, problem: OuraProblem? = null, last: OuraSyncReport? = null) = OuraUi(
        canStore = settings.canStoreSecrets,
        hasClient = auth.hasClient,
        connected = auth.connected,
        clientId = settings.clientId.orEmpty(),
        redirectUri = settings.redirectUri,
        preferSleep = settings.preferSleep,
        lastSync = settings.lastSyncMs.takeIf { it > 0 }?.let(Instant::ofEpochMilli),
        busy = busy,
        problem = problem,
        last = last,
    )

    /** Saves the application the owner made at Oura. A blank secret keeps the one already saved. */
    fun saveClient(clientId: String, clientSecret: String, redirectUri: String) {
        if (!settings.canStoreSecrets) {
            _state.update { it.copy(problem = OuraProblem.CANNOT_STORE) }
            return
        }
        val id = clientId.trim()
        val secret = clientSecret.trim().ifEmpty { settings.clientSecret.orEmpty() }
        if (id.isEmpty() || secret.isEmpty()) {
            _state.update { it.copy(problem = OuraProblem.MISSING_FIELDS) }
            return
        }
        if (id != settings.clientId) auth.disconnect() // tokens of another application are of no use
        settings.clientId = id
        settings.clientSecret = secret
        settings.redirectUri = redirectUri.trim().ifEmpty { OuraAuth.DEFAULT_REDIRECT }
        _state.value = snapshot()
    }

    /** The address to open in the browser to log in at Oura, or null when no application is saved. */
    fun loginUrl(): String? = try {
        auth.begin()
    } catch (_: OuraException) {
        _state.update { it.copy(problem = OuraProblem.MISSING_FIELDS) }
        null
    }

    /** Called with the address Oura sent the browser back to; logs in and then syncs the last month. */
    fun finishLogin(redirect: String) {
        if (!running.compareAndSet(false, true)) return
        _state.update { it.copy(busy = true, problem = null) }
        scope.launch(io) {
            val problem = try {
                auth.finish(redirect)
                null
            } catch (e: OuraException) {
                if (e.kind == OuraException.Kind.NETWORK) OuraProblem.NETWORK else OuraProblem.LOGIN_FAILED
            }
            if (problem != null) {
                running.set(false)
                _state.value = snapshot(problem = problem)
            } else {
                execute(FIRST_SYNC_DAYS)
            }
        }
    }

    fun disconnect() {
        auth.disconnect()
        settings.lastSyncMs = 0
        _state.value = snapshot()
        onBackgroundWanted(false)
    }

    /** Removes the application and the login. */
    fun forget() {
        auth.disconnect()
        settings.clientId = null
        settings.clientSecret = null
        settings.lastSyncMs = 0
        _state.value = snapshot()
        onBackgroundWanted(false)
    }

    fun setPreferSleep(on: Boolean) {
        settings.preferSleep = on
        _state.update { it.copy(preferSleep = on) }
    }

    fun sync() = start(FIRST_SYNC_DAYS)

    /** For when the app opens and for the ten-minute timer: syncs when the last sync is at least [gap] ago. */
    fun syncIfDue(gap: Duration) {
        if (!auth.connected || !due(gap)) return
        start(autoDays())
    }

    /** The same, run to the end for the background job. True when it synced. */
    suspend fun syncInBackground(gap: Duration): Boolean {
        if (!auth.connected || !due(gap)) return false
        if (!running.compareAndSet(false, true)) return false
        _state.update { it.copy(busy = true, problem = null) }
        return withContext(io) { execute(autoDays()) }
    }

    private fun due(gap: Duration): Boolean {
        val last = settings.lastSyncMs.takeIf { it > 0 } ?: return true
        return Duration.between(Instant.ofEpochMilli(last), now()) >= gap
    }

    private fun autoDays(): Int {
        val last = settings.lastSyncMs.takeIf { it > 0 } ?: return LONG_DAYS
        return if (Duration.between(Instant.ofEpochMilli(last), now()) >= LONG_AFTER) LONG_DAYS else SHORT_DAYS
    }

    private fun start(days: Int) {
        if (!auth.connected) return
        if (!running.compareAndSet(false, true)) return
        _state.update { it.copy(busy = true, problem = null) }
        scope.launch(io) { execute(days) }
    }

    /** Runs one sync to the end. The caller has taken [running]; it is released here. */
    private fun execute(days: Int): Boolean {
        var report: OuraSyncReport? = null
        val problem = try {
            report = sync.sync(days, LocalDate.now(zone()), settings.preferSleep)
            settings.lastSyncMs = now().toEpochMilli()
            null
        } catch (e: OuraException) {
            when (e.kind) {
                OuraException.Kind.NOT_CONNECTED, OuraException.Kind.LOGIN_EXPIRED, OuraException.Kind.BAD_REDIRECT -> OuraProblem.LOGIN_EXPIRED
                OuraException.Kind.FORBIDDEN -> OuraProblem.FORBIDDEN
                OuraException.Kind.RATE_LIMITED -> OuraProblem.RATE_LIMITED
                OuraException.Kind.NETWORK -> OuraProblem.NETWORK
                OuraException.Kind.SERVER -> OuraProblem.SERVER
            }
        } catch (e: CancellationException) {
            running.set(false)
            throw e
        } catch (e: Exception) {
            OuraProblem.SERVER
        }
        onSynced(SyncLogEntry(now(), SyncLog.SOURCE_OURA, ok = problem == null, added = report?.total ?: 0, detail = problem?.name.orEmpty()))
        if ((report?.total ?: 0) > 0) onDataChanged()
        running.set(false)
        _state.value = snapshot(problem = problem, last = report)
        onBackgroundWanted(auth.connected)
        return problem == null
    }
}
