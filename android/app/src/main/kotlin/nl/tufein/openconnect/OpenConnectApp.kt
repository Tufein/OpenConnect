package nl.tufein.openconnect

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import nl.tufein.openconnect.ai.AiController
import nl.tufein.openconnect.ai.AiSettings
import nl.tufein.openconnect.ai.CoachController
import nl.tufein.openconnect.core.StatsService
import nl.tufein.openconnect.core.reports.CoachFacts
import nl.tufein.openconnect.core.reports.HttpTransport
import nl.tufein.openconnect.core.reports.ReportService
import nl.tufein.openconnect.core.reports.UrlConnectionTransport
import nl.tufein.openconnect.sync.BackgroundSync
import nl.tufein.openconnect.core.health.HealthConnectGateway
import nl.tufein.openconnect.core.health.NoHealthConnect
import nl.tufein.openconnect.health.AndroidHealthConnectGateway
import nl.tufein.openconnect.health.HealthConnectController
import nl.tufein.openconnect.health.HealthSettings
import nl.tufein.openconnect.notify.AndroidInstalledApps
import nl.tufein.openconnect.notify.AndroidNudgeNotifier
import nl.tufein.openconnect.notify.ForegroundForwarding
import nl.tufein.openconnect.notify.ForwardAccess
import nl.tufein.openconnect.notify.ForwardStore
import nl.tufein.openconnect.notify.ForwardingKeeper
import nl.tufein.openconnect.notify.InstalledApps
import nl.tufein.openconnect.notify.NoForwardingKeeper
import nl.tufein.openconnect.notify.NoInstalledApps
import nl.tufein.openconnect.notify.NotificationForwarder
import nl.tufein.openconnect.notify.NudgeNotifier
import nl.tufein.openconnect.notify.PhoneNotificationListener
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
import nl.tufein.openconnect.i18n.AppLocale
import nl.tufein.openconnect.core.garmin.GarminHttp
import nl.tufein.openconnect.core.garmin.UrlConnectionGarminHttp
import nl.tufein.openconnect.core.updates.UpdateHttp
import nl.tufein.openconnect.core.updates.UrlConnectionUpdateHttp
import nl.tufein.openconnect.updates.UpdateController
import java.io.File
import nl.tufein.openconnect.garmin.GarminController
import nl.tufein.openconnect.garmin.GarminSettings
import nl.tufein.openconnect.watch.AndroidGarminBleLink
import nl.tufein.openconnect.watch.BleHeartRateLink
import nl.tufein.openconnect.watch.FolderLink
import nl.tufein.openconnect.watch.ForegroundLiveRecording
import nl.tufein.openconnect.watch.GarminBleLink
import nl.tufein.openconnect.watch.HeartRateLink
import nl.tufein.openconnect.watch.LiveWatchRegistry
import nl.tufein.openconnect.watch.LiveRecordingKeeper
import nl.tufein.openconnect.watch.NoGarminBleLink
import nl.tufein.openconnect.watch.NoLiveRecordingKeeper
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
    val usb: WatchLink,
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
    /** Bumped by the Bluetooth link when the watch reports a new charge, so the battery card can re-read it. */
    val batteryChanges: MutableStateFlow<Int> = MutableStateFlow(0),
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
    /** The phone's Health Connect. Tests pass a fake; the app passes the real one. */
    healthConnect: HealthConnectGateway = NoHealthConnect,
    healthSettings: HealthSettings = settings.health,
    /** The network to Oura. Tests pass a fake. */
    ouraHttp: nl.tufein.openconnect.core.oura.OuraHttp = nl.tufein.openconnect.core.oura.UrlConnectionOuraHttp(),
    ouraSettings: nl.tufein.openconnect.oura.OuraSettings = settings.oura,
    /** Starts and stops the Oura job. Tests pass a fake; the app passes WorkManager. */
    ouraSync: BackgroundSync = BackgroundSync { },
    /** Starts and stops the Health Connect fill job. Tests pass a fake; the app passes WorkManager. */
    healthSync: BackgroundSync = BackgroundSync { },
    /** Keeps a live heart-rate recording alive with the screen off. Tests pass a fake; the app posts a notification. */
    keeper: LiveRecordingKeeper = NoLiveRecordingKeeper,
    /** What the person chose for notifications on the watch. */
    forwardStore: ForwardStore = settings.forwarding,
    /** Keeps the watch connection alive while notifications are sent. Tests pass a fake; the app shows a notification. */
    forwardKeeper: ForwardingKeeper = NoForwardingKeeper,
    /** Whether Android's notification access is given. Tests pass a fake; the app asks Android. */
    forwardAccess: () -> Boolean = { false },
    /** Whether the Bluetooth permission is given. Tests pass a fake; the app asks Android. */
    forwardBluetooth: () -> Boolean = { false },
    forwardScreenOn: () -> Boolean = { true },
    /** The line the watch shows when the text is hidden (translated by the app). */
    forwardHiddenBody: () -> String = { "New notification" },
    /** Swipes a notification away on the phone when it was dismissed on the watch. */
    forwardDismiss: (key: String) -> Unit = {},
    /** The apps to choose from. Tests pass a fake. */
    val installedApps: InstalledApps = NoInstalledApps,
    /** Where a notification connection and a sync find out about each other; the app shares it with the Bluetooth link. */
    val liveRegistry: LiveWatchRegistry = LiveWatchRegistry(),
    /** The name of this app's package, whose notifications are never sent on. */
    forwardOwnPackage: String = "nl.tufein.openconnect",
    /** What forwarding did, line by line (the "What is happening?" part of the card). The app shares it with the Bluetooth link. */
    val forwardTrail: nl.tufein.openconnect.notify.ForwardTrail = nl.tufein.openconnect.notify.ForwardTrail(),
    /** Asks Android to start the notification listener again. Tests pass a fake. */
    forwardRebind: () -> Unit = {},
    /** Whether Android lets the app show notifications. Tests pass a fake; the app asks Android. */
    forwardNotificationsAllowed: () -> Boolean = { true },
    /** Where a verified update APK is kept until Android installs it. The app uses its own cache directory. */
    updateDir: File = File(System.getProperty("java.io.tmpdir"), "openconnect-updates"),
    /** The network to GitHub's releases. Tests pass a fake. */
    updateHttp: UpdateHttp = UrlConnectionUpdateHttp(),
    /** Whether a downloaded APK carries the installed app's signature. Tests pass a fake; the app asks Android. */
    updateSigner: (File) -> Boolean = { true },
) {
    val stats = StatsService(repository)

    /** When the app is locked. Android's own check (fingerprint, face, screen lock) is in [nl.tufein.openconnect.lock]. */
    val lockGate = nl.tufein.openconnect.core.LockGate(settings.appLock)

    fun setAppLock(on: Boolean) {
        settings.appLock = on
        lockGate.enabled = on
        if (on) lockGate.markUnlocked() // the person has just passed the check to switch it on
    }

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

    /** The Health Connect card. */
    val health = HealthConnectController(
        repo = repository,
        gateway = healthConnect,
        settings = healthSettings,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        io = Dispatchers.IO,
        onDataChanged = { dataChanged.tryEmit(Unit) },
        ledger = settings.healthLedger,
        onSynced = syncLog::record,
        onBackgroundWanted = healthSync::set,
    )

    init {
        // After every sync that wrote something, Health Connect gets the new activities (only when the switch is on).
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { dataChanged.collect { health.autoExport() } }
        nudgeSync.set(nudgeState.enabled) // a switch left on keeps its job after an update or reboot
        // Only the real app has widgets; the summary is worked out only when one is on the home screen.
        widgetRefresh?.let { refresh ->
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                dataChanged.collect { runCatching { refresh { widgetSummary(LocalDate.now()) } } }
            }
        }
    }

    /** The in-app updater: GitHub releases, checksum-verified APK in the cache, then Android's installer. */
    val updates = UpdateController(
        dir = updateDir,
        currentVersion = BuildConfig.VERSION_NAME,
        http = updateHttp,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        io = Dispatchers.IO,
        sameSigner = updateSigner,
    )

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
        keeper = keeper,
    )

    /** Sends phone notifications to the watch (the card on the Watch screen). */
    val forwarding = NotificationForwarder(
        store = forwardStore,
        memory = settings.garminWatch,
        link = garminBle,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        io = Dispatchers.IO,
        ownPackage = forwardOwnPackage,
        accessGranted = forwardAccess,
        screenOn = forwardScreenOn,
        bluetoothAllowed = forwardBluetooth,
        keeper = forwardKeeper,
        hiddenBody = forwardHiddenBody,
        dismissOnPhone = forwardDismiss,
        syncBusy = { liveRegistry.syncBusy },
        trail = forwardTrail,
        rebindListener = forwardRebind,
        notificationsAllowed = forwardNotificationsAllowed,
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
        retry = nl.tufein.openconnect.core.RetryPolicy.standard(),
    )

    /** The Oura Ring link. */
    val oura = nl.tufein.openconnect.oura.OuraController(
        settings = ouraSettings,
        repo = repository,
        http = ouraHttp,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        io = Dispatchers.IO,
        onDataChanged = { dataChanged.tryEmit(Unit) },
        onSynced = syncLog::record,
        // The background job runs only for someone who chose the Oura Ring and logged in.
        onBackgroundWanted = { wanted -> ouraSync.set(wanted && settings.useOura) },
        retry = nl.tufein.openconnect.core.RetryPolicy.standard(),
    )

    private val ouraJob = ouraSync

    /** Starts or stops the Oura background job after the choice of devices changed. */
    fun applySources() {
        ouraJob.set(settings.useOura && oura.state.value.connected)
    }

    /** The keys, models and choice of AI provider (Claude, Gemini, or the offline reports). */
    val ai = AiController(
        settings = aiSettings,
        transport = aiTransport,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        io = Dispatchers.IO,
    )

    /** The latest bounded plan digest, replaced after every serialized plan reload. */
    private val currentCoachFacts = java.util.concurrent.atomic.AtomicReference(CoachFacts())

    /** The read-only conversation on the Coach screen. */
    val coach = CoachController(
        ai = ai,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        io = Dispatchers.IO,
        facts = currentCoachFacts::get,
    )

    /** Publishes only week/proposal facts; activities, journal and own workouts never enter the coach digest. */
    fun updateCoachFacts(facts: CoachFacts) {
        currentCoachFacts.set(facts)
    }
}

