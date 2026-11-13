package nl.tufein.openconnect

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import nl.tufein.openconnect.ai.AiController
import nl.tufein.openconnect.ai.AiSettings
import nl.tufein.openconnect.ai.CoachController
import nl.tufein.openconnect.core.StatsService
import nl.tufein.openconnect.core.reports.HttpTransport
import nl.tufein.openconnect.core.reports.ReportService
import nl.tufein.openconnect.core.reports.UrlConnectionTransport
import nl.tufein.openconnect.sync.BackgroundSync
import nl.tufein.openconnect.notify.AndroidNudgeNotifier
import nl.tufein.openconnect.notify.NudgeNotifier
import nl.tufein.openconnect.notify.NudgeService
import nl.tufein.openconnect.notify.NudgeSettings
import nl.tufein.openconnect.notify.WorkManagerNudgeSync
import nl.tufein.openconnect.widget.HomeWidget
import nl.tufein.openconnect.core.WidgetSummary
import java.time.LocalDate
import kotlinx.coroutines.launch
import nl.tufein.openconnect.sync.SyncLogKeeper
import nl.tufein.openconnect.sync.WorkManagerBackgroundSync
import nl.tufein.openconnect.sync.WorkManagerBleBackgroundSync
import nl.tufein.openconnect.data.SettingsStore
import nl.tufein.openconnect.data.SqliteRepository
import nl.tufein.openconnect.core.garmin.GarminHttp
import nl.tufein.openconnect.core.garmin.UrlConnectionGarminHttp
import nl.tufein.openconnect.garmin.GarminController
import nl.tufein.openconnect.garmin.GarminSettings
import nl.tufein.openconnect.watch.AndroidGarminBleLink
import nl.tufein.openconnect.watch.BleHeartRateLink
import nl.tufein.openconnect.watch.FolderLink
import nl.tufein.openconnect.watch.GarminBleLink
import nl.tufein.openconnect.watch.HeartRateLink
import nl.tufein.openconnect.watch.NoGarminBleLink
import nl.tufein.openconnect.watch.SafFolderLink
import nl.tufein.openconnect.watch.UsbWatchLink
import nl.tufein.openconnect.watch.WatchController
import nl.tufein.openconnect.watch.WatchLink
import nl.tufein.openconnect.watch.WatchLinkException
import java.time.Instant

/**
 * Wires the services once per process. The phone's USB, folders, Bluetooth and the network to Garmin are passed in
 * so tests can replace them.
 */
class AppContainer(
    val repository: SqliteRepository,
    val settings: SettingsStore,
    usb: WatchLink,
    ble: HeartRateLink,
    folder: FolderLink = FolderLink { throw WatchLinkException(WatchLinkException.Reason.CANNOT_OPEN) },
    garminHttp: GarminHttp = UrlConnectionGarminHttp(),
    garminSettings: GarminSettings = settings.garmin,
    garminNow: () -> Instant = { Instant.now() },
    /** The network to Claude and Gemini. Tests pass a fake. */
    aiTransport: HttpTransport = UrlConnectionTransport(),
    aiSettings: AiSettings = settings,
    /** Starts and stops the periodic Garmin sync job. Tests pass a fake; the app passes WorkManager. */
    backgroundSync: BackgroundSync = BackgroundSync { },
    /** Garmin's own Bluetooth protocol. Tests pass a fake; the app passes [AndroidGarminBleLink]. */
    garminBle: GarminBleLink = NoGarminBleLink,
    /** Starts and stops the experimental background Bluetooth job. Tests pass a fake; the app passes WorkManager. */
    bleBackgroundSync: BackgroundSync = BackgroundSync { },
    bleOptions: nl.tufein.openconnect.watch.GarminBleOptions = settings.garminBle,
    /** Shows a nudge. Tests pass a fake; the app posts an Android notification. */
    nudgeNotifier: NudgeNotifier = NudgeNotifier { },
    /** Starts and stops the job that checks for nudges. Tests pass a fake; the app passes WorkManager. */
    private val nudgeSync: BackgroundSync = BackgroundSync { },
    nudgeSettings: NudgeSettings = settings.nudges,
    /** Redraws the home-screen widgets after new data arrived. */
    private val widgetRefresh: ((summary: () -> WidgetSummary) -> Unit)? = null,
) {
    val stats = StatsService(repository)

    /** The notifications that are due: the app's job calls [NudgeService.check]. */
    val nudges = NudgeService(repository, stats, nudgeSettings, nudgeNotifier)

    private val nudgeState = nudgeSettings

    fun setNudges(on: Boolean) {
        nudgeState.enabled = on
        nudgeSync.set(on)
    }

    /** What the home-screen widget shows for [today]. */
    fun widgetSummary(today: LocalDate): WidgetSummary = WidgetSummary.of(repository, stats, settings.weeklyGoals, today)

    /** What the last syncs did, for the Settings screen. */
    val syncLog = SyncLogKeeper(load = { settings.syncLogText }, save = { settings.syncLogText = it })
    val reports = ReportService(repository, stats)

    /** Emits whenever the watch screen wrote data, so the other screens reload. */
    val dataChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    init {
        nudgeSync.set(nudgeState.enabled) // a switch left on keeps its job after an update or reboot
        // Only the real app has widgets; the summary is worked out only when one is on the home screen.
        widgetRefresh?.let { refresh ->
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                dataChanged.collect { runCatching { refresh { widgetSummary(LocalDate.now()) } } }
            }
        }
    }

    val watch = WatchController(
        repo = repository,
        usb = usb,
        ble = ble,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        io = Dispatchers.IO,
        onDataChanged = { dataChanged.tryEmit(Unit) },
        folder = folder,
        garminBle = garminBle,
        garminMemory = settings.garminWatch,
        onSynced = syncLog::record,
        garminOptions = bleOptions,
        onBleBackgroundWanted = bleBackgroundSync::set,
    )

    val garmin = GarminController(
        settings = garminSettings,
        repo = repository,
        http = garminHttp,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        io = Dispatchers.IO,
        now = garminNow,
        onDataChanged = { dataChanged.tryEmit(Unit) },
        onBackgroundWanted = backgroundSync::set,
        onSynced = syncLog::record,
    )

    /** The keys, models and choice of AI provider (Claude, Gemini, or the offline reports). */
    val ai = AiController(
        settings = aiSettings,
        transport = aiTransport,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        io = Dispatchers.IO,
    )

    /** The conversation on the Coach screen. */
    val coach = CoachController(
        repo = repository,
        stats = stats,
        ai = ai,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        io = Dispatchers.IO,
    )
}

class OpenConnectApp : Application() {
    /** Tests set this before the first screen starts to put fake USB and Bluetooth in the app. */
    var containerOverride: AppContainer? = null

    val container: AppContainer by lazy {
        containerOverride ?: SettingsStore(this).let { settingsStore -> AppContainer(
            SqliteRepository(this),
            settingsStore,
            UsbWatchLink(this),
            BleHeartRateLink(this),
            folder = SafFolderLink(this),
            backgroundSync = WorkManagerBackgroundSync(this),
            garminBle = AndroidGarminBleLink(this, archiveAfterRead = { settingsStore.garminBle.archiveAfterRead }),
            bleBackgroundSync = WorkManagerBleBackgroundSync(this),
            nudgeNotifier = AndroidNudgeNotifier(this),
            nudgeSync = WorkManagerNudgeSync(this),
            widgetRefresh = { summary -> HomeWidget.refresh(this, summary) },
        ) }
    }
}
