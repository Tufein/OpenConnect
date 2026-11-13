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
import nl.tufein.openconnect.sync.WorkManagerBackgroundSync
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
) {
    val stats = StatsService(repository)
    val reports = ReportService(repository, stats)

    /** Emits whenever the watch screen wrote data, so the other screens reload. */
    val dataChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

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
        containerOverride ?: AppContainer(
            SqliteRepository(this),
            SettingsStore(this),
            UsbWatchLink(this),
            BleHeartRateLink(this),
            folder = SafFolderLink(this),
            backgroundSync = WorkManagerBackgroundSync(this),
            garminBle = AndroidGarminBleLink(this),
        )
    }
}
