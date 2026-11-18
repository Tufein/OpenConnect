package nl.tufein.openconnect.whoop

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
import nl.tufein.openconnect.core.RetryAfter
import nl.tufein.openconnect.core.RetryPolicy
import nl.tufein.openconnect.core.SyncLog
import nl.tufein.openconnect.core.SyncLogEntry
import nl.tufein.openconnect.core.whoop.WhoopAuth
import nl.tufein.openconnect.core.whoop.WhoopClient
import nl.tufein.openconnect.core.whoop.WhoopDay
import nl.tufein.openconnect.core.whoop.WhoopException
import nl.tufein.openconnect.core.whoop.WhoopHttp
import nl.tufein.openconnect.core.whoop.WhoopStore
import nl.tufein.openconnect.core.whoop.WhoopSync
import nl.tufein.openconnect.core.whoop.WhoopSyncReport
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean

/** What the WHOOP screen remembers. The real one keeps the secret and the tokens encrypted. */
interface WhoopSettings : WhoopStore {
    /** Epoch milliseconds of the last sync that ran to the end, 0 when there was none. */
    var lastSyncMs: Long
}

/** Why something on the WHOOP screen did not work, in terms the screen can explain. */
enum class WhoopProblem { CANNOT_STORE, MISSING_FIELDS, LOGIN_FAILED, LOGIN_EXPIRED, FORBIDDEN, RATE_LIMITED, NETWORK, SERVER }

data class WhoopUi(
    val canStore: Boolean = true,
    val hasClient: Boolean = false,
    val connected: Boolean = false,
    val clientId: String = "",
    val redirectUri: String = WhoopAuth.DEFAULT_REDIRECT,
    val lastSync: Instant? = null,
    val busy: Boolean = false,
    val problem: WhoopProblem? = null,
    /** What the last sync brought in, until the next one starts. */
    val last: WhoopSyncReport? = null,
    /** The band's last 30 days, oldest first, as stored apart from the watch's data. */
    val days: List<WhoopDay> = emptyList(),
)

/**
 * The WHOOP link: keep the owner's own WHOOP application (client id and secret), log in through the browser, and
 * pull days, nights, recovery, strain and workouts into the app. No Android types: the network sits behind [WhoopHttp], the
 * secrets behind [WhoopSettings]. Not tried against the real WHOOP service.
 */
