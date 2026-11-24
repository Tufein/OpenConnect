// OpenConnect, SPDX-License-Identifier: AGPL-3.0-or-later
package nl.tufein.openconnect.notify

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

/**
 * Keeps the Bluetooth connection to the watch alive while notifications are being forwarded: a quiet persistent
 * notification that stops Android from ending the app and from taking the connection away. The connection itself is
 * held by [NotificationForwarder]; this only shows the notification. It copies [nl.tufein.openconnect.watch.LiveRecordingService],
 * and like it needs a real phone to exercise.
 */
class ForwardingService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            runCatching { onStop() } // switch forwarding off first; that removes the notification
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, notification(this, state), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
                } else {
                    startForeground(NOTIFICATION_ID, notification(this, state))
                }
                log("the persistent notification is up")
            } catch (e: Exception) {
                // Android refused (a missing Bluetooth permission, or a start it counts as "from the background"):
                // forwarding goes on without the notification, as long as the listener keeps the app alive.
                log("Android refused the persistent notification: ${e::class.java.simpleName}: ${e.message}")
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL = "forward-notifications"
        private const val NOTIFICATION_ID = 1002
        private const val ACTION_STOP = "nl.tufein.openconnect.notify.FORWARD_STOP"

        @Volatile private var state: ForwardUi = ForwardUi()

        /** Called from the notification's "Turn off" action; set by [start]. */
        @Volatile private var onStop: () -> Unit = {}

        /** Where to write down what went wrong; set by [start], because the service itself has no way to be asked. */
        @Volatile private var sink: (String) -> Unit = {}

        fun start(context: Context, ui: ForwardUi, onStop: () -> Unit, log: (String) -> Unit = {}) {
            state = ui
            this.onStop = onStop
            sink = log
            log("asking Android to show the persistent notification (notifications ${if (NotificationManagerCompat.from(context).areNotificationsEnabled()) "allowed" else "NOT allowed"})")
            try {
                context.startForegroundService(Intent(context, ForwardingService::class.java))
            } catch (e: Exception) {
                // Android can refuse a foreground service started from the background (in Doze, say). The listener keeps
                // the app alive while notification access is on, so forwarding carries on; only the notification is missing.
                log("Android would not start the persistent notification: ${e::class.java.simpleName}: ${e.message}")
            }
        }

        private fun log(line: String) = sink(line)

        fun update(context: Context, ui: ForwardUi) {
            state = ui
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            try {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification(context, ui))
            } catch (_: SecurityException) {
                // permission withdrawn meanwhile: the notification is simply not refreshed
            }
        }

        fun stop(context: Context) {
            onStop = {} // a stale "Turn off" must not switch off what the person has switched on again
            try {
                context.stopService(Intent(context, ForwardingService::class.java))
            } catch (_: Exception) {
            }
        }

        /** Android ends the app when a foreground notification's channel does not exist, so every build makes sure it does. */
        fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL) == null) {
                // The service has no Activity to follow: the channel name follows the saved app language.
                val localized = AppLocale.wrap(context, SettingsStore(context).appLanguage)
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL, localized.getString(R.string.forward_channel), NotificationManager.IMPORTANCE_LOW),
                )
            }
        }

        fun notification(context: Context, ui: ForwardUi): Notification {
            ensureChannel(context)
            // Texts follow the saved app language; the tap and "Turn off" still target the original service context.
            val localized = AppLocale.wrap(context, SettingsStore(context).appLanguage)
            val content = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val turnOff = PendingIntent.getService(
                context, 2, Intent(context, ForwardingService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            return NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(localized.getString(R.string.forward_notification_title))
                .setContentText(textIn(localized, ui))
                .setContentIntent(content)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .addAction(0, localized.getString(R.string.forward_turn_off), turnOff)
                .build()
        }

        /** The one line of the notification; a function so a test can check it without building a notification. */
        fun text(context: Context, ui: ForwardUi): String =
            textIn(AppLocale.wrap(context, SettingsStore(context).appLanguage), ui)

        private fun textIn(context: Context, ui: ForwardUi): String {
            val watch = ui.watchName ?: context.getString(R.string.forward_watch_fallback)
            return if (ui.phase == ForwardPhase.CONNECTED) {
                context.resources.getQuantityString(R.plurals.forward_notification_connected, ui.sent, watch, ui.sent)
            } else {
                context.getString(R.string.forward_notification_waiting, watch)
            }
        }
    }
}

/** The real [ForwardingKeeper]: the notification shown by [ForwardingService]. */
class ForegroundForwarding(private val context: Context, private val log: (String) -> Unit = {}) : ForwardingKeeper {
    override fun start(state: ForwardUi, onStop: () -> Unit) = ForwardingService.start(context, state, onStop, log)

    override fun update(state: ForwardUi) = ForwardingService.update(context, state)

    override fun stop() = ForwardingService.stop(context)
}
