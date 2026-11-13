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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import nl.tufein.openconnect.OpenConnectApp
import nl.tufein.openconnect.R
import nl.tufein.openconnect.core.Achievements
import nl.tufein.openconnect.core.AchievementsCalculator
import nl.tufein.openconnect.core.Activity
import nl.tufein.openconnect.core.Backup
import nl.tufein.openconnect.core.BestEfforts
import nl.tufein.openconnect.core.Effort
import nl.tufein.openconnect.core.Lap
import nl.tufein.openconnect.core.PlanConfig
import nl.tufein.openconnect.core.PlanGoal
import nl.tufein.openconnect.core.PlanProgress
import nl.tufein.openconnect.core.TrainingPlan
import nl.tufein.openconnect.core.TrainingPlans
import nl.tufein.openconnect.core.BackupException
import nl.tufein.openconnect.core.Csv
import nl.tufein.openconnect.core.DataOverview
import nl.tufein.openconnect.core.SyncLogEntry
import nl.tufein.openconnect.health.HealthUi
import nl.tufein.openconnect.core.DailySummary
import nl.tufein.openconnect.core.DemoData
import nl.tufein.openconnect.core.Overview
import nl.tufein.openconnect.core.PlayerLevel
import nl.tufein.openconnect.core.ReadinessCalculator
import nl.tufein.openconnect.core.Readiness
import nl.tufein.openconnect.core.TrainingLoadCalculator
import nl.tufein.openconnect.core.TrainingLoad
import nl.tufein.openconnect.core.WeeklyGoalProgress
import nl.tufein.openconnect.core.WeeklyGoals
import nl.tufein.openconnect.core.RecordEntry
import nl.tufein.openconnect.core.SleepSummary
import nl.tufein.openconnect.core.Sport
import nl.tufein.openconnect.core.StoredReport
import nl.tufein.openconnect.core.TrendRow
import nl.tufein.openconnect.core.WeeklySummary
import nl.tufein.openconnect.core.formats.FormatError
import nl.tufein.openconnect.core.formats.Formats
import nl.tufein.openconnect.core.personalRecords
import nl.tufein.openconnect.core.reports.AiVendor
import nl.tufein.openconnect.core.reports.ReportError
import nl.tufein.openconnect.core.reports.ReportPeriod
import nl.tufein.openconnect.core.weekStart
import nl.tufein.openconnect.ai.AiUi
import nl.tufein.openconnect.ai.CoachUi
import nl.tufein.openconnect.garmin.GarminUi
import nl.tufein.openconnect.watch.BleSensor
import nl.tufein.openconnect.watch.GarminBleWatch
import nl.tufein.openconnect.watch.WatchInfo
import nl.tufein.openconnect.watch.WatchUi
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

data class HomeState(
    val loaded: Boolean = false,
    val overview: Overview? = null,
    val hasData: Boolean = false,
    /** OpenConnect's own 0 to 100 estimate for the day, null while fewer than two signals are known. */
    val readiness: Readiness? = null,
    /** The night that ended on the day shown, if the watch reported it. */
    val sleep: SleepSummary? = null,
    val level: PlayerLevel? = null,
    val goals: WeeklyGoals = WeeklyGoals(),
    /** This week against [goals]. */
    val goalProgress: WeeklyGoalProgress? = null,
    /** This week's training load against your four-week average; null until there are two weeks of activities. */
    val load: TrainingLoad? = null,
)

data class TrophiesState(val loaded: Boolean = false, val achievements: Achievements? = null)

data class ActivitiesState(
    val loaded: Boolean = false,
    val sport: Sport? = null,
    val sports: List<Sport> = emptyList(),
    val items: List<Activity> = emptyList(),
)

data class DetailState(
    val loaded: Boolean = false,
    val activity: Activity? = null,
    val laps: List<Lap> = emptyList(),
    val efforts: List<Effort> = emptyList(),
    val report: StoredReport? = null,
)

data class HealthState(
    val loaded: Boolean = false,
    val trend: List<TrendRow> = emptyList(),
    val sleeps: List<SleepSummary> = emptyList(),
    val dailies: List<DailySummary> = emptyList(),
    /** Readiness per day (OpenConnect's estimate) and the watch's sleep score per night, for the score charts. */
    val readiness: List<Pair<LocalDate, Int?>> = emptyList(),
    val sleepScores: List<Pair<LocalDate, Double?>> = emptyList(),
)

