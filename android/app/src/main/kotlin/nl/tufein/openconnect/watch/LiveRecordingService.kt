// OpenConnect, SPDX-License-Identifier: AGPL-3.0-or-later
package nl.tufein.openconnect.watch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import nl.tufein.openconnect.MainActivity
import nl.tufein.openconnect.R
import nl.tufein.openconnect.data.SettingsStore
import nl.tufein.openconnect.i18n.AppLocale
import nl.tufein.openconnect.ui.Fmt

/**
 * A foreground service that keeps a live heart-rate recording running while the screen is off. The recording itself
 * (the Bluetooth connection and the numbers) is still held by [WatchController] in the app process; this service only
 * shows the persistent notification that stops Android from killing that process and from reclaiming the Bluetooth
 * connection. Like the other links it needs a real phone to exercise.
 */
class LiveRecordingService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            runCatching { onStop() } // end the recording first; the state change removes the notification
            stopForegroundCompat()
            stopSelf()
        } else {
            startForegroundCompat()
        }
        return START_NOT_STICKY
    }

    private fun startForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification(this, state), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification(this, state))
        }
    }

    private fun stopForegroundCompat() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    companion object {
        private const val CHANNEL = "live-heart-rate"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP = "nl.tufein.openconnect.watch.LIVE_STOP"

        /** The latest state, for the service to build its first notification and for updates. */
        @Volatile private var state: LiveUi = LiveUi()

        /** Called from the notification's Stop action; set by [start]. */
        @Volatile private var onStop: () -> Unit = {}

        fun start(context: Context, live: LiveUi, onStop: () -> Unit) {
            state = live
            this.onStop = onStop
            try {
                context.startForegroundService(Intent(context, LiveRecordingService::class.java))
            } catch (_: Exception) {
                // Foreground services can be refused (for example in Doze); the recording still works while the app is open.
            }
        }

        fun update(context: Context, live: LiveUi) {
            state = live
            notify(context)
        }

        fun stop(context: Context) {
            onStop = {} // a stale Stop action must not stop a recording that is already over
            try {
                context.stopService(Intent(context, LiveRecordingService::class.java))
            } catch (_: Exception) {
            }
        }

        private fun notify(context: Context) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            try {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification(context, state))
            } catch (_: SecurityException) {
                // permission withdrawn while we were working: the notification is simply not refreshed
            }
        }

        /**
         * Makes sure the notification channel exists. Android refuses a foreground-service notification whose
         * channel does not exist yet and ends the app with it, so this runs for every notification that is built,
         * including the very first one handed to `startForeground`. A channel that exists already is left alone.
         */
        fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            // The notification is refreshed every second; only ask the system when the channel is not there yet.
            if (manager.getNotificationChannel(CHANNEL) == null) {
                // The service has no Activity to follow: the channel name follows the saved app language.
                val localized = AppLocale.wrap(context, SettingsStore(context).appLanguage)
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL, localized.getString(R.string.watch_live_title), NotificationManager.IMPORTANCE_LOW),
                )
            }
        }

        fun notification(context: Context, live: LiveUi): Notification {
            ensureChannel(context)
            // Texts follow the saved app language; the tap and Stop still target the original service context.
            val localized = AppLocale.wrap(context, SettingsStore(context).appLanguage)
            val content = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val stop = PendingIntent.getService(
                context, 1, Intent(context, LiveRecordingService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            return NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(localized.getString(R.string.watch_live_title))
                .setContentText(textIn(localized, live))
                .setContentIntent(content)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .addAction(0, localized.getString(R.string.watch_live_stop), stop)
                .build()
        }

        /** The one line of the notification; a function so a test can check it without building a notification. */
        fun text(context: Context, live: LiveUi): String =
            textIn(AppLocale.wrap(context, SettingsStore(context).appLanguage), live)

        private fun textIn(context: Context, live: LiveUi): String =
            context.getString(R.string.live_notification_text, live.bpm?.toString() ?: "–", Fmt.duration(live.seconds.toDouble()))
    }
}

/** The real [LiveRecordingKeeper]: the notification shown by [LiveRecordingService]. */
class ForegroundLiveRecording(private val context: Context) : LiveRecordingKeeper {
    override fun start(state: LiveUi, onStop: () -> Unit) = LiveRecordingService.start(context, state, onStop)

    override fun update(state: LiveUi) = LiveRecordingService.update(context, state)

    override fun stop() = LiveRecordingService.stop(context)
}
