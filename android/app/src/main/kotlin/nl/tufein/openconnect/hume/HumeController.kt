package nl.tufein.openconnect.hume

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
import nl.tufein.openconnect.core.health.HealthAccess
import nl.tufein.openconnect.core.hume.HumeDay
import nl.tufein.openconnect.core.hume.HumeException
import nl.tufein.openconnect.core.hume.HumeGateway
import nl.tufein.openconnect.core.hume.HumeSync
import nl.tufein.openconnect.core.hume.HumeSyncReport
import nl.tufein.openconnect.core.runCatchingUnlessCancelled
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean

/** What the Hume screen remembers: which app the person picked as the band's, and when the last sync ran. */
interface HumeSettings {
    /** The package of the app the person chose as the band's; null lets the link recognise the Hume app itself. */
    var origin: String?

    /** Epoch milliseconds of the last sync that ran to the end, 0 when there was none. */
    var lastSyncMs: Long
}

/** Why something on the Hume screen did not work, in terms the screen can explain. */
enum class HumeProblem { UNAVAILABLE, NEEDS_PERMISSION, NO_DATA, FAILED }

data class HumeUi(
    val access: HealthAccess = HealthAccess.UNAVAILABLE,
    /** What the phone's permission dialog is asked for; empty until Health Connect has been asked. */
    val permissions: Set<String> = emptySet(),
    /** The app the person chose, or null for "recognise the Hume app". */
    val origin: String? = null,
    /** The apps Health Connect holds data of, known after a sync found none of the band's. */
    val found: Set<String> = emptySet(),
    /** The apps the last sync read from. */
    val using: Set<String> = emptySet(),
    val lastSync: Instant? = null,
    val busy: Boolean = false,
    val problem: HumeProblem? = null,
    /** What the last sync brought in, until the next one starts. */
    val last: HumeSyncReport? = null,
    /** The band's last 30 days, oldest first, as stored apart from the watch's data. */
    val days: List<HumeDay> = emptyList(),
) {
    val ready: Boolean get() = access == HealthAccess.READY
}

/**
 * The Hume Band link. Hume has no public interface of its own: its app writes what the band measures to Health Connect, and
 * this reads it from there, only the data of the band's own app. No Android types: Health Connect sits behind [HumeGateway].
 * Not tried with a real band.
 */