data class ReportsState(
    val loaded: Boolean = false,
    /** The first day of the week, month or year on screen (the name is from when only weeks existed). */
    val weekStart: LocalDate = weekStart(LocalDate.now(ZoneOffset.UTC)),
    val period: ReportPeriod = ReportPeriod.WEEK,
    val summary: WeeklySummary? = null,
    val report: StoredReport? = null,
    val records: Map<Sport, Map<String, RecordEntry>> = emptyMap(),
    /** The best times over 1 km, 5 km, 10 km and up, over all runs. */
    val efforts: List<Effort> = emptyList(),
)

private const val MAX_IMPORT_BYTES = 25 * 1024 * 1024
private const val BACKUP_MAX_BYTES = 200 * 1024 * 1024

private val FIRST_DAY = LocalDate.of(1970, 1, 1)
private val LAST_DAY = LocalDate.of(2100, 12, 31)

const val WATCH_ROUTE = "watch"
const val GARMIN_ROUTE = "garmin"
const val OURA_ROUTE = "oura"

/** Which devices the person uses. [onboarded] is false until the first-start questions are answered. */
data class SourcesUi(val onboarded: Boolean = true, val garmin: Boolean = true, val oura: Boolean = false)
const val CSV_DAILY = "daily"
const val CSV_ACTIVITIES = "activities"

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

    private val _trophies = MutableStateFlow(TrophiesState())
    val trophies: StateFlow<TrophiesState> = _trophies.asStateFlow()

    /** What the last syncs did, newest first. */
    val syncLog: StateFlow<List<SyncLogEntry>> = container.syncLog.entries

    private val _overview = MutableStateFlow<DataOverview?>(null)

    /** Where the data came from, counted per source. */
    val overview: StateFlow<DataOverview?> = _overview.asStateFlow()

    private val _goals = MutableStateFlow(container.settings.weeklyGoals)

    /** The weekly goals, as set in Settings. */
    val goals: StateFlow<WeeklyGoals> = _goals.asStateFlow()

    private val _theme = MutableStateFlow(container.settings.themeMode)

    /** "system", "dark" or "light". */
    val theme: StateFlow<String> = _theme.asStateFlow()

    private val _sources = MutableStateFlow(sourcesNow())

    /** Which devices the person uses, as chosen at the first start or later in Settings. */
    val sources: StateFlow<SourcesUi> = _sources.asStateFlow()

    private fun sourcesNow() = container.settings.let { SourcesUi(it.onboarded, it.useGarmin, it.useOura) }

    /** Saves the choice of devices (from the first start or Settings); the first-start questions are then done. */
    fun setSources(garmin: Boolean, oura: Boolean) {
        val settings = container.settings
        settings.useGarmin = garmin
        settings.useOura = oura
        settings.onboarded = true
        _sources.value = sourcesNow()
        container.applySources()
        if (oura) container.oura.syncIfDue(OPEN_GAP)
    }

    private val _now = MutableStateFlow(Instant.now())

    /** The time the screens measure "5 min ago" against. It moves when the app opens and when a sync changes. */
    val now: StateFlow<Instant> = _now.asStateFlow()

    private val _busy = MutableStateFlow(false)

    /** True while a report is being written. */
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    /** The watch screen's state and actions live in [AppContainer.watch], which outlives the activity. */
    val watch: StateFlow<WatchUi> get() = container.watch.state

    /** The Garmin account screen's state and actions live in [AppContainer.garmin], which outlives the activity. */
    val garmin: StateFlow<GarminUi> get() = container.garmin.state

    /** The AI providers (Claude, Gemini) and the coach live in the container, which outlives the activity. */
    val ai: StateFlow<AiUi> get() = container.ai.state

    val coach: StateFlow<CoachUi> get() = container.coach.state

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
        container.health.refresh()
        refreshAll()
        viewModelScope.launch { container.dataChanged.collect { refreshAll() } }
        // A sync starting or ending moves the clock "last synced" is measured against.
        viewModelScope.launch { container.garmin.state.collect { _now.value = Instant.now() } }
        // A key added or removed changes whether the coach can answer.
        viewModelScope.launch { container.ai.state.collect { container.coach.refresh() } }
        container.garmin.autoSyncIfDue()
    }

    /** The app came to the front: sync if the last sync is old enough, like Garmin Connect does. */
    /** The app came to the front: sync every connected account (unless that happened in the last minute) and keep
     *  doing so every ten minutes while it stays open. */
    fun onResume() {
        _now.value = Instant.now()
        syncAccounts(OPEN_GAP)
        ticker?.cancel()
        // Off the main thread: a ten-minute wait has no business in the UI's queue.
        ticker = viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                kotlinx.coroutines.delay(TICK.toMillis())
                _now.value = Instant.now()
                syncAccounts(TICK.minusSeconds(30))
            }
        }
    }

    /** The app went to the background: the background jobs take over. */
    fun onPause() {
        ticker?.cancel()
        ticker = null
    }

    private var ticker: kotlinx.coroutines.Job? = null

    /** Only the devices the person chose: someone without a ring never talks to Oura, and the same for Garmin. */
    private fun syncAccounts(gap: java.time.Duration) {
        if (container.settings.useGarmin) container.garmin.autoSyncIfDue(gap)
        if (container.settings.useOura) container.oura.syncIfDue(gap)
    }

    private companion object {
        val OPEN_GAP: java.time.Duration = java.time.Duration.ofMinutes(1)
        val TICK: java.time.Duration = java.time.Duration.ofMinutes(10)
    }

    // ------------------------------------------------------------------------------------ AI

    fun saveAiKey(vendor: AiVendor, pasted: String) = container.ai.saveKey(vendor, pasted)

    fun checkAiKey(vendor: AiVendor) = container.ai.checkKey(vendor)

    fun forgetAiKey(vendor: AiVendor) = container.ai.forgetKey(vendor)

    fun setAiModel(vendor: AiVendor, model: String) = container.ai.setModel(vendor, model)

    fun setAiChoice(choice: String) = container.ai.setChoice(choice)

    fun setAiLanguage(language: String) = container.ai.setLanguage(language)

    fun askCoach(question: String) = container.coach.ask(question)

    fun clearCoach() = container.coach.clear()

    fun setGoals(goals: WeeklyGoals) {
        container.settings.weeklyGoals = goals
        _goals.value = container.settings.weeklyGoals
        loadHome()
    }

    fun setTheme(mode: String) {
        container.settings.themeMode = mode
        _theme.value = container.settings.themeMode
    }

    // ---------------------------------------------------------------------------------- watch

    fun refreshWatches() = container.watch.refreshUsb()

    fun readWatch(info: WatchInfo) = container.watch.syncUsb(info)

    /** [tree] is the address the folder picker returned. */
    fun readFolder(tree: Uri) = container.watch.syncFolder(tree.toString())

    fun scanSensors() = container.watch.startScan()

    fun connectSensor(sensor: BleSensor) = container.watch.connect(sensor)

    fun stopLive() = container.watch.stopLive()

    fun findGarmin() = container.watch.findGarmin()

    fun stopFindGarmin() = container.watch.stopFindGarmin()

    fun syncGarmin(watch: GarminBleWatch) = container.watch.syncGarmin(watch)

    fun forgetGarmin() = container.watch.forgetGarmin()

    val healthConnect: StateFlow<HealthUi> = container.health.state

    fun refreshHealth() = container.health.refresh()

    fun exportHealth() = container.health.exportNow()

    fun importHealth() = container.health.importNow()

    fun setHealthAuto(on: Boolean) = container.health.setAuto(on)

    private val _nudges = MutableStateFlow(container.settings.nudges.enabled)
    val nudges: StateFlow<Boolean> = _nudges.asStateFlow()

    fun setNudges(on: Boolean) {
        container.setNudges(on)
        _nudges.value = on
    }

    fun setGarminArchive(on: Boolean) = container.watch.setGarminArchive(on)

    fun setGarminBackground(on: Boolean) = container.watch.setGarminBackground(on)

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

    // ----------------------------------------------------------------------------------- Oura

    val oura: StateFlow<nl.tufein.openconnect.oura.OuraUi> get() = container.oura.state

    fun saveOuraClient(id: String, secret: String, redirect: String) = container.oura.saveClient(id, secret, redirect)

    fun ouraLoginUrl(): String? = container.oura.loginUrl()

    fun finishOuraLogin(redirect: String) = container.oura.finishLogin(redirect)

    fun syncOura() = container.oura.sync()

    fun disconnectOura() = container.oura.disconnect()

    fun forgetOura() = container.oura.forget()

    fun cancelGarmin() = container.garmin.cancel()

    // ------------------------------------------------------------------------------- loading

    fun refreshAll() {
        io { _overview.value = DataOverview.of(repo) }
        loadHome()
        loadActivities(_activities.value.sport)
        loadHealth()
        loadReports(_reports.value.weekStart)
        loadEfforts()
        loadPlan()
        loadTrophies()
        container.oura.refreshDays()
        currentActivityId?.let { openActivity(it) }
    }

    private fun loadHome() = io {
        val overview = container.stats.overview()
        val goals = container.settings.weeklyGoals
        val today = overview.today
        val sleep = today?.let { repo.listSleep(it.date.minusDays(1), it.date).lastOrNull() }
        val readiness = today?.let {
            ReadinessCalculator.compute(it, sleep, repo.listDaily(it.date.minusDays(ReadinessCalculator.BASELINE_DAYS.toLong()), it.date.minusDays(1)))
        }
        _home.value = HomeState(
            loaded = true,
            overview = overview,
            hasData = repo.countActivities() > 0 || today != null,
            readiness = readiness,
            sleep = sleep,
            level = AchievementsCalculator.compute(repo, ZoneId.systemDefault()).player,
            goals = goals,
            goalProgress = goals.progress(overview.thisWeek),
            load = TrainingLoadCalculator.compute(repo.listActivities(limit = 10_000), LocalDate.now(ZoneOffset.UTC)),
        )
    }

    fun loadTrophies() = io {
        _trophies.value = TrophiesState(loaded = true, achievements = AchievementsCalculator.compute(repo, ZoneId.systemDefault()))
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
                laps = activity?.let { BestEfforts.laps(it.track) }.orEmpty(),
                efforts = activity?.takeIf { it.sport == Sport.RUNNING }?.let { BestEfforts.efforts(it.track) }.orEmpty(),
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
            readiness = container.stats.readinessSeries(start, today),
            sleepScores = container.stats.sleepScoreSeries(start, today),
        )
    }

    fun loadReports(day: LocalDate, period: ReportPeriod = _reports.value.period) = io {
        val start = period.start(day)
        val summary = container.stats.summary(start, period.end(start))
        val report = container.reports.cached(period.kind, start.toString())
        val records = personalRecords(repo)
        _reports.update { it.copy(loaded = true, weekStart = start, period = period, summary = summary, report = report, records = records) }
    }

    /** Moves to the previous (-1) or next (1) week, month or year, whichever is on screen. */
    fun shiftWeek(by: Long) = loadReports(_reports.value.period.shift(_reports.value.weekStart, by))

    /** Switches between week, month and year, starting at the current one. */
    fun setReportPeriod(period: ReportPeriod) = loadReports(LocalDate.now(ZoneOffset.UTC), period)

    private val _plan = MutableStateFlow(PlanState())

    /** The training plan and how far it has got. */
    val plan: StateFlow<PlanState> = _plan.asStateFlow()

    fun loadPlan() = io {
        val today = LocalDate.now(ZoneOffset.UTC)
        val config = PlanConfig.decode(container.settings.trainingPlan)
        val built = config?.let(TrainingPlans::build)
        val progress = built?.let {
            val from = it.start.atStartOfDay().toInstant(ZoneOffset.UTC)
            val to = it.weeks.last().end.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
            TrainingPlans.progress(it, repo.listActivities(sport = Sport.RUNNING, start = from, end = to, limit = 500), today)
        }
        val baseline = TrainingPlans.baselineKm(repo, today)
        _plan.value = PlanState(loaded = true, plan = built, progress = progress, baselineKm = baseline, today = today)
    }

    /** Builds a plan that starts next Monday from what you run now. */
    fun createPlan(goal: PlanGoal, runsPerWeek: Int, weeks: Int) {
        val today = LocalDate.now(ZoneOffset.UTC)
        val config = PlanConfig(goal, runsPerWeek, weeks, TrainingPlans.nextMonday(today).toString(), TrainingPlans.baselineKm(repo, today))
        container.settings.trainingPlan = config.coerced().encode()
        loadPlan()
    }

    fun deletePlan() {
        container.settings.trainingPlan = ""
        loadPlan()
    }

    private val _lock = MutableStateFlow(LockUi(container.lockGate.enabled, container.lockGate.locked))

    /** Whether the app is locked; the activity shows the lock screen while [LockUi.locked]. */
    val lock: StateFlow<LockUi> = _lock.asStateFlow()

    private fun publishLock() {
        _lock.value = LockUi(container.lockGate.enabled, container.lockGate.locked)
    }

    fun lockLeft() = container.lockGate.left()

    fun lockReturned() {
        container.lockGate.returned()
        publishLock()
    }

    fun unlocked() {
        container.lockGate.markUnlocked()
        publishLock()
    }

    fun setAppLock(on: Boolean) {
        container.setAppLock(on)
        publishLock()
    }

    private fun loadEfforts() = io {
        val efforts = BestEfforts.personalBests(repo)
        _reports.update { it.copy(efforts = efforts) }
    }

    // ------------------------------------------------------------------------------- actions

    fun loadDemo() = io {
        val data = DemoData.generate(LocalDate.now(ZoneOffset.UTC))
        repo.inTransaction {
            DemoData.store(repo, data)
            // Ring days too, for someone who chose the Oura Ring; they stay apart from the watch's demo days.
            if (container.settings.useOura) nl.tufein.openconnect.core.oura.OuraDemo.days(LocalDate.now()).forEach(repo::upsertOuraDay)
        }
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

    /** Writes every day of health data ("daily") or every activity ("activities") to the chosen file as CSV. */
    fun exportCsv(kind: String, target: Uri) = io {
        val csv = if (kind == CSV_ACTIVITIES) {
            Csv.activities(repo.listActivities(limit = 100_000))
        } else {
            Csv.dailies(repo.listDaily(FIRST_DAY, LAST_DAY), repo.listSleep(FIRST_DAY, LAST_DAY))
        }
        val out = getApplication<Application>().contentResolver.openOutputStream(target) ?: throw IOException("unwritable")
        out.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
        tell(text(R.string.exported_as, csvFileName(kind)))
    }

    /** Writes one compressed file with everything the app holds (no keys) to the chosen place. */
    fun backup(target: Uri) = io {
        val bytes = Backup.write(repo)
        val out = getApplication<Application>().contentResolver.openOutputStream(target) ?: throw IOException("unwritable")
        out.use { it.write(bytes) }
        tell(text(R.string.backup_saved, backupFileName()))
    }

    /** Reads a backup file and merges it in. A file that is not a backup, or is damaged, changes nothing. */
    fun restore(source: Uri) = io {
        val bytes = getApplication<Application>().contentResolver.openInputStream(source)?.use { readLimited(it, BACKUP_MAX_BYTES) } ?: throw IOException("unreadable")
        try {
            val report = Backup.restore(bytes, repo)
            tell(text(R.string.backup_restored, report.activities, report.days + report.nights))
            refreshAll()
        } catch (e: BackupException) {
            tell(text(R.string.backup_refused, e.message ?: ""))
        }
    }

    fun backupFileName(): String = "openconnect-backup-" + LocalDate.now() + ".ocbak"

    fun clearSyncLog() = container.syncLog.clear()

    fun csvFileName(kind: String): String = "openconnect-" + (if (kind == CSV_ACTIVITIES) "activities" else "daily") + "-" + LocalDate.now() + ".csv"

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
                val report = container.reports.activityReport(id, container.ai.provider(), container.ai.language, force)
                _detail.value = _detail.value.copy(report = report)
            } finally {
                _busy.value = false
            }
        }
    }

    fun generateWeeklyReport(force: Boolean) = io {
        _busy.value = true
        try {
            val shown = _reports.value
            val report = container.reports.periodReport(shown.period, shown.weekStart, container.ai.provider(), container.ai.language, force)
            _reports.update { it.copy(report = report) }
        } finally {
            _busy.value = false
        }
    }

    private fun readLimited(input: InputStream, limit: Int = MAX_IMPORT_BYTES): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            if (out.size() > limit) throw FormatError(text(R.string.error_too_large))
        }
        return out.toByteArray()
    }
}

/** The training plan on the Plan tab: nothing chosen yet ([plan] is null) or the plan and how far it has got. */
data class PlanState(
    val loaded: Boolean = false,
    val plan: TrainingPlan? = null,
    val progress: PlanProgress? = null,
    /** What you run in a week now, which the next plan starts from. */
    val baselineKm: Double = 0.0,
    val today: LocalDate = LocalDate.now(ZoneOffset.UTC),
)

/** Whether the app is locked right now (and whether the lock is on at all). */
data class LockUi(val enabled: Boolean = false, val locked: Boolean = false)
