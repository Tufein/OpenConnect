// OpenConnect, SPDX-License-Identifier: AGPL-3.0-or-later
package nl.tufein.openconnect.core.watch

import nl.tufein.openconnect.core.watch.ble.WatchNotification
import nl.tufein.openconnect.core.watch.ble.WatchNotificationCategory

/**
 * A notification on the phone, reduced to what decides whether it goes to the watch and what the watch shows. The
 * Android side fills it in from a `StatusBarNotification`; this side has no Android types, so the rules are tested
 * without a phone.
 */
data class PhoneNotification(
    /** Android's key for this notification; the same key means the same notification, updated. */
    val key: String,
    val packageName: String,
    val appName: String,
    val title: String? = null,
    val text: String? = null,
    /** The long form of the text, when the app has one. */
    val bigText: String? = null,
    val subText: String? = null,
    /** When the notification says it happened, else when it was posted; milliseconds since 1970. */
    val postTime: Long = 0,
    /** Android's category (`msg`, `email`, `call`, ...). */
    val category: String? = null,
    /** A running service or something the app keeps on screen (a music player, a timer, a download). */
    val ongoing: Boolean = false,
    /** The line that stands for a stack of notifications of one app. */
    val groupSummary: Boolean = false,
    /** The app asked for it to stay on the phone. */
    val localOnly: Boolean = false,
    /** The person can swipe it away. */
    val clearable: Boolean = true,
    /** Android shows it quietly (importance below "default"). */
    val lowImportance: Boolean = false,
    /** Do Not Disturb hides it from the person right now. */
    val silencedByDoNotDisturb: Boolean = false,
)

/** What the person chose: which apps, and how much of the text the watch may show. */
data class ForwardSettings(
    val enabled: Boolean = false,
    /** Package names; only these are forwarded. */
    val apps: Set<String> = emptySet(),
    /** Show the app's name and a fixed line, never the text of the message. */
    val hideContent: Boolean = false,
    /** Hold a notification back while the phone's screen is on: the person is looking at it already. */
    val onlyWhenScreenOff: Boolean = false,
    /** The line the watch shows instead of the text when [hideContent] is on; the app passes a translated one. */
    val hiddenBody: String = "New notification",
)

enum class SkipReason {
    OFF, OWN_APP, APP_NOT_CHOSEN, ONGOING, GROUP_SUMMARY, LOCAL_ONLY, CALL, LOW_IMPORTANCE, DO_NOT_DISTURB, EMPTY, SCREEN_ON,

    /** The same words were sent for this notification already. */
    DUPLICATE,

    /** There is no connection to the watch right now. */
    NOT_CONNECTED,

    /** The watch (or the phone side of the link) has notifications switched off. */
    WATCH_OFF,
}

sealed interface ForwardDecision {
    data class Send(val notification: WatchNotification) : ForwardDecision

    data class Skip(val reason: SkipReason) : ForwardDecision
}

object NotificationRules {
    private const val MAX_TITLE = 100
    private const val MAX_BODY = 1000

    /** The id the watch knows a notification by: the same key always gives the same id. */
    fun idOf(key: String): Int = key.hashCode()

    fun decide(n: PhoneNotification, settings: ForwardSettings, screenOn: Boolean, ownPackage: String): ForwardDecision {
        val skip = when {
            !settings.enabled -> SkipReason.OFF
            n.packageName == ownPackage -> SkipReason.OWN_APP
            n.packageName !in settings.apps -> SkipReason.APP_NOT_CHOSEN
            n.ongoing -> SkipReason.ONGOING
            n.groupSummary -> SkipReason.GROUP_SUMMARY
            n.localOnly -> SkipReason.LOCAL_ONLY
            // An incoming call is the dialer's own screen; the watch could show it but not answer it.
            n.category == "call" -> SkipReason.CALL
            n.lowImportance -> SkipReason.LOW_IMPORTANCE
            n.silencedByDoNotDisturb -> SkipReason.DO_NOT_DISTURB
            settings.onlyWhenScreenOff && screenOn -> SkipReason.SCREEN_ON
            else -> null
        }
        if (skip != null) return ForwardDecision.Skip(skip)

        val longer = listOf(n.text.orEmpty(), n.bigText.orEmpty()).maxByOrNull { it.length }.orEmpty()
        val body = tidy(longer, singleLine = false, max = MAX_BODY)
        val title = tidy(n.title, singleLine = true, max = MAX_TITLE)
        if (title.isEmpty() && body.isEmpty()) return ForwardDecision.Skip(SkipReason.EMPTY)

        val appName = tidy(n.appName, singleLine = true, max = MAX_TITLE).ifEmpty { n.packageName }
        val shown = if (settings.hideContent) {
            Triple(appName, settings.hiddenBody, "")
        } else {
            Triple(title.ifEmpty { appName }, body, tidy(n.subText, singleLine = true, max = MAX_TITLE))
        }
        return ForwardDecision.Send(
            WatchNotification(
                id = idOf(n.key),
                appId = n.packageName,
                appName = appName,
                title = shown.first,
                body = shown.second,
                whenMillis = n.postTime,
                category = categoryOf(n.category),
                subtitle = shown.third,
                canDismiss = n.clearable,
            ),
        )
    }

    fun categoryOf(androidCategory: String?): WatchNotificationCategory = when (androidCategory) {
        "email" -> WatchNotificationCategory.EMAIL
        "msg" -> WatchNotificationCategory.SMS
        "social" -> WatchNotificationCategory.SOCIAL
        "event", "reminder", "alarm" -> WatchNotificationCategory.SCHEDULE
        "navigation" -> WatchNotificationCategory.LOCATION
        else -> WatchNotificationCategory.OTHER
    }

    /**
     * Makes text fit a small screen: control characters become spaces, runs of spaces become one, line breaks stay
     * (or become spaces for a title), and it is cut at [max] characters without splitting an emoji.
     */
    internal fun tidy(text: String?, singleLine: Boolean, max: Int): String {
        if (text == null) return ""
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '\r' -> {
                    if (i + 1 < text.length && text[i + 1] == '\n') i++
                    sb.append(if (singleLine) ' ' else '\n')
                }
                c == '\n' -> sb.append(if (singleLine) ' ' else '\n')
                c.isISOControl() || c == ' ' || c == ' ' -> sb.append(' ')
                else -> sb.append(c)
            }
            i++
        }
        var s = sb.toString().replace(Regex(" {2,}"), " ")
        s = s.lines().joinToString("\n") { it.trim() }.trim()
        s = s.replace(Regex("\n{3,}"), "\n\n")
        if (s.length > max) {
            var end = max
            if (Character.isHighSurrogate(s[end - 1])) end--
            s = s.substring(0, end).trimEnd()
        }
        return s
    }
}

/**
 * Remembers the last words sent for each notification, so an app that posts the same notification again and again
 * (a progress bar, a sync icon) does not buzz the watch every time. A change of words, or a notification that was
 * removed and comes back, is new.
 */
class RepeatFilter {
    private val last = HashMap<Int, Int>()

    @Synchronized
    fun isNew(n: WatchNotification): Boolean {
        val fingerprint = listOf(n.title, n.subtitle, n.body).hashCode()
        if (last[n.id] == fingerprint) return false
        last[n.id] = fingerprint
        return true
    }

    @Synchronized
    fun forget(id: Int) {
        last.remove(id)
    }

    @Synchronized
    fun clear() = last.clear()
}
