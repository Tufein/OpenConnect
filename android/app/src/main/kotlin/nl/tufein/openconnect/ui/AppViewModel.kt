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
import nl.tufein.openconnect.core.ActivityAround
import nl.tufein.openconnect.core.ActivityAroundCalculator
import nl.tufein.openconnect.core.BandSource
import nl.tufein.openconnect.core.BodyEntry
import nl.tufein.openconnect.core.WaterEntry
import nl.tufein.openconnect.core.BandWorkout
import nl.tufein.openconnect.core.BandWorkouts
import nl.tufein.openconnect.core.RingWorkouts
import nl.tufein.openconnect.core.Backup
import nl.tufein.openconnect.core.BestEfforts
import nl.tufein.openconnect.core.Effort
import nl.tufein.openconnect.core.Lap
import nl.tufein.openconnect.core.OwnWorkout
import nl.tufein.openconnect.core.fitFile
import nl.tufein.openconnect.core.PlanConfig
import nl.tufein.openconnect.core.PlanGoal
import nl.tufein.openconnect.core.PlanProgress
import nl.tufein.openconnect.core.RaceGoal
import nl.tufein.openconnect.core.RaceState
import nl.tufein.openconnect.core.Races
import nl.tufein.openconnect.core.TrainingPlan
import nl.tufein.openconnect.core.TrainingPaces
import nl.tufein.openconnect.core.TrainingPlans
import nl.tufein.openconnect.core.WorkoutBook
import nl.tufein.openconnect.core.Workouts
import nl.tufein.openconnect.core.BackupException
import nl.tufein.openconnect.core.ExportAll
import nl.tufein.openconnect.core.Csv
import nl.tufein.openconnect.core.DataOverview
import nl.tufein.openconnect.core.SyncLog
import nl.tufein.openconnect.core.SyncLogEntry
import nl.tufein.openconnect.core.ArchiveImport
import nl.tufein.openconnect.health.HealthUi
import nl.tufein.openconnect.core.DailySummary
import nl.tufein.openconnect.core.DayInsights
import nl.tufein.openconnect.core.DayRecords
import nl.tufein.openconnect.core.DaySeries
import nl.tufein.openconnect.core.DemoData
import nl.tufein.openconnect.core.Overview
import nl.tufein.openconnect.core.PlayerLevel
import nl.tufein.openconnect.core.ReadinessCalculator
import nl.tufein.openconnect.core.Readiness
import nl.tufein.openconnect.core.TrainingLoadCalculator
import nl.tufein.openconnect.core.Gear
import nl.tufein.openconnect.core.MonthView
import nl.tufein.openconnect.core.ManualActivity
import nl.tufein.openconnect.core.StreakSummary
import nl.tufein.openconnect.core.Streaks
import nl.tufein.openconnect.core.GearBook
import nl.tufein.openconnect.core.HeartZones
import nl.tufein.openconnect.core.StreakRun
import nl.tufein.openconnect.core.Shoe
import nl.tufein.openconnect.core.ShoeUsage
import nl.tufein.openconnect.core.ActivityZones
import nl.tufein.openconnect.core.MaxHr
import nl.tufein.openconnect.core.MaxHrSource
import nl.tufein.openconnect.core.ZoneOverview
import nl.tufein.openconnect.core.ZoneVerdict
import nl.tufein.openconnect.core.TrainingLoad
import nl.tufein.openconnect.core.BodyWarning
import nl.tufein.openconnect.core.EarlyWarning
import nl.tufein.openconnect.core.FormCalculator
import nl.tufein.openconnect.core.FormSeries
import nl.tufein.openconnect.core.HeatGrid
import nl.tufein.openconnect.core.Heatmap
import nl.tufein.openconnect.core.JournalBook
import nl.tufein.openconnect.core.JournalInsights
import nl.tufein.openconnect.core.JournalTag
import nl.tufein.openconnect.core.MonotonyCalculator
import nl.tufein.openconnect.core.MonotonyResult
import nl.tufein.openconnect.core.RampRate
import nl.tufein.openconnect.core.RampRateCalculator
import nl.tufein.openconnect.core.RestingHrTrend
import nl.tufein.openconnect.core.RestingHrTrendCalculator
import nl.tufein.openconnect.core.SleepConsistency
import nl.tufein.openconnect.core.StepGoalWeek
import nl.tufein.openconnect.core.StepGoalWeekCalculator
import nl.tufein.openconnect.core.SleepConsistencyCalculator
import nl.tufein.openconnect.core.Recovery
import nl.tufein.openconnect.core.SleepNeed
import nl.tufein.openconnect.core.SleepNeedCalculator
import nl.tufein.openconnect.core.RouteHistory
import nl.tufein.openconnect.core.SeasonPlan
import nl.tufein.openconnect.core.SeasonPlanner
import nl.tufein.openconnect.core.YearScore
import nl.tufein.openconnect.core.YearScoreCalculator
import nl.tufein.openconnect.core.YearOverview
import nl.tufein.openconnect.core.YearOverviewCalculator
import nl.tufein.openconnect.core.RouteMatcher
import nl.tufein.openconnect.core.TagEffect
import nl.tufein.openconnect.core.TodayAdvice
import nl.tufein.openconnect.core.TodayAdviceCalculator
import nl.tufein.openconnect.core.RaceDistance
import nl.tufein.openconnect.core.RaceShape
import nl.tufein.openconnect.core.RaceShapeCalculator
import nl.tufein.openconnect.core.Vo2Max
import nl.tufein.openconnect.core.Vo2Summary
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
    /** Fitness, fatigue and form over the last 90 days; null until there are two weeks of activities. */
    val form: FormSeries? = null,
    /** How hard today should be, from readiness and form; null when neither is known. */
    val advice: TodayAdvice? = null,
    /** The days marked in the journal. */
    val journal: JournalBook = JournalBook(),
    /** Resting heart rate or HRV off for days in a row compared with your own last four weeks; null when all is as usual. */
    val warning: BodyWarning? = null,
    /** How alike the last seven days of training were, and the strain; null until there are two weeks of activities. */
    val monotony: MonotonyResult? = null,
    val ramp: RampRate? = null,
    /** How long to sleep tonight; null while fewer than four of the last seven nights are known. */
    val sleepNeed: SleepNeed? = null,
    val sleepConsistency: SleepConsistency? = null,
    val stepGoalWeek: StepGoalWeek? = null,
    val restingHrTrend: RestingHrTrend? = null,
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
    /** Time per heart-rate zone of this activity, and the maximum the zones were worked out from. */
    val zones: ActivityZones? = null,
    val zonesMaxHr: Int = 0,
    /** The other runs on the same route, when there are any. */
    val route: RouteHistory? = null,
    /** The pair of shoes this activity went on, the pairs it could be put on, and whether it was chosen by hand. */
    val shoe: Shoe? = null,
    val shoeChoices: List<Shoe> = emptyList(),
    val shoeByHand: Boolean = false,
    /** What the watch and the Oura Ring measured around this activity (v0.43.0); null when neither has anything to say. */
    val around: ActivityAround? = null,
    /** True for a workout that only the Oura Ring recorded: it is not stored as an activity, so it cannot be deleted, exported or reported on. */
    val fromRing: Boolean = false,
)