class OpenConnectApp : Application() {
    /** Tests set this before the first screen starts to put fake USB and Bluetooth in the app. */
    var containerOverride: AppContainer? = null

    /** Tests put a fake fingerprint check here; the real one needs a phone with a screen lock. */
    var unlockerOverride: ((androidx.activity.ComponentActivity) -> nl.tufein.openconnect.lock.Unlocker)? = null

    val container: AppContainer by lazy {
        containerOverride ?: SettingsStore(this).let { settingsStore ->
            val registry = LiveWatchRegistry()
            val apps = AndroidInstalledApps(this)
            val trail = nl.tufein.openconnect.notify.ForwardTrail()
            val batteryChanges = MutableStateFlow(0)
            AppContainer(
            SqliteRepository(this),
            settingsStore,
            UsbWatchLink(this),
            BleHeartRateLink(this),
            folder = SafFolderLink(this),
            backgroundSync = WorkManagerBackgroundSync(this),
            garminBle = AndroidGarminBleLink(
                this,
                archiveAfterRead = { settingsStore.garminBle.archiveAfterRead },
                registry = registry,
                diagnostics = trail::add,
                notificationOptions = {
                    nl.tufein.openconnect.core.watch.ble.NotificationOptions(
                        dismissLabel = getString(R.string.forward_dismiss),
                        appName = { pkg -> apps.labelOf(pkg).takeIf { it != pkg } },
                    )
                },
                onBattery = { level ->
                    settingsStore.watchBattery = level
                    settingsStore.watchBatteryAt = System.currentTimeMillis()
                    batteryChanges.value++
                },
            ),
            batteryChanges = batteryChanges,
            bleBackgroundSync = WorkManagerBleBackgroundSync(this),
            nudgeNotifier = AndroidNudgeNotifier(this),
            nudgeSync = WorkManagerNudgeSync(this),
            healthConnect = AndroidHealthConnectGateway(this),
            widgetRefresh = { summary -> HomeWidget.refresh(this, summary) },
            ouraSync = nl.tufein.openconnect.sync.WorkManagerOuraSync(this),
            healthSync = nl.tufein.openconnect.sync.WorkManagerHealthSync(this),
            keeper = ForegroundLiveRecording(this),
            forwardKeeper = ForegroundForwarding(this, trail::add),
            forwardAccess = { ForwardAccess.hasNotificationAccess(this) },
            forwardBluetooth = { ForwardAccess.bluetoothAllowed(this) },
            forwardScreenOn = { ForwardAccess.screenOn(this) },
            forwardHiddenBody = { AppLocale.wrap(this, SettingsStore(this).appLanguage).getString(R.string.forward_hidden_body) },
            forwardDismiss = PhoneNotificationListener::cancel,
            installedApps = apps,
            liveRegistry = registry,
            forwardOwnPackage = packageName,
            forwardTrail = trail,
            forwardRebind = { PhoneNotificationListener.rebind(this) },
            forwardNotificationsAllowed = { ForwardAccess.notificationsAllowed(this) },
            updateDir = File(cacheDir, "updates"),
            updateSigner = { apk -> nl.tufein.openconnect.updates.ApkSigners.sameAsInstalled(this, apk) },
        ) }
    }

    /**
     * Android 13 and newer keep a language for this app alone (Settings > Apps > OpenConnect > Language). That choice
     * is the one to keep: it is stored and applied, so the widget, notifications and the next start agree with it.
     * Without one, the language stored in Settings is applied. Only a small [SettingsStore] is read here on purpose:
     * touching [container] this early would build the database, the Bluetooth link and every controller before the
     * first screen even exists.
     */
    override fun onCreate() {
        super.onCreate()
        reconcileAppLanguage()
    }

    private var languageReconciled = false

    private fun reconcileAppLanguage() {
        if (languageReconciled) return
        languageReconciled = true
        try {
            val settings = SettingsStore(this)
            val installed = AppCompatDelegate.getApplicationLocales()
            // A per-app locale such as "de-AT" counts as German. One we have no texts for is left to the stored choice.
            val chosen = if (installed.isEmpty) null else AppLocale.supportedPrefix(installed.toLanguageTags())
            if (chosen != null) {
                settings.appLanguage = chosen
                AppLocale.apply(chosen)
            } else {
                AppLocale.apply(settings.appLanguage)
            }
        } catch (_: Throwable) {
            // A language that cannot be applied must never keep the app from starting.
        }
    }
}