class WhoopController(
    private val settings: WhoopSettings,
    private val repo: Repository,
    http: WhoopHttp,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val now: () -> Instant = { Instant.now() },
    private val onDataChanged: () -> Unit = {},
    private val onSynced: (SyncLogEntry) -> Unit = {},
    /** Told whether the background job is wanted, which is while a login is saved. */
    private val onBackgroundWanted: (Boolean) -> Unit = {},
    /** What to do about a dropped connection, a 429 or a 5xx before a sync gives up. The app asks for [RetryPolicy.standard]. */
    retry: RetryPolicy = RetryPolicy.NONE,
) {
    companion object {
        const val FIRST_SYNC_DAYS = 30
        const val LONG_DAYS = 7
        const val SHORT_DAYS = 2

        /** How many days the WHOOP tab shows. */
        const val DAYS_SHOWN = 30L

        /** After this long without a sync, the next automatic one looks a week back instead of two days. */
        val LONG_AFTER: Duration = Duration.ofHours(12)
    }

    private val auth = WhoopAuth(http, settings, now)
    private val sync = WhoopSync(WhoopClient(http, auth, retry, now), repo)
    private val running = AtomicBoolean(false)

    /** After a rate limit the automatic syncs stay quiet until this moment; the Sync button still works. In memory only. */
    @Volatile private var coolDownUntil: Instant? = null
    @Volatile private var days: List<WhoopDay> = emptyList()
    private val _state = MutableStateFlow(snapshot())
    val state: StateFlow<WhoopUi> = _state.asStateFlow()

    init {
        onBackgroundWanted(auth.connected)
        refreshDays()
    }

    /** Reads the stored days again, for the WHOOP tab (after a sync, a restore or clearing the data). */
    fun refreshDays() {
        scope.launch(io) {
            val today = LocalDate.now(zone())
            days = repo.listWhoopDays(today.minusDays(DAYS_SHOWN - 1), today)
            _state.update { it.copy(days = days) }
        }
    }

    private fun snapshot(busy: Boolean = false, problem: WhoopProblem? = null, last: WhoopSyncReport? = null) = WhoopUi(
        canStore = settings.canStoreSecrets,
        hasClient = auth.hasClient,
        connected = auth.connected,
        clientId = settings.clientId.orEmpty(),
        redirectUri = settings.redirectUri,
        lastSync = settings.lastSyncMs.takeIf { it > 0 }?.let(Instant::ofEpochMilli),
        busy = busy,
        problem = problem,
        last = last,
        days = days,
    )

    /** Saves the application the owner made in the WHOOP Developer Dashboard. A blank secret keeps the one already saved. */
    fun saveClient(clientId: String, clientSecret: String, redirectUri: String) {
        if (!settings.canStoreSecrets) {
            _state.update { it.copy(problem = WhoopProblem.CANNOT_STORE) }
            return
        }
        val id = clientId.trim()
        val secret = clientSecret.trim().ifEmpty { settings.clientSecret.orEmpty() }
        if (id.isEmpty() || secret.isEmpty()) {
            _state.update { it.copy(problem = WhoopProblem.MISSING_FIELDS) }
            return
        }
        if (id != settings.clientId) auth.disconnect() // tokens of another application are of no use
        settings.clientId = id
        settings.clientSecret = secret
        settings.redirectUri = redirectUri.trim().ifEmpty { WhoopAuth.DEFAULT_REDIRECT }
        _state.value = snapshot()
    }

    /** The address to open in the browser to log in at WHOOP, or null when no application is saved. */
    fun loginUrl(): String? = try {
        auth.begin()
    } catch (_: WhoopException) {
        _state.update { it.copy(problem = WhoopProblem.MISSING_FIELDS) }
        null
    }

    /** Called with the address WHOOP sent the browser back to; logs in and then syncs the last month. */
    fun finishLogin(redirect: String) {
        if (!running.compareAndSet(false, true)) return
        _state.update { it.copy(busy = true, problem = null) }
        scope.launch(io) {
            val problem = try {
                auth.finish(redirect)
                null
            } catch (e: WhoopException) {
                if (e.kind == WhoopException.Kind.NETWORK) WhoopProblem.NETWORK else WhoopProblem.LOGIN_FAILED
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
        coolDownUntil?.let { if (now().isBefore(it)) return false }
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
    private fun execute(daysBack: Int): Boolean {
        var report: WhoopSyncReport? = null
        val problem = try {
            report = sync.sync(daysBack, LocalDate.now(zone()))
            settings.lastSyncMs = now().toEpochMilli()
            coolDownUntil = null
            null
        } catch (e: WhoopException) {
            if (e.kind == WhoopException.Kind.RATE_LIMITED) coolDownUntil = now().plus(RetryAfter.coolDown(e.retryAfterS))
            when (e.kind) {
                WhoopException.Kind.NOT_CONNECTED, WhoopException.Kind.LOGIN_EXPIRED, WhoopException.Kind.BAD_REDIRECT -> WhoopProblem.LOGIN_EXPIRED
                WhoopException.Kind.FORBIDDEN -> WhoopProblem.FORBIDDEN
                WhoopException.Kind.RATE_LIMITED -> WhoopProblem.RATE_LIMITED
                WhoopException.Kind.NETWORK -> WhoopProblem.NETWORK
                WhoopException.Kind.SERVER -> WhoopProblem.SERVER
            }
        } catch (e: CancellationException) {
            running.set(false)
            throw e
        } catch (e: Exception) {
            WhoopProblem.SERVER
        }
        onSynced(SyncLogEntry(now(), SyncLog.SOURCE_WHOOP, ok = problem == null, added = report?.total ?: 0, detail = problem?.name.orEmpty()))
        if ((report?.total ?: 0) > 0) onDataChanged()
        running.set(false)
        val today = LocalDate.now(zone())
        this.days = repo.listWhoopDays(today.minusDays(DAYS_SHOWN - 1), today)
        _state.value = snapshot(problem = problem, last = report)
        onBackgroundWanted(auth.connected)
        return problem == null
    }
}
