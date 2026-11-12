package nl.tufein.openconnect

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import nl.tufein.openconnect.core.StatsService
import nl.tufein.openconnect.core.reports.ReportService
import nl.tufein.openconnect.data.SettingsStore
import nl.tufein.openconnect.data.SqliteRepository
import nl.tufein.openconnect.core.garmin.GarminHttp
import nl.tufein.openconnect.core.garmin.UrlConnectionGarminHttp
import nl.tufein.openconnect.garmin.GarminController
import nl.tufein.openconnect.garmin.GarminSettings
import nl.tufein.openconnect.watch.BleHeartRateLink
import nl.tufein.openconnect.watch.FolderLink
import nl.tufein.openconnect.watch.HeartRateLink
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
    )

    val garmin = GarminController(
        settings = garminSettings,
        repo = repository,
        http = garminHttp,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        io = Dispatchers.IO,
        now = garminNow,
        onDataChanged = { dataChanged.tryEmit(Unit) },
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
        )
    }
}
