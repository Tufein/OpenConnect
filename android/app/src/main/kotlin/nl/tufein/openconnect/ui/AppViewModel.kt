package nl.tufein.openconnect.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import nl.tufein.openconnect.OpenConnectApp
import nl.tufein.openconnect.R
import nl.tufein.openconnect.core.Activity
import nl.tufein.openconnect.core.DailySummary
import nl.tufein.openconnect.core.DemoData
import nl.tufein.openconnect.core.Overview
import nl.tufein.openconnect.core.RecordEntry
import nl.tufein.openconnect.core.SleepSummary
import nl.tufein.openconnect.core.Sport
import nl.tufein.openconnect.core.StoredReport
import nl.tufein.openconnect.core.TrendRow
import nl.tufein.openconnect.core.WeeklySummary
import nl.tufein.openconnect.core.formats.FormatError
import nl.tufein.openconnect.core.formats.Formats
import nl.tufein.openconnect.core.personalRecords
import nl.tufein.openconnect.core.reports.ReportError
import nl.tufein.openconnect.core.reports.kilometreSplits
import nl.tufein.openconnect.core.weekStart
import nl.tufein.openconnect.garmin.GarminUi
import nl.tufein.openconnect.watch.BleSensor
import nl.tufein.openconnect.watch.WatchInfo
import nl.tufein.openconnect.watch.WatchUi
import java.io.IOException
import java.io.InputStream
import java.time.LocalDate
import java.time.ZoneOffset

data class HomeState(val loaded: Boolean = false, val overview: Overview? = null, val hasData: Boolean = false)

data class ActivitiesState(
    val loaded: Boolean = false,
    val sport: Sport? = null,
    val sports: List<Sport> = emptyList(),
    val items: List<Activity> = emptyList(),
)

data class DetailState(
    val loaded: Boolean = false,
    val activity: Activity? = null,
    val splits: List<Int> = emptyList(),
    val report: StoredReport? = null,
)

data class HealthState(
    val loaded: Boolean = false,
    val trend: List<TrendRow> = emptyList(),
    val sleeps: List<SleepSummary> = emptyList(),
    val dailies: List<DailySummary> = emptyList(),
)

data class ReportsState(
    val loaded: Boolean = false,
    val weekStart: LocalDate = weekStart(LocalDate.now(ZoneOffset.UTC)),
    val summary: WeeklySummary? = null,
    val report: StoredReport? = null,
    val records: Map<Sport, Map<String, RecordEntry>> = emptyMap(),
)

private const val MAX_IMPORT_BYTES = 25 * 1024 * 1024

const val WATCH_ROUTE = "watch"
const val GARMIN_ROUTE = "garmin"

