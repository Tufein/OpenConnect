package nl.tufein.openconnect.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import nl.tufein.openconnect.MainActivity
import nl.tufein.openconnect.OpenConnectApp
import nl.tufein.openconnect.R
import nl.tufein.openconnect.core.Nudge
import nl.tufein.openconnect.core.NudgeKind
import nl.tufein.openconnect.core.Gear
import nl.tufein.openconnect.core.GearBook
import nl.tufein.openconnect.core.Nudges
import nl.tufein.openconnect.core.Repository
import nl.tufein.openconnect.core.StatsService
import nl.tufein.openconnect.core.TrainingLoadCalculator
import nl.tufein.openconnect.core.WeeklyGoals
import nl.tufein.openconnect.sync.BackgroundSync
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** What the person chose and what was already sent. */
interface NudgeSettings {
    /** Off until the person turns it on. */
    var enabled: Boolean

    /** The keys of nudges already sent, oldest first. */
    var sent: List<String>

    val goals: WeeklyGoals

    /** The shoes as [nl.tufein.openconnect.core.GearBook] writes them; empty when none were added. */
    val gear: String get() = ""
}

/** Shows one nudge. The real one posts an Android notification; tests collect them. */
fun interface NudgeNotifier {
    fun show(nudge: Nudge)
}

/** Looks at the data and sends the nudges that are due, each only once. */
class NudgeService(
    private val repo: Repository,
    private val stats: StatsService,
    private val settings: NudgeSettings,
    private val notifier: NudgeNotifier,
) {
    /** Returns how many nudges were sent. Does nothing when notifications are switched off. */
    @Synchronized
    fun check(now: ZonedDateTime): Int {
        if (!settings.enabled) return 0
        val today = now.toLocalDate()
        val activities = repo.listActivities(limit = 10_000)
        val due = Nudges.due(
            today = today,
            hour = now.hour,
            goals = settings.goals,
            thisWeek = stats.weeklySummary(today),
            lastWeek = stats.weeklySummary(today.minusWeeks(1)),
            load = TrainingLoadCalculator.compute(activities, today),
            alreadySent = settings.sent.toSet(),
            shoesWorn = Gear.due(Gear.usage(GearBook.decode(settings.gear), activities)),
        )
        if (due.isEmpty()) return 0
        // Remembered first: a crash while showing must not send the same nudge again at the next check.
        settings.sent = Nudges.remember(settings.sent, due)
        due.forEach(notifier::show)
        return due.size
    }
}

/** Posts the nudge as an Android notification (the person has to allow notifications on Android 13 and newer). */
class AndroidNudgeNotifier(private val context: Context) : NudgeNotifier {
    override fun show(nudge: Nudge) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.nudge_channel), NotificationManager.IMPORTANCE_DEFAULT))
        }
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text(context, nudge))
            .setStyle(NotificationCompat.BigTextStyle().bigText(text(context, nudge)))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(nudge.key.hashCode(), notification)
        } catch (_: SecurityException) {
            // permission withdrawn while we were working: the nudge is simply not shown
        }
    }

    companion object {
        const val CHANNEL = "nudges"

        fun text(context: Context, n: Nudge): String = when (n.kind) {
            NudgeKind.GOAL_CLOSE -> when (n.goal) {
                "intensity" -> context.getString(R.string.nudge_goal_intensity, n.numbers[0])
                "distance" -> context.getString(R.string.nudge_goal_distance, n.numbers[0])
                else -> context.resources.getQuantityString(R.plurals.nudge_goal_activities, n.numbers[0], n.numbers[0])
            }
            NudgeKind.WEEKLY_SUMMARY -> context.getString(R.string.nudge_weekly, n.numbers[0], n.numbers[1], n.numbers[2])
            NudgeKind.LOAD_JUMP -> context.getString(R.string.nudge_load)
            NudgeKind.SHOES_WORN -> context.getString(R.string.nudge_shoes, n.goal.orEmpty(), n.numbers[0])
        }
    }
}

/** Runs about every three hours while notifications are on; Android decides the exact moment. */
class WorkManagerNudgeSync(private val context: Context) : BackgroundSync {
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
        val request = PeriodicWorkRequestBuilder<NudgeWorker>(3, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        manager.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    companion object {
        const val NAME = "nudges"
    }
}

class NudgeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        (applicationContext as OpenConnectApp).container.nudges.check(ZonedDateTime.now(ZoneId.systemDefault()))
        return Result.success()
    }
}
