package nl.tufein.openconnect

import android.app.Application
import nl.tufein.openconnect.core.StatsService
import nl.tufein.openconnect.core.reports.ReportService
import nl.tufein.openconnect.data.SettingsStore
import nl.tufein.openconnect.data.SqliteRepository

/** Wires the services once per process. */
class AppContainer(val repository: SqliteRepository, val settings: SettingsStore) {
    val stats = StatsService(repository)
    val reports = ReportService(repository, stats)
}

class OpenConnectApp : Application() {
    val container: AppContainer by lazy { AppContainer(SqliteRepository(this), SettingsStore(this)) }
}
