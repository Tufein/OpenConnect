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
 * The job runs about every three hours when there is a connection and the battery is not low, which is how Garmin
 * Connect keeps itself up to date without being opened. Android decides the exact moment; it can be later in battery
 * saver or Doze, and it only runs while a Garmin key is saved and automatic syncing is on.
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
        val request = PeriodicWorkRequestBuilder<GarminSyncWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
            .build()
        // KEEP: setting it again at every start of the app must not push the next run further away.
        manager.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    companion object {
        const val NAME = "garmin-sync"
        const val INTERVAL_HOURS = 3L
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
