package nl.tufein.openconnect.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import nl.tufein.openconnect.OpenConnectApp
import nl.tufein.openconnect.garmin.BackgroundOutcome
import nl.tufein.openconnect.watch.BleBackgroundOutcome
import java.util.concurrent.TimeUnit

/** Starts or stops the phone's periodic sync job. The Garmin controller says when it is wanted. */
fun interface BackgroundSync {
    fun set(enabled: Boolean)
}

/**
 * The job runs about every 15 minutes (the shortest period Android allows for a background job; while the app is open
 * it syncs every ten minutes itself) when there is a connection and the battery is not low. Android decides the exact
 * moment; it can be much later in battery saver or Doze, and it only runs while a Garmin key is saved and automatic
 * syncing is on.
 */
class WorkManagerBackgroundSync(private val context: Context) : BackgroundSync {
    override fun set(enabled: Boolean) {
        val manager = try {
            WorkManager.getInstance(context)
        } catch (_: IllegalStateException) {
            return // WorkManager is not up (it is not in a bare unit test); there is nothing to schedule on
        }
        if (!enabled) {
            manager.cancelUniqueWork(NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<GarminSyncWorker>(INTERVAL_MINUTES, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
            .build()
        // UPDATE once to move a job of an older version (every three hours) to the new period; KEEP afterwards, so
        // setting it again at every start of the app does not push the next run further away.
        manager.enqueueUniquePeriodicWork(NAME, periodPolicy(context, NAME), request)
    }

    companion object {
        const val NAME = "garmin-sync"
        const val INTERVAL_MINUTES = 15L
    }
}

/** Runs the short Garmin sync of the app's controller, which is the same code as the Sync button and the sync on opening. */
class GarminSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val garmin = (applicationContext as OpenConnectApp).container.garmin
        return when (garmin.syncInBackground()) {
            BackgroundOutcome.SKIPPED, BackgroundOutcome.SYNCED -> Result.success()
            // A few backed-off retries are worth it; after that the next scheduled run is a better time to try.
            BackgroundOutcome.RETRY -> if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
            BackgroundOutcome.KEY_REJECTED -> Result.failure()
        }
    }

    private companion object {
        const val MAX_RETRIES = 3
    }
}

/**
 * EXPERIMENTAL. Looks for the remembered Garmin watch over Bluetooth about every three hours, with the app closed, and
 * syncs it when it is in range. Android decides the exact moment and may delay it a lot in battery saver or Doze. It only
 * runs when the person switched it on, and never pairs or searches: it only talks to the watch already remembered.
 */
class WorkManagerBleBackgroundSync(private val context: Context) : BackgroundSync {
    override fun set(enabled: Boolean) {
        val manager = try {
            WorkManager.getInstance(context)
        } catch (_: IllegalStateException) {
            return
        }
        if (!enabled) {
            manager.cancelUniqueWork(NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<GarminBleSyncWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        manager.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    companion object {
        const val NAME = "garmin-ble-sync"
        const val INTERVAL_HOURS = 3L
    }
}

class GarminBleSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // Without the Bluetooth permission (Android 12 and newer) there is nothing to try; the app asks for it when the watch is used.
        if (android.os.Build.VERSION.SDK_INT >= 31 &&
            androidx.core.content.ContextCompat.checkSelfPermission(applicationContext, android.Manifest.permission.BLUETOOTH_CONNECT) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }
        val watch = (applicationContext as OpenConnectApp).container.watch
        return when (watch.syncGarminInBackground()) {
            BleBackgroundOutcome.SKIPPED, BleBackgroundOutcome.SYNCED, BleBackgroundOutcome.FAILED -> Result.success()
            // not in range right now: a couple of backed-off tries, then the next scheduled run
            BleBackgroundOutcome.RETRY -> if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
        }
    }

    private companion object {
        const val MAX_RETRIES = 2
    }
}

/** UPDATE the first time this version schedules [name] (an older version may have it at another period), KEEP after. */
internal fun periodPolicy(context: Context, name: String): ExistingPeriodicWorkPolicy {
    val prefs = context.getSharedPreferences("work-periods", Context.MODE_PRIVATE)
    val mark = "$name-15min"
    if (prefs.getBoolean(mark, false)) return ExistingPeriodicWorkPolicy.KEEP
    prefs.edit().putBoolean(mark, true).apply()
    return ExistingPeriodicWorkPolicy.UPDATE
}

/** The Oura ring's job: about every 15 minutes while a login is saved, when there is a connection. */
class WorkManagerOuraSync(private val context: Context) : BackgroundSync {
    override fun set(enabled: Boolean) {
        val manager = try {
            WorkManager.getInstance(context)
        } catch (_: IllegalStateException) {
            return
        }
        if (!enabled) {
            manager.cancelUniqueWork(NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<OuraSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
            .build()
        manager.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    companion object {
        const val NAME = "oura-sync"
    }
}

class OuraSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        (applicationContext as OpenConnectApp).container.oura.syncInBackground(java.time.Duration.ofMinutes(10))
        return Result.success() // the next run is a quarter of an hour away; no need to retry sooner
    }
}