/** Holds screen state and does all disk and network work off the main thread. */
class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as OpenConnectApp).container
    private val repo = container.repository

    val settings get() = container.settings

    private val _home = MutableStateFlow(HomeState())
    val home: StateFlow<HomeState> = _home.asStateFlow()

    private val _activities = MutableStateFlow(ActivitiesState())
    val activities: StateFlow<ActivitiesState> = _activities.asStateFlow()

    private val _detail = MutableStateFlow(DetailState())
    val detail: StateFlow<DetailState> = _detail.asStateFlow()

    private val _health = MutableStateFlow(HealthState())
    val health: StateFlow<HealthState> = _health.asStateFlow()

    private val _reports = MutableStateFlow(ReportsState())
    val reports: StateFlow<ReportsState> = _reports.asStateFlow()

    private val _busy = MutableStateFlow(false)

    /** True while a report is being written. */
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    /** The watch screen's state and actions live in [AppContainer.watch], which outlives the activity. */
    val watch: StateFlow<WatchUi> get() = container.watch.state

    /** The Garmin account screen's state and actions live in [AppContainer.garmin], which outlives the activity. */
    val garmin: StateFlow<GarminUi> get() = container.garmin.state

    private val _navigation = Channel<String>(Channel.BUFFERED)

    /** Routes the UI should open, for example when a watch is plugged in. */
    val navigation = _navigation.receiveAsFlow()

    private var currentActivityId: String? = null

    private fun text(id: Int, vararg args: Any) = getApplication<Application>().getString(id, *args)

    private fun tell(message: String) {
        _messages.trySend(message)
    }

    private fun io(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                block()
            } catch (e: ReportError) {
                tell(e.message ?: text(R.string.error_generic))
            } catch (e: FormatError) {
                tell(e.message ?: text(R.string.error_generic))
            } catch (e: IOException) {
                tell(text(R.string.error_io))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                tell(e.message ?: text(R.string.error_generic))
            }
        }
    }

    init {
        refreshAll()
        viewModelScope.launch { container.dataChanged.collect { refreshAll() } }
        container.garmin.autoSyncIfDue()
    }

    // ---------------------------------------------------------------------------------- watch

    fun refreshWatches() = container.watch.refreshUsb()

    fun readWatch(info: WatchInfo) = container.watch.syncUsb(info)

    /** [tree] is the address the folder picker returned. */
    fun readFolder(tree: Uri) = container.watch.syncFolder(tree.toString())

    fun scanSensors() = container.watch.startScan()

    fun connectSensor(sensor: BleSensor) = container.watch.connect(sensor)

    fun stopLive() = container.watch.stopLive()

    fun saveLive(name: String) {
        if (container.watch.saveLive(name, Sport.OTHER)) tell(text(R.string.watch_live_saved))
    }

    /** A watch was plugged in: show the watch screen. */
    fun onWatchAttached() {
        container.watch.refreshUsb()
        _navigation.trySend(WATCH_ROUTE)
    }

    // ---------------------------------------------------------------------------------- garmin

    fun saveGarminKey(pasted: String) = container.garmin.saveKey(pasted)

    fun forgetGarminKey() = container.garmin.forgetKey()

    fun setGarminDays(days: Int) = container.garmin.setDays(days)

    fun setGarminAutoSync(on: Boolean) = container.garmin.setAutoSync(on)

    fun syncGarmin() = container.garmin.sync()

    fun cancelGarmin() = container.garmin.cancel()

    // ------------------------------------------------------------------------------- loading

    fun refreshAll() {
        loadHome()
        loadActivities(_activities.value.sport)
        loadHealth()
        loadReports(_reports.value.weekStart)
        currentActivityId?.let { openActivity(it) }
    }

    private fun loadHome() = io {
        val overview = container.stats.overview()
        _home.value = HomeState(loaded = true, overview = overview, hasData = repo.countActivities() > 0 || overview.today != null)
    }

    fun loadActivities(sport: Sport?) = io {
        val all = repo.listActivities(limit = 10_000)
        _activities.value = ActivitiesState(
            loaded = true,
            sport = sport,
            sports = all.map { it.sport }.distinct().sortedBy { it.ordinal },
            items = if (sport == null) all else all.filter { it.sport == sport },
        )
    }

    fun openActivity(id: String) {
        currentActivityId = id
        io {
            val activity = repo.getActivity(id)
            _detail.value = DetailState(
                loaded = true,
                activity = activity,
                splits = activity?.let { kilometreSplits(it.track) }.orEmpty(),
                report = container.reports.cached("activity", id),
            )
        }
    }

    private fun loadHealth() = io {
        val today = LocalDate.now(ZoneOffset.UTC)
        val start = today.minusDays(29)
        _health.value = HealthState(
            loaded = true,
            trend = container.stats.trendSeries(start, today),
            sleeps = repo.listSleep(start, today),
            dailies = repo.listDaily(start, today),
        )
    }

    fun loadReports(week: LocalDate) = io {
        val monday = weekStart(week)
        _reports.value = ReportsState(
            loaded = true,
            weekStart = monday,
            summary = container.stats.weeklySummary(monday),
            report = container.reports.cached("weekly", monday.toString()),
            records = personalRecords(repo),
        )
    }

    fun shiftWeek(weeks: Long) = loadReports(_reports.value.weekStart.plusWeeks(weeks))

    // ------------------------------------------------------------------------------- actions

    fun loadDemo() = io {
        val data = DemoData.generate(LocalDate.now(ZoneOffset.UTC))
        repo.inTransaction { DemoData.store(repo, data) }
        tell(text(R.string.demo_loaded, data.activities.size))
        refreshAll()
    }

    fun clearAll() = io {
        repo.clearAll()
        currentActivityId = null
        _detail.value = DetailState()
        tell(text(R.string.data_cleared))
        refreshAll()
    }

    fun importUris(uris: List<Uri>) = io {
        val resolver = getApplication<Application>().contentResolver
        var imported = 0
        val failures = ArrayList<String>()
        for (uri in uris) {
            val name = uri.lastPathSegment.orEmpty()
            try {
                val bytes = resolver.openInputStream(uri)?.use { readLimited(it) } ?: throw IOException("unreadable")
                val activity = Formats.import(bytes, name)
                repo.upsertActivity(activity)
                imported++
            } catch (e: FormatError) {
                failures.add("$name: ${e.message}")
            } catch (e: IOException) {
                failures.add("$name: ${text(R.string.error_io)}")
            }
        }
        if (imported > 0) tell(text(R.string.imported_n, imported))
        failures.forEach(::tell)
        if (imported > 0) refreshAll()
    }

    fun exportActivity(id: String, format: String, target: Uri) = io {
        val activity = repo.getActivity(id) ?: return@io
        val exported = Formats.export(activity, format)
        val out = getApplication<Application>().contentResolver.openOutputStream(target) ?: throw IOException("unwritable")
        out.use { it.write(exported.bytes) }
        tell(text(R.string.exported_as, exported.filename))
    }

    /** The file name proposed in the save dialog; the activity is the one currently on screen. */
    fun exportFileName(id: String, format: String): String {
        val activity = _detail.value.activity?.takeIf { it.id == id }
        return if (activity != null) Formats.filename(activity, format) else "activity.$format"
    }

    fun deleteActivity(id: String) = io {
        repo.deleteActivity(id)
        currentActivityId = null
        refreshAll()
    }

    fun generateActivityReport(force: Boolean) {
        val id = currentActivityId ?: return
        io {
            _busy.value = true
            try {
                val report = container.reports.activityReport(id, settings.reportProvider(), settings.reportLanguage, force)
                _detail.value = _detail.value.copy(report = report)
            } finally {
                _busy.value = false
            }
        }
    }

    fun generateWeeklyReport(force: Boolean) = io {
        _busy.value = true
        try {
            val report = container.reports.weeklyReport(_reports.value.weekStart, settings.reportProvider(), settings.reportLanguage, force)
            _reports.value = _reports.value.copy(report = report)
        } finally {
            _busy.value = false
        }
    }

    private fun readLimited(input: InputStream): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            if (out.size() > MAX_IMPORT_BYTES) throw FormatError(text(R.string.error_too_large))
        }
        return out.toByteArray()
    }
}
