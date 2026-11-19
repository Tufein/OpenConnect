// OpenConnect, SPDX-License-Identifier: AGPL-3.0-or-later
package nl.tufein.openconnect.notify

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import nl.tufein.openconnect.OpenConnectApp
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Android starts this when the person gave OpenConnect "notification access" and calls it for every notification on the
 * phone. It does not decide anything: it describes each notification ([StatusBarMapper]) and hands it to the
 * [NotificationForwarder], which applies the person's choices. Nothing is stored or sent anywhere but to the watch.
 *
 * Needs a real phone to exercise: only Android can bind a listener.
 */
class PhoneNotificationListener : NotificationListenerService() {
    /** One thread, so notifications reach the watch in the order they came and the main thread is never kept waiting. */
    private val worker: ExecutorService = Executors.newSingleThreadExecutor { Thread(it, "forward-notifications") }

    private val forwarder: NotificationForwarder get() = (application as OpenConnectApp).container.forwarding

    override fun onListenerConnected() {
        instance = this
        worker.execute {
            runCatching { forwarder.listenerChanged(true) }
            runCatching { forwarder.refresh() }
        }
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
        worker.execute {
            runCatching { forwarder.listenerChanged(false) }
            runCatching { forwarder.refresh() }
        }
        // Android drops a listener now and then (a crash elsewhere, a low-memory phase); ask to be bound again.
        if (ForwardAccess.hasNotificationAccess(this)) {
            runCatching { requestRebind(ComponentName(this, PhoneNotificationListener::class.java)) }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) {
        val ranking = Ranking().takeIf { rankingMap.getRanking(sbn.key, it) }
        worker.execute {
            runCatching {
                val phone = StatusBarMapper.map(sbn, appName = labels.labelOf(sbn.packageName), ranking = ranking)
                forwarder.onPosted(phone)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
        worker.execute { runCatching { forwarder.onRemoved(sbn.key) } }
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        worker.shutdown()
        super.onDestroy()
    }

    private val labels by lazy { AndroidInstalledApps(this) }

    companion object {
        @Volatile private var instance: PhoneNotificationListener? = null

        /** Asks Android to start the listener again: it does not always do so by itself after an update. */
        fun rebind(context: android.content.Context) {
            runCatching { requestRebind(ComponentName(context, PhoneNotificationListener::class.java)) }
        }

        /** Swipes a notification away on the phone, for when the person dismissed it on the watch. */
        fun cancel(key: String) {
            runCatching { instance?.cancelNotification(key) }
        }
    }
}