class HumeController(
    private val settings: HumeSettings,
    private val repo: Repository,
    private val gateway: HumeGateway,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val now: () -> Instant = { Instant.now() },
    private val onDataChanged: () -> Unit = {},
    private val onSynced: (SyncLogEntry) -> Unit = {},
    /** Told whether the background job is wanted, which is while Health Connect lets the app read. */
    private val onBackgroundWanted: (Boolean) -> Unit = {},
) {
    companion object {
        const val FIRST_SYNC_DAYS = 30
        const val LONG_DAYS = 7
        const val SHORT_DAYS = 2

        /** How many days the Hume tab shows. */
        const val DAYS_SHOWN = 30L

        /** After this long without a sync, the next automatic one looks a week back instead of two days. */
        val LONG_AFTER: Duration = Duration.ofHours(12)
    }

    private val sync = HumeSync(gateway, repo, zone())
    private val running = AtomicBoolean(false)

    @Volatile private var days: List<HumeDay> = emptyList()
    private val _state = MutableStateFlow(HumeUi(origin = settings.origin, lastSync = lastSync()))
    val state: StateFlow<HumeUi> = _state.asStateFlow()

    init {
        refreshAccess()
        refreshDays()
    }

    /** Asks the phone again what Health Connect allows; call when the tab opens and after the permission dialog closed. */
    fun refreshAccess() {
        scope.launch(io) {
            val access = try {
                gateway.access()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                HealthAccess.UNAVAILABLE
            }
            val permissions = if (access == HealthAccess.UNAVAILABLE) emptySet() else runCatchingUnlessCancelled { gateway.permissionsToAsk() }.getOrDefault(emptySet())
            _state.update { it.copy(access = access, permissions = permissions, problem = if (access == HealthAccess.READY && it.problem == HumeProblem.NEEDS_PERMISSION) null else it.problem) }
            onBackgroundWanted(access == HealthAccess.READY)
        }
    }

    /** Reads the stored days again, for the Hume tab (after a sync, a restore or clearing the data). */
    fun refreshDays() {
        scope.launch(io) {
            val today = LocalDate.now(zone())
            days = repo.listHumeDays(today.minusDays(DAYS_SHOWN - 1), today)
            _state.update { it.copy(days = days) }
        }
    }

    private fun lastSync(): Instant? = settings.lastSyncMs.takeIf { it > 0 }?.let(Instant::ofEpochMilli)

    /** The state now, with what Health Connect said last about access and permissions kept. */
    private fun snapshot(busy: Boolean = false, problem: HumeProblem? = null, last: HumeSyncReport? = null, found: Set<String> = emptySet(), using: Set<String> = emptySet()) =
        _state.value.copy(origin = settings.origin, found = found, using = using, lastSync = lastSync(), busy = busy, problem = problem, last = last, days = days)

    /** The person picked the app that wrote the band's data, or null to go back to recognising the Hume app. Syncs at once. */
    fun chooseOrigin(pkg: String?) {
        settings.origin = pkg
        _state.update { it.copy(origin = pkg, problem = null, found = emptySet()) }
        start(FIRST_SYNC_DAYS)
    }

    /** Forgets the chosen app and when the last sync was; the stored days stay. */
    fun forget() {
        settings.origin = null
        settings.lastSyncMs = 0
        _state.value = snapshot()
    }

    fun sync() = start(FIRST_SYNC_DAYS)

    /** For when the app opens and for the ten-minute timer: syncs when the last sync is at least [gap] ago and Health Connect lets us read. */
    fun syncIfDue(gap: Duration) {
        if (!_state.value.ready || !due(gap)) return
        start(autoDays(), quietUnlessReady = true)
    }

    /** The same, run to the end for the background job. True when it synced. */
    suspend fun syncInBackground(gap: Duration): Boolean {
        if (!due(gap)) return false
        if (!running.compareAndSet(false, true)) return false
        var settled = false // true once the flag is freed, or execute() has taken over freeing it
        try {
            return withContext(io) {
                val ready = try {
                    gateway.access() == HealthAccess.READY
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    false
                }
                settled = true
                if (!ready) {
                    running.set(false)
                    false
                } else {
                    _state.update { it.copy(busy = true, problem = null) }
                    execute(autoDays())
                }
            }
        } catch (e: CancellationException) {
            // Cancelled on the way in or while asking Health Connect, before anything freed the flag.
            if (!settled) {
                running.set(false)
                _state.update { it.copy(busy = false) }
            }
            throw e
        }
    }

    private fun due(gap: Duration): Boolean {
        val last = settings.lastSyncMs.takeIf { it > 0 } ?: return true
        return Duration.between(Instant.ofEpochMilli(last), now()) >= gap
    }

    private fun autoDays(): Int {
        val last = settings.lastSyncMs.takeIf { it > 0 } ?: return LONG_DAYS
        return if (Duration.between(Instant.ofEpochMilli(last), now()) >= LONG_AFTER) LONG_DAYS else SHORT_DAYS
    }

    private fun start(days: Int, quietUnlessReady: Boolean = false) {
        if (!running.compareAndSet(false, true)) return
        _state.update { it.copy(busy = true, problem = null) }
        scope.launch(io) {
            if (quietUnlessReady && runCatchingUnlessCancelled { gateway.access() }.getOrNull() != HealthAccess.READY) {
                running.set(false)
                _state.update { it.copy(busy = false) }
                return@launch
            }
            execute(days)
        }
    }

    /** Runs one sync to the end. The caller has taken [running]; it is released here. */
    private suspend fun execute(daysBack: Int): Boolean {
        var report: HumeSyncReport? = null
        var found: Set<String> = emptySet()
        val problem = try {
            report = sync.sync(daysBack, LocalDate.now(zone()), settings.origin)
            settings.lastSyncMs = now().toEpochMilli()
            null
        } catch (e: HumeException) {
            found = e.found
            when (e.kind) {
                HumeException.Kind.UNAVAILABLE -> HumeProblem.UNAVAILABLE
                HumeException.Kind.NEEDS_PERMISSION -> HumeProblem.NEEDS_PERMISSION
                HumeException.Kind.NO_DATA -> HumeProblem.NO_DATA
            }
        } catch (e: CancellationException) {
            running.set(false)
            _state.update { it.copy(busy = false) }
            throw e
        } catch (e: SecurityException) {
            HumeProblem.NEEDS_PERMISSION
        } catch (e: IOException) {
            HumeProblem.FAILED
        } catch (e: Exception) {
            HumeProblem.FAILED
        }
        onSynced(SyncLogEntry(now(), SyncLog.SOURCE_HUME, ok = problem == null, added = report?.total ?: 0, detail = problem?.name.orEmpty()))
        if ((report?.total ?: 0) > 0) onDataChanged()
        running.set(false)
        val today = LocalDate.now(zone())
        this.days = repo.listHumeDays(today.minusDays(DAYS_SHOWN - 1), today)
        val access = runCatchingUnlessCancelled { gateway.access() }.getOrDefault(HealthAccess.UNAVAILABLE)
        _state.value = snapshot(problem = problem, last = report, found = found, using = report?.origins.orEmpty()).copy(access = access)
        onBackgroundWanted(access == HealthAccess.READY)
        return problem == null
    }
}
