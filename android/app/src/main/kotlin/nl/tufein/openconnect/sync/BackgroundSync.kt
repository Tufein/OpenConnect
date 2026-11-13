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