data class HealthState(
    val loaded: Boolean = false,
    val trend: List<TrendRow> = emptyList(),
    val sleeps: List<SleepSummary> = emptyList(),
    val dailies: List<DailySummary> = emptyList(),
    /** The weight the person entered by hand, oldest first, at most the last 30 days. */
    val body: List<BodyEntry> = emptyList(),
    /** The water the person logged by hand, oldest first, at most the last 30 days. */
    val water: List<WaterEntry> = emptyList(),
    /** Readiness per day (OpenConnect's estimate) and the watch's sleep score per night, for the score charts. */
    val readiness: List<Pair<LocalDate, Int?>> = emptyList(),
    val sleepScores: List<Pair<LocalDate, Double?>> = emptyList(),
)

/**
 * What the Insights "Day" tab picks from (v0.44.0): the last stretch of days of each device the person uses, read once. The day,
 * the source and the measure are chosen on screen, so they cost no new read. A source is null when its device is not in use.
 */
data class DayState(
    val loaded: Boolean = false,
    val today: LocalDate = LocalDate.now(),
    val garmin: DaySeries? = null,
    val oura: DaySeries? = null,
    val whoop: DaySeries? = null,
    val hume: DaySeries? = null,
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
const val WATCH_APPS_ROUTE = "watch-apps"
const val GARMIN_ROUTE = "garmin"
const val OURA_ROUTE = "oura"

/** The colour theme ([Palette] key), the letters ([Letters] key), the style ([Style] key), signal colours, ring weight and photo tone. */
data class Look(
    val palette: String = "olive",
    val letters: String = "clear",
    val style: String = "editorial",
    val signals: String = "soft",
    val rings: String = "thin",
    val hero: String = "auto",
)

/**
 * What the front page shows: the card under the battery ([metric], a [HomeMetric] key) and whether the battery card,
 * the journal and the "Last 7 days" section are drawn. Everything is on by default, and calories keep the old page.
 */
data class HomePrefs(
    val metric: String = HomeMetric.DEFAULT,
    val battery: Boolean = true,
    val journal: Boolean = true,
    val last7: Boolean = true,
)

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

    private val _import = MutableStateFlow(ImportUi())

    /** What the file import is doing, and what the last one did (v1.3.0). */
    val importState: StateFlow<ImportUi> = _import.asStateFlow()
    private val importCancelled = java.util.concurrent.atomic.AtomicBoolean(false)

    private val _health = MutableStateFlow(HealthState())
    val health: StateFlow<HealthState> = _health.asStateFlow()

    private val _days = MutableStateFlow(DayState())
    val days: StateFlow<DayState> = _days.asStateFlow()

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
        loadDays()
        if (oura) container.oura.syncIfDue(OPEN_GAP)
    }

    private fun lookNow() = Look(
        Palette.of(container.settings.palette).key,
        Letters.of(container.settings.letters).key,
        Style.of(container.settings.style).key,
        container.settings.signals,
        RingWeight.of(container.settings.rings).key,
        container.settings.hero,
    )

    private val _look = MutableStateFlow(lookNow())

    /** The colour theme. */
    val look: StateFlow<Look> = _look.asStateFlow()

    private fun homePrefsNow() = HomePrefs(
        HomeMetric.of(container.settings.homeMetric).key,
        container.settings.homeBattery,
        container.settings.homeJournal,
        container.settings.homeLast7,
    )

    private val _homePrefs = MutableStateFlow(homePrefsNow())

    /** The card under the battery and the optional cards on the front page. */
    val homePrefs: StateFlow<HomePrefs> = _homePrefs.asStateFlow()

    /** The card under the battery: any [HomeMetric] key; an unknown key falls back to calories. */
    fun setHomeMetric(metric: String) {
        container.settings.homeMetric = HomeMetric.of(metric).key
        _homePrefs.value = homePrefsNow()
    }

    fun setHomeBattery(show: Boolean) {
        container.settings.homeBattery = show
        _homePrefs.value = homePrefsNow()
    }

    fun setHomeJournal(show: Boolean) {
        container.settings.homeJournal = show
        _homePrefs.value = homePrefsNow()
    }

    fun setHomeLast7(show: Boolean) {
        container.settings.homeLast7 = show
        _homePrefs.value = homePrefsNow()
    }

    fun setLook(palette: String) {
        container.settings.palette = Palette.of(palette).key
        _look.value = lookNow()
        redrawWidget()
    }

    fun setLetters(letters: String) {
        container.settings.letters = Letters.of(letters).key
        _look.value = lookNow()
    }

    fun setStyle(style: String) {
        container.settings.style = Style.of(style).key
        _look.value = lookNow()
        redrawWidget()
    }

    /** "soft" keeps the signals in the theme's family, "clear" gives each its own strong colour. */
    fun setSignals(signals: String) {
        container.settings.signals = if (signals == "clear") "clear" else "soft"
        _look.value = lookNow()
    }

    /** "thin" (the default) or "bold"; the orbs on the front page and the ring tab follow it. */
    fun setRings(rings: String) {
        container.settings.rings = RingWeight.of(rings).key
        _look.value = lookNow()
    }

    /** "auto" follows the hour; "dawn", "day", "dusk" and "night" hold one light. */
    fun setHero(hero: String) {
        container.settings.hero = hero
        _look.value = lookNow()
    }

    /** The widget has no Compose theme; repaint it with the new palette as soon as one is picked. */
    private fun redrawWidget() {
        nl.tufein.openconnect.widget.HomeWidget.refresh(getApplication()) { container.widgetSummary(LocalDate.now()) }
    }

    private val _appIcon = MutableStateFlow(AppIcons.current(application))

    /** The icon on the launcher, one of [AppIcon]. */
    val appIcon: StateFlow<AppIcon> = _appIcon.asStateFlow()

    fun setAppIcon(icon: AppIcon) {
        AppIcons.select(getApplication(), icon)
        _appIcon.value = AppIcons.current(getApplication())
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

    /** The charge of the watch and the ring for the card on the Watch screen, re-read with [refreshBattery]. */
    val battery = MutableStateFlow(BatteriesUi())

    /** Reads the stored charge again (after a watch sync, an Oura sync, or opening the screen). */
    fun refreshBattery() {
        battery.value = container.settings.batteries()
    }

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

    // These four are filled by refreshAll(), which the init block below starts on another thread, so they have to
    // exist before it (a property declared after an init block is still null while that block runs).
    private val _zones = MutableStateFlow(ZonesState())

    /** Heart-rate zones: the maximum, the zone table and the last four weeks. */
    val zones: StateFlow<ZonesState> = _zones.asStateFlow()

    private val _calendar = MutableStateFlow(CalendarState())

    /** The month calendar and the streaks. */
    val calendar: StateFlow<CalendarState> = _calendar.asStateFlow()

    private val _gear = MutableStateFlow(GearState())

    /** The shoes and what each pair has done. */
    val gear: StateFlow<GearState> = _gear.asStateFlow()

    private val _journal = MutableStateFlow(JournalState())

    /** What the journal says about the nights after the marked days. */
    val journal: StateFlow<JournalState> = _journal.asStateFlow()

    private val _score = MutableStateFlow(ScoreState())

    /** The hi-score card of one year. */
    val score: StateFlow<ScoreState> = _score.asStateFlow()

    private val _year = MutableStateFlow(YearState())

    /** The year in twelve months: steps, sleep, resting heart rate and trainings. */
    val year: StateFlow<YearState> = _year.asStateFlow()

    private val _map = MutableStateFlow(MapState())

    /** The heatmap of all routes. Worked out when the Map tab is opened, and again after the data changes. */
    val map: StateFlow<MapState> = _map.asStateFlow()

    private val _plan = MutableStateFlow(PlanState())

    /** The training plan and how far it has got. */
    val plan: StateFlow<PlanState> = _plan.asStateFlow()

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

    /**
     * The app came to the front: sync every connected account (unless that happened in the last minute) and keep
     * doing so every ten minutes while it stays open, like Garmin Connect does.
     */
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
        // Health Connect is on the phone itself; the switch inside the controller decides whether it is filled.
        container.health.autoImportIfDue(gap)
    }

    private companion object {
        val OPEN_GAP: java.time.Duration = java.time.Duration.ofMinutes(1)
        val TICK: java.time.Duration = java.time.Duration.ofMinutes(10)

        /** The heatmap reads this many of the newest activities, with at most this many positions each. */
        const val MAP_ACTIVITIES = 300
        const val MAP_POINTS = 500

        /** The most other activities whose track is read to look for the same route. */
        const val ROUTE_CANDIDATES = 60
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
        redrawWidget()
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

    fun setHealthAutoImport(on: Boolean) = container.health.setAutoImport(on)

    private val _nudges = MutableStateFlow(container.settings.nudges.enabled)
    val nudges: StateFlow<Boolean> = _nudges.asStateFlow()

    fun setNudges(on: Boolean) {
        container.setNudges(on)
        _nudges.value = on
    }

    fun setGarminArchive(on: Boolean) = container.watch.setGarminArchive(on)

    fun setGarminBackground(on: Boolean) = container.watch.setGarminBackground(on)

    // ------------------------------------------------------------------ notifications on the watch

    /** Where forwarding stands, the person's choices and what was sent; lives in [AppContainer.forwarding]. */
    val forward: StateFlow<nl.tufein.openconnect.notify.ForwardUi> get() = container.forwarding.state

    /** Looks at what is still needed (access, a watch, Bluetooth) and connects when it is all there. */
    /** What forwarding did, line by line, for the "What is happening?" part of the card. */
    val forwardLog: StateFlow<List<nl.tufein.openconnect.notify.ForwardTrail.Line>> get() = container.forwardTrail.flow

    fun refreshForward() = container.forwarding.refresh()

    fun setForward(on: Boolean) = container.forwarding.setEnabled(on)

    fun forwardReconnect() = container.forwarding.reconnect()

    fun setForwardHide(on: Boolean) = container.forwarding.setHideContent(on)

    fun setForwardScreenOff(on: Boolean) = container.forwarding.setOnlyWhenScreenOff(on)

    fun setForwardApp(packageName: String, on: Boolean) = container.forwarding.setApp(packageName, on)

    /** Sends a test notification to the watch and says what happened. */
    fun testForward() {
        val result = container.forwarding.sendTest(text(R.string.forward_test_title), text(R.string.forward_test_body))
        tell(
            text(
                when (result) {
                    nl.tufein.openconnect.notify.TestResult.SENT -> R.string.forward_test_sent
                    nl.tufein.openconnect.notify.TestResult.NOT_CONNECTED -> R.string.forward_test_not_connected
                    nl.tufein.openconnect.notify.TestResult.WATCH_OFF -> R.string.forward_test_watch_off
                    nl.tufein.openconnect.notify.TestResult.WATCH_NOT_ASKING -> R.string.forward_test_not_asking
                },
            ),
        )
    }

    private val _forwardApps = MutableStateFlow<List<nl.tufein.openconnect.notify.InstalledApp>?>(null)

    /** The apps to choose from; null until [loadForwardApps] has asked Android (it can take a moment). */
    val forwardApps: StateFlow<List<nl.tufein.openconnect.notify.InstalledApp>?> = _forwardApps.asStateFlow()

    fun loadForwardApps() = io { _forwardApps.value = container.installedApps.list() }

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
        _map.value = MapState() // stale: the Map tab works it out again when it is open
        io { _overview.value = DataOverview.of(repo) }
        loadHome()
        loadActivities(_activities.value.sport)
        loadHealth()
        loadDays()
        loadReports(_reports.value.weekStart)
        loadEfforts()
        loadPlan()
        loadZones()
        loadGear()
        loadCalendar()
        loadJournal()
        loadScore(_score.value.selected)
        loadYear(_year.value.selected)
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
        val activities = repo.listActivities(limit = 10_000)
        val todayUtc = LocalDate.now(ZoneOffset.UTC)
        val form = FormCalculator.compute(activities, todayUtc)
        val todayLocal = LocalDate.now()
        val warning = EarlyWarning.compute(
            repo.listDaily(todayLocal.minusDays((EarlyWarning.BASELINE_DAYS + EarlyWarning.RECENT_DAYS + 3).toLong()), todayLocal),
            todayLocal,
        )
        val sleepNeed = SleepNeedCalculator.compute(
            repo.listSleep(todayLocal.minusDays((SleepNeedCalculator.WINDOW - 1).toLong()), todayLocal),
            Recovery.dayLoad(activities, todayUtc),
            todayLocal,
        )
        _home.value = HomeState(
            loaded = true,
            overview = overview,
            hasData = repo.countActivities() > 0 || today != null,
            readiness = readiness,
            sleep = sleep,
            level = AchievementsCalculator.compute(repo, ZoneId.systemDefault()).player,
            goals = goals,
            goalProgress = goals.progress(overview.thisWeek),
            load = TrainingLoadCalculator.compute(activities, todayUtc),
            form = form,
            advice = TodayAdviceCalculator.compute(readiness, form?.status),
            journal = JournalBook.decode(container.settings.journal),
            warning = warning,
            monotony = MonotonyCalculator.compute(activities, todayUtc),
            ramp = RampRateCalculator.compute(activities, todayUtc),
            sleepNeed = sleepNeed,
            restingHrTrend = RestingHrTrendCalculator.compute(
                repo.listDaily(todayLocal.minusDays((RestingHrTrendCalculator.RECENT_DAYS + RestingHrTrendCalculator.NORMAL_DAYS - 1).toLong()), todayLocal),
                todayLocal,
            ),
            stepGoalWeek = StepGoalWeekCalculator.compute(
                repo.listDaily(todayLocal.minusDays((StepGoalWeekCalculator.DAYS - 1).toLong()), todayLocal),
                todayLocal,
            ),
            sleepConsistency = SleepConsistencyCalculator.compute(
                repo.listSleep(todayLocal.minusDays((SleepConsistencyCalculator.WINDOW - 1).toLong()), todayLocal),
                todayLocal,
            ),
        )
    }

    fun loadTrophies() = io {
        _trophies.value = TrophiesState(loaded = true, achievements = AchievementsCalculator.compute(repo, ZoneId.systemDefault()))
    }

    fun loadActivities(sport: Sport?) = io {
        val recorded = repo.listActivities(limit = 10_000)
        // For someone who chose the Oura Ring, what the ring recorded and no watch or file did is listed too (v0.43.0).
        // These rows are made here from the ring's stored days and are not written to the activities table, so totals,
        // training load, records and trophies keep counting only what a watch or a file recorded.
        val ring = if (container.settings.useOura) RingWorkouts.extra(repo.listOuraDays(FIRST_DAY, LAST_DAY), recorded) else emptyList()
        val all = if (ring.isEmpty()) recorded else (recorded + ring).sortedByDescending { it.startTime }
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
            val useWatch = container.settings.useGarmin
            val useRing = container.settings.useOura
            // A row of the Oura Ring is not in the activities table: it is made again from the ring's stored days.
            val band = BandSource.ofRow(id)
            val bandWorkout: BandWorkout? = when {
                band == BandSource.OURA && useRing -> BandWorkouts.find(id, band, repo.listOuraDays(FIRST_DAY, LAST_DAY).flatMap { it.workouts })
                else -> null
            }
            val fromRing = bandWorkout != null
            val activity = bandWorkout?.let { BandWorkouts.toActivity(it, band!!) } ?: repo.getActivity(id)
            val maxHr = HeartZones.maxHr(container.settings.maxHr, repo.listActivities(limit = 10_000)).bpm
            val book = GearBook.decode(container.settings.gear)
            _detail.value = DetailState(
                loaded = true,
                activity = activity,
                fromRing = fromRing,
                around = activity?.let { ActivityAroundCalculator.load(repo, it, ZoneId.systemDefault(), useWatch, useRing) }?.takeIf { !it.isEmpty },
                shoe = activity?.takeIf { !fromRing }?.let { Gear.shoeFor(book, it) },
                shoeChoices = activity?.takeIf { !fromRing }?.let { Gear.choices(book, it) }.orEmpty(),
                shoeByHand = activity != null && activity.id in book.overrides,
                zones = activity?.let { HeartZones.activityZones(it, maxHr) },
                zonesMaxHr = maxHr,
                laps = activity?.let { BestEfforts.laps(it.track) }.orEmpty(),
                efforts = activity?.takeIf { it.sport == Sport.RUNNING }?.let { BestEfforts.efforts(it.track) }.orEmpty(),
                report = container.reports.cached("activity", id),
                route = activity?.let { routeFor(it) },
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
            body = repo.listBody(start, today),
            water = repo.listWater(start, today),
        )
    }

    /** Reads the days of the watch and of the ring, whichever are in use, for the Day tab of Insights. */
    private fun loadDays() = io {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val start = today.minusDays(DayInsights.HISTORY_DAYS.toLong())
        val useOura = container.settings.useOura
        // Someone who chose no device still has days from files and imports: those are read as the watch's.
        val useGarmin = container.settings.useGarmin || !useOura
        _days.value = DayState(
            loaded = true,
            today = today,
            garmin = if (useGarmin) {
                DayRecords.garmin(
                    start, today,
                    dailies = repo.listDaily(start, today),
                    sleeps = repo.listSleep(start, today),
                    readiness = container.stats.readinessSeries(start, today),
                    activities = repo.listActivities(start = start.atStartOfDay(zone).toInstant(), end = today.plusDays(1).atStartOfDay(zone).toInstant(), limit = 10_000),
                    zone = zone,
                )
            } else {
                null
            },
            oura = if (useOura) DayRecords.oura(start, today, repo.listOuraDays(start, today)) else null,
            whoop = null,
            hume = null,
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

    fun loadZones() = io {
        val today = LocalDate.now(ZoneOffset.UTC)
        val all = repo.listActivities(limit = 10_000)
        val from = today.minusDays((HeartZones.WEEKS * 7 - 1).toLong()).atStartOfDay().toInstant(ZoneOffset.UTC)
        // Listings leave the tracks out, and the zones are counted from the samples, so fetch the recent ones in full.
        val recent = all.filter { !it.startTime.isBefore(from) }.mapNotNull { repo.getActivity(it.id) }
        _zones.value = ZonesState(loaded = true, overview = HeartZones.overview(all, recent, container.settings.maxHr, today))
    }

    /** A change to the maximum heart rate: move it by some beats, or go back to the highest measured. */
    fun zonesAction(action: ZonesAction) {
        val current = _zones.value.overview.maxHr
        when (action) {
            is ZonesAction.ShiftMax -> {
                val base = if (current.bpm > 0) current.bpm else HeartZones.DEFAULT_MAX_HR
                container.settings.maxHr = (base + action.bpm).coerceIn(HeartZones.MIN_MAX_HR, HeartZones.MAX_MAX_HR)
            }
            ZonesAction.UseMeasured -> container.settings.maxHr = 0
        }
        loadZones()
        currentActivityId?.let { openActivity(it) }
    }

    /** The month the person paged to; null means the current one. */
    private var calendarMonth: java.time.YearMonth? = null

    fun loadCalendar() = io {
        val today = LocalDate.now(ZoneOffset.UTC)
        val all = repo.listActivities(limit = 10_000)
        val thisMonth = java.time.YearMonth.from(today)
        val first = minOf(Streaks.firstMonth(all) ?: thisMonth, thisMonth)
        val month = (calendarMonth ?: thisMonth).coerceIn(first, thisMonth)
        _calendar.value = CalendarState(
            loaded = true,
            view = Streaks.month(all, month),
            streaks = Streaks.compute(all, today),
            canGoPrevious = month > first,
            canGoNext = month < thisMonth,
        )
    }

    /** Pages the calendar by [months] (negative goes back). It stops at the first month with an activity and at this one. */
    fun shiftCalendar(months: Int) {
        calendarMonth = (calendarMonth ?: java.time.YearMonth.now(ZoneOffset.UTC)).plusMonths(months.toLong())
        loadCalendar()
    }

    /** Reads the tracks of the newest 300 activities with a distance one at a time and thins each to at most 500 positions. */
    fun loadMap() = io {
        val tracks = repo.listActivities(limit = 10_000).asSequence()
            .filter { it.distanceM > 0 }
            .take(MAP_ACTIVITIES)
            .mapNotNull { repo.getActivity(it.id)?.track }
            .map { track ->
                val positioned = track.filter { it.hasPosition }
                val stride = (positioned.size / MAP_POINTS).coerceAtLeast(1)
                positioned.filterIndexed { i, _ -> i % stride == 0 || i == positioned.lastIndex }
            }
            .toList()
        _map.value = MapState(loaded = true, grid = Heatmap.build(tracks))
    }

    /** The other runs on the same route as [activity], read with their tracks only when their distance is close enough. */
    private fun routeFor(activity: Activity): RouteHistory? {
        if (activity.distanceM <= 0 || !activity.hasGps) return null
        val candidates = repo.listActivities(sport = activity.sport, limit = 10_000).asSequence()
            .filter { it.id != activity.id && it.distanceM > 0 }
            .filter { kotlin.math.abs(it.distanceM - activity.distanceM) / maxOf(it.distanceM, activity.distanceM) <= RouteMatcher.DISTANCE_TOLERANCE }
            .take(ROUTE_CANDIDATES)
            .mapNotNull { repo.getActivity(it.id) }
            .toList()
        return RouteMatcher.history(activity, candidates)
    }

    /** The card of [year], or of the newest year with data when [year] is null or has none. */
    fun loadScore(year: Int? = null) = io {
        val activities = repo.listActivities(limit = 100_000)
        val dailies = repo.listDaily(LocalDate.of(2000, 1, 1), LocalDate.now().plusDays(1))
        val years = YearScoreCalculator.years(activities, dailies)
        val selected = year?.takeIf { it in years } ?: years.firstOrNull()
        _score.value = ScoreState(
            loaded = true,
            years = years,
            selected = selected,
            score = selected?.let { YearScoreCalculator.compute(activities, dailies, it) },
        )
    }

    /** The twelve months of [year], or of the newest year with data when [year] is null or has none. */
    fun loadYear(year: Int? = null) = io {
        val activities = repo.listActivities(limit = 100_000)
        val dailies = repo.listDaily(LocalDate.of(2000, 1, 1), LocalDate.now().plusDays(1))
        val sleeps = repo.listSleep(LocalDate.of(2000, 1, 1), LocalDate.now().plusDays(1))
        val years = YearOverviewCalculator.years(dailies, sleeps, activities)
        val selected = year?.takeIf { it in years } ?: years.firstOrNull()
        _year.value = YearState(
            loaded = true,
            years = years,
            selected = selected,
            overview = selected?.let { YearOverviewCalculator.compute(dailies, sleeps, activities, it) },
        )
    }

    fun loadJournal() = io {
        val book = JournalBook.decode(container.settings.journal)
        val first = book.days.keys.minOrNull()
        val effects = if (first == null) {
            emptyList()
        } else {
            val end = LocalDate.now().plusDays(1)
            JournalInsights.effects(book, repo.listDaily(first, end), repo.listSleep(first, end))
        }
        _journal.value = JournalState(loaded = true, loggedDays = book.days.size, effects = effects)
    }

    /** Mark or unmark [tag] on [date]. The home screen shows the change at once; the Journal tab follows. */
    fun toggleJournal(date: LocalDate, tag: JournalTag) {
        val settings = container.settings
        val next = JournalBook.decode(settings.journal).toggle(date, tag)
        settings.journal = next.encode()
        _home.value = _home.value.copy(journal = next)
        loadJournal()
    }

    fun loadGear() = io {
        val book = GearBook.decode(container.settings.gear)
        _gear.value = GearState(loaded = true, usage = Gear.usage(book, repo.listActivities(limit = 10_000)))
    }

    /** A change to the shoes: add, retire, bring back, move the limit, remove, or put an activity on a pair. */
    fun gearAction(action: GearAction) {
        val settings = container.settings
        val book = GearBook.decode(settings.gear)
        val today = LocalDate.now(ZoneOffset.UTC)
        val next = when (action) {
            is GearAction.Add -> Gear.add(book, action.name, action.sport, today, action.countEarlier, java.time.Instant.now())
            is GearAction.Retire -> Gear.retire(book, action.id, today)
            is GearAction.Resume -> Gear.resume(book, action.id)
            is GearAction.MoveLimit -> Gear.moveLimit(book, action.id, action.km)
            is GearAction.Remove -> Gear.remove(book, action.id)
            is GearAction.Assign -> Gear.assign(book, action.activityId, action.shoeId)
        }
        settings.gear = next.encode()
        loadGear()
        currentActivityId?.let { openActivity(it) }
    }

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
        val runs = repo.listActivities(sport = Sport.RUNNING, limit = 10_000)
        val maxHr = HeartZones.maxHr(container.settings.maxHr, repo.listActivities(limit = 10_000)).bpm
        _plan.value = PlanState(
            loaded = true,
            plan = built,
            progress = progress,
            baselineKm = baseline,
            today = today,
            race = raceState(today),
            shapes = RaceDistance.entries.mapNotNull { RaceShapeCalculator.compute(runs, it, today) },
            vo2 = Vo2Max.summary(runs, maxHr, today),
            season = seasonFor(today),
            ownWorkouts = WorkoutBook.decode(container.settings.workouts).workouts,
        )
    }

    private fun seasonFor(today: LocalDate): SeasonPlan? =
        RaceGoal.decode(container.settings.raceGoal)?.let { SeasonPlanner.plan(it, today) }

    private fun raceState(today: LocalDate): RaceState =
        Races.state(_reports.value.efforts, RaceGoal.decode(container.settings.raceGoal), today)

    /** Works out the race predictions and outlook again, after the efforts or the goal changed. */
    private fun publishRace() {
        val today = LocalDate.now(ZoneOffset.UTC)
        _plan.update { it.copy(race = raceState(today), season = seasonFor(today)) }
    }

    /** A change to the race goal: start one, move its date by weeks, move its target by seconds, or remove it. */
    fun raceAction(action: RaceAction) {
        val today = LocalDate.now(ZoneOffset.UTC)
        val current = RaceGoal.decode(container.settings.raceGoal)
        val next = when (action) {
            is RaceAction.Start -> RaceGoal.starter(action.distance, today, _plan.value.race.predictions)
            is RaceAction.ShiftDate -> current?.copy(date = current.date.plusWeeks(action.weeks))
            is RaceAction.ShiftTarget -> current?.copy(
                targetSeconds = (current.targetSeconds + action.seconds).coerceIn(RaceGoal.MIN_SECONDS, RaceGoal.MAX_SECONDS),
            )
            RaceAction.Clear -> null
        }
        container.settings.raceGoal = next?.encode().orEmpty()
        publishRace()
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
        publishRace()
    }

    // ------------------------------------------------------------------------------- actions

    /** The weight the person typed on Health; storing it reloads the screens (the card and the trend). */
    fun saveBody(kg: Double, fatPct: Double?) = io {
        repo.upsertBody(BodyEntry(LocalDate.now(), Math.round(kg * 1000).toInt(), fatPct))
        container.dataChanged.tryEmit(Unit)
    }

    /** Adds to today's water total (v1.1.0); the card reads it back from the database, so taps add up. */
    fun addWater(millilitres: Int) = io {
        val today = LocalDate.now()
        val already = repo.listWater(today, today).firstOrNull()?.millilitres ?: 0
        repo.upsertWater(WaterEntry(today, (already + millilitres).coerceAtLeast(0)))
        container.dataChanged.tryEmit(Unit)
    }

    /** v1.3.0: a workout the person typed in. It is stored like any other activity, with its own source. */
    fun addManualActivity(manual: ManualActivity) = io {
        val now = Instant.now()
        repo.upsertActivity(manual.toActivity(ZoneId.systemDefault(), ManualActivity.id(now)))
        container.dataChanged.tryEmit(Unit)
    }

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

    /** Stops a running import after the file it is on. What came in so far stays. */
    fun cancelImport() = importCancelled.set(true)

    /**
     * Imports what the person picked or shared: FIT, GPX and TCX files, gzip files and whole ZIP archives (the exports
     * of Garmin Connect and Strava, also a ZIP inside a ZIP), see [ArchiveImport]. It runs in the background with a
     * progress line and a report in Settings, and one line in the sync log.
     */
    fun importUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        if (_import.value.running) {
            tell(text(R.string.import_busy))
            return
        }
        importCancelled.set(false)
        _import.value = ImportUi(running = true)
        io {
            val resolver = getApplication<Application>().contentResolver
            var total: ArchiveImport.Report? = null
            try {
                for (uri in uris) {
                    if (importCancelled.get()) break
                    val name = displayName(uri)
                    val done = total
                    val report = try {
                        val stream = resolver.openInputStream(uri) ?: throw IOException("unreadable")
                        stream.use {
                            ArchiveImport.import(
                                it, name, repo,
                                transaction = { block -> repo.inTransaction(block) },
                                shouldStop = importCancelled::get,
                                onProgress = { p ->
                                    _import.value = ImportUi(
                                        running = true,
                                        entries = (done?.entries ?: 0) + p.entries,
                                        imported = (done?.imported ?: 0) + p.imported,
                                        alreadyThere = (done?.alreadyThere ?: 0) + p.alreadyThere,
                                    )
                                },
                            )
                        }
                    } catch (e: IOException) {
                        ArchiveImport.Report(0, 0, 0, 0, listOf(ArchiveImport.Failure(name, text(R.string.import_unreadable))), 1, null, null, false, null)
                    }
                    total = if (total == null) report else total + report
                    if (report.stopped != null) break
                }
            } finally {
                _import.value = ImportUi(report = total)
            }
            val report = total ?: return@io
            val failedOnly = report.imported == 0 && report.failureCount > 0
            container.syncLog.record(
                SyncLogEntry(
                    Instant.now(), SyncLog.SOURCE_ARCHIVE, ok = !failedOnly, added = report.imported,
                    detail = report.failures.firstOrNull()?.let { "${it.name}: ${it.reason}" }.orEmpty(),
                ),
            )
            tell(text(R.string.import_summary, report.imported, report.alreadyThere, report.skipped, report.failureCount))
            if (report.imported > 0) refreshAll()
        }
    }

    /** The name the file has in the file manager; the last part of the address is only a fallback ("msf:1234"). */
    private fun displayName(uri: Uri): String {
        val fromProvider = runCatching {
            getApplication<Application>().contentResolver
                .query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
        return fromProvider ?: uri.lastPathSegment.orEmpty()
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
        val bytes = Backup.write(repo, userData = container.settings.userData())
        val out = getApplication<Application>().contentResolver.openOutputStream(target) ?: throw IOException("unwritable")
        out.use { it.write(bytes) }
        tell(text(R.string.backup_saved, backupFileName()))
    }

    /** One ZIP with CSV, FIT, GPX and the backup file (no keys), written to the chosen place. */
    fun exportAll(target: Uri) = io {
        val out = getApplication<Application>().contentResolver.openOutputStream(target) ?: throw IOException("unwritable")
        out.use { ExportAll.write(repo, container.settings.userData(), java.time.Instant.now(), it) }
        tell(text(R.string.export_all_saved, exportAllFileName()))
    }

    fun exportAllFileName(): String = "openconnect-export-" + LocalDate.now() + ".zip"

    /** Reads a backup file and merges it in. A file that is not a backup, or is damaged, changes nothing. */
    fun restore(source: Uri) = io {
        val bytes = getApplication<Application>().contentResolver.openInputStream(source)?.use { readLimited(it, BACKUP_MAX_BYTES) } ?: throw IOException("unreadable")
        try {
            val report = Backup.restore(bytes, repo, container.settings.userData())
            // Only after the whole file was read and checked: the journal, shoes, plan and goals of the backup are added
            // to this phone's own, never over them.
            container.settings.applyUserData(report.userData)
            _goals.value = container.settings.weeklyGoals
            tell(text(R.string.backup_restored, report.activities, report.days + report.nights))
            refreshAll()
        } catch (e: BackupException) {
            tell(text(R.string.backup_refused, e.message ?: ""))
        }
    }

    /**
     * Copies the workouts of the next seven days of the training plan, and the work-outs made in the app, to a Garmin on
     * the USB cable, into GARMIN/NewFiles. Tempo and interval steps get a pace window from the predicted 10 km pace when
     * there is one.
     */
    fun sendPlanToWatch() = io {
        val state = _plan.value
        val sessions = state.plan?.let { Workouts.upcoming(it, LocalDate.now(ZoneOffset.UTC)) } ?: emptyList()
        val message = if (sessions.isEmpty() && state.ownWorkouts.isEmpty()) {
            text(R.string.workouts_none)
        } else {
            try {
                val created = java.time.Instant.now()
                val files = Workouts.files(sessions, TrainingPaces.from(state.race.predictions), created) +
                    state.ownWorkouts.map { it.fitFile(created) }
                val watch = container.usb.attached().firstOrNull()
                    ?: throw nl.tufein.openconnect.core.watch.WatchWriteException(nl.tufein.openconnect.core.watch.WatchWriteException.Kind.NO_USB_WATCH)
                val result = container.usb.open(watch).use { Workouts.send(it.source, files) }
                text(R.string.workouts_done, result.sent.size, result.alreadyThere.size)
            } catch (e: nl.tufein.openconnect.core.watch.WatchWriteException) {
                text(if (e.kind == nl.tufein.openconnect.core.watch.WatchWriteException.Kind.NO_USB_WATCH) R.string.watch_no_usb else R.string.watch_cannot_write)
            } catch (e: nl.tufein.openconnect.watch.WatchLinkException) {
                text(R.string.watch_cannot_write)
            }
        }
        tell(message)
    }

    /** Adds a work-out made in the app to the ones that go to the watch with [sendPlanToWatch]; it replaces one with the same id. */
    fun saveOwnWorkout(workout: OwnWorkout) = io {
        val book = WorkoutBook.decode(container.settings.workouts).plus(workout)
        container.settings.workouts = book.encode()
        loadPlan()
    }

    /** Removes a work-out made in the app; one that was already sent to the watch stays there. */
    fun deleteOwnWorkout(id: String) = io {
        container.settings.workouts = WorkoutBook.decode(container.settings.workouts).without(id).encode()
        loadPlan()
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

/** The file import: [running] with how far it is, or the [report] of the last one. */
data class ImportUi(
    val running: Boolean = false,
    val entries: Int = 0,
    val imported: Int = 0,
    val alreadyThere: Int = 0,
    val report: ArchiveImport.Report? = null,
)

/** The Calendar tab: a month with its activities, and the streaks. [view] is null until loaded. */
data class CalendarState(
    val loaded: Boolean = false,
    val view: MonthView? = null,
    val streaks: StreakSummary = StreakSummary(StreakRun.NONE, StreakRun.NONE, StreakRun.NONE, StreakRun.NONE, false),
    val canGoPrevious: Boolean = false,
    val canGoNext: Boolean = false,
)

/** The Shoes tab: every pair with what it has done so far. */
data class ScoreState(val loaded: Boolean = false, val years: List<Int> = emptyList(), val selected: Int? = null, val score: YearScore? = null)

data class YearState(val loaded: Boolean = false, val years: List<Int> = emptyList(), val selected: Int? = null, val overview: YearOverview? = null)

data class MapState(val loaded: Boolean = false, val grid: HeatGrid? = null)

data class JournalState(val loaded: Boolean = false, val loggedDays: Int = 0, val effects: List<TagEffect> = emptyList())

data class GearState(val loaded: Boolean = false, val usage: List<ShoeUsage> = emptyList())

/** The Zones tab: nothing to show until [loaded]; without any heart rate the overview has no zones. */
data class ZonesState(
    val loaded: Boolean = false,
    val overview: ZoneOverview = ZoneOverview(MaxHr(0, MaxHrSource.NONE), emptyList(), emptyList(), null, ZoneVerdict.NO_DATA),
)

/** The training plan on the Plan tab: nothing chosen yet ([plan] is null) or the plan and how far it has got. */
data class PlanState(
    val loaded: Boolean = false,
    val plan: TrainingPlan? = null,
    val progress: PlanProgress? = null,
    /** What you run in a week now, which the next plan starts from. */
    val baselineKm: Double = 0.0,
    val today: LocalDate = LocalDate.now(ZoneOffset.UTC),
    /** Predicted race times and the race goal, if there is one. */
    val race: RaceState = RaceState(),
    /** How well the last six weeks of running fit each distance; empty without a run in that time. */
    val shapes: List<RaceShape> = emptyList(),
    /** VO2max worked out from the pace and heart rate of recent runs; null with fewer than two such runs. */
    val vo2: Vo2Summary? = null,
    /** The phases from now to the race goal; null without a goal or when the race is not in the future. */
    val season: SeasonPlan? = null,
    /** Work-outs made in the app; they go to the watch together with the plan. */
    val ownWorkouts: List<OwnWorkout> = emptyList(),
)

/** Whether the app is locked right now (and whether the lock is on at all). */
data class LockUi(val enabled: Boolean = false, val locked: Boolean = false)
