// OpenConnect, SPDX-License-Identifier: AGPL-3.0-or-later
package nl.tufein.openconnect.notify

import android.app.Notification
import android.app.NotificationManager
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import nl.tufein.openconnect.core.watch.PhoneNotification

/**
 * Turns what Android hands the listener (a `StatusBarNotification` and how it ranks) into a [PhoneNotification], the
 * plain description the rules in core decide on. Only reads; nothing is kept.
 */
object StatusBarMapper {
    fun map(
        sbn: StatusBarNotification,
        appName: String,
        ranking: NotificationListenerService.Ranking? = null,
    ): PhoneNotification {
        val n = sbn.notification
        val extras = n.extras ?: Bundle.EMPTY
        val flags = n.flags
        return PhoneNotification(
            key = sbn.key,
            packageName = sbn.packageName,
            appName = appName,
            title = extras.text(Notification.EXTRA_TITLE) ?: extras.text(Notification.EXTRA_TITLE_BIG),
            text = extras.text(Notification.EXTRA_TEXT) ?: lastMessage(extras) ?: lastLine(extras),
            bigText = extras.text(Notification.EXTRA_BIG_TEXT),
            subText = extras.text(Notification.EXTRA_SUB_TEXT),
            // the time the app gave it, else the time it was posted
            postTime = n.`when`.takeIf { it > 0 } ?: sbn.postTime,
            category = n.category,
            ongoing = sbn.isOngoing || flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE) != 0,
            groupSummary = flags and Notification.FLAG_GROUP_SUMMARY != 0,
            localOnly = flags and Notification.FLAG_LOCAL_ONLY != 0,
            clearable = sbn.isClearable,
            // "default" and up make a sound or appear on screen; quieter ones are not worth a buzz on the wrist
            lowImportance = ranking != null && ranking.importance != NotificationManager.IMPORTANCE_UNSPECIFIED &&
                ranking.importance < NotificationManager.IMPORTANCE_DEFAULT,
            silencedByDoNotDisturb = ranking != null && !ranking.matchesInterruptionFilter(),
            onlyAlertOnce = flags and Notification.FLAG_ONLY_ALERT_ONCE != 0,
        )
    }

    private fun Bundle.text(key: String): String? = getCharSequence(key)?.toString()

    /** The newest line of a chat (messaging style notifications keep the lines as bundles). */
    @Suppress("DEPRECATION")
    private fun lastMessage(extras: Bundle): String? {
        val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES) ?: return null
        val last = messages.lastOrNull() as? Bundle ?: return null
        val sender = last.getCharSequence("sender")?.toString()
        val text = last.getCharSequence("text")?.toString() ?: return null
        return if (sender.isNullOrBlank()) text else "$sender: $text"
    }

    /** The newest line of an inbox style notification. */
    private fun lastLine(extras: Bundle): String? = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.lastOrNull()?.toString()
}
