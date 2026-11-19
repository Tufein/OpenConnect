// OpenConnect, SPDX-License-Identifier: AGPL-3.0-or-later
package nl.tufein.openconnect.notify

import nl.tufein.openconnect.core.watch.ble.WatchAsk

/** The things that all have to be in place for a notification to reach the watch, in the order they happen. */
enum class ForwardCheckId { ACCESS, LISTENER, SEEN, BLUETOOTH, OWN_NOTIFICATION, WATCH_KNOWN, CONNECTION, WATCH_ASKED, DELIVERY }

/** [OK] works, [WARN] works but not the way it should, [FAIL] is where it breaks, [WAIT] is still going, [UNKNOWN] cannot be known yet. */
enum class CheckState { OK, WARN, FAIL, WAIT, UNKNOWN }

data class ForwardCheck(val id: ForwardCheckId, val state: CheckState, val detail: String? = null)

/** What to tell the person to try, picked from what the checks found. */
enum class ForwardHint {
    LISTENER_NOT_STARTED, NOTHING_SEEN, CLOSE_GARMIN_CONNECT, BATTERY, WATCH_NOT_ASKING, WATCH_SAID_NO, WATCH_IGNORES, CHOOSE_APPS,
    ALLOW_OWN_NOTIFICATION,
}

/**
 * Reads [ForwardUi] and says, step by step, how far a notification gets. This is what makes "it does not arrive" an
 * answer: the first step that is not [CheckState.OK] is where it stops. Pure, so it is tested without a phone.
 */
object ForwardChecks {
    fun of(ui: ForwardUi): List<ForwardCheck> {
        val accessOk = ui.phase != ForwardPhase.NEEDS_ACCESS
        val connected = ui.phase == ForwardPhase.CONNECTED
        val n = ui.notify
        return listOf(
            ForwardCheck(ForwardCheckId.ACCESS, if (accessOk) CheckState.OK else CheckState.FAIL),
            ForwardCheck(
                ForwardCheckId.LISTENER,
                when {
                    !accessOk -> CheckState.UNKNOWN
                    ui.listenerBound -> CheckState.OK
                    else -> CheckState.FAIL
                },
            ),
            ForwardCheck(
                ForwardCheckId.SEEN,
                when {
                    !accessOk || !ui.listenerBound -> CheckState.UNKNOWN
                    ui.seen > 0 -> CheckState.OK
                    else -> CheckState.WAIT
                },
                detail = ui.seen.takeIf { it > 0 }?.toString(),
            ),
            ForwardCheck(ForwardCheckId.BLUETOOTH, if (ui.phase == ForwardPhase.NEEDS_BLUETOOTH) CheckState.FAIL else CheckState.OK),
            // not needed for forwarding itself, but without it Android hides the persistent notification and ends the app sooner
            ForwardCheck(ForwardCheckId.OWN_NOTIFICATION, if (ui.ownNotificationAllowed) CheckState.OK else CheckState.WARN),
            ForwardCheck(ForwardCheckId.WATCH_KNOWN, if (ui.phase == ForwardPhase.NEEDS_WATCH) CheckState.FAIL else CheckState.OK, ui.watchName),
            ForwardCheck(
                ForwardCheckId.CONNECTION,
                when (ui.phase) {
                    ForwardPhase.CONNECTED -> CheckState.OK
                    ForwardPhase.CONNECTING -> CheckState.WAIT
                    ForwardPhase.WAITING, ForwardPhase.PROBLEM -> CheckState.FAIL
                    else -> CheckState.UNKNOWN
                },
                detail = when (ui.phase) {
                    ForwardPhase.CONNECTING -> ui.connectStep?.name
                    ForwardPhase.WAITING, ForwardPhase.PROBLEM -> ui.error
                    else -> null
                },
            ),
            ForwardCheck(
                ForwardCheckId.WATCH_ASKED,
                when {
                    !connected -> CheckState.UNKNOWN
                    n.watch == WatchAsk.ON -> CheckState.OK
                    n.watch == WatchAsk.ASSUMED -> CheckState.WARN
                    n.watch == WatchAsk.OFF -> CheckState.FAIL
                    else -> CheckState.WAIT
                },
            ),
            ForwardCheck(
                ForwardCheckId.DELIVERY,
                when {
                    !connected -> CheckState.UNKNOWN
                    n.watch == WatchAsk.OFF -> CheckState.UNKNOWN
                    n.answered > 0 && n.failed == 0 -> CheckState.OK
                    n.answered > 0 -> CheckState.WARN
                    n.announced == 0 -> CheckState.WAIT
                    n.asked == 0 -> CheckState.FAIL // the watch was told, and never asked what it says
                    n.failed > 0 -> CheckState.FAIL
                    else -> CheckState.WAIT
                },
                detail = if (n.announced > 0) "${n.announced}/${n.asked}/${n.answered}" else null,
            ),
        )
    }

    /** The first step that is not working, or null when every step is [CheckState.OK]. */
    fun firstProblem(checks: List<ForwardCheck>): ForwardCheck? =
        checks.firstOrNull { it.state == CheckState.FAIL || it.state == CheckState.WARN }

    /** What to try, most useful first; empty when nothing is wrong. [garminConnectInstalled] is asked of Android by the screen. */
    fun hints(ui: ForwardUi, garminConnectInstalled: Boolean): List<ForwardHint> {
        if (!ui.enabled) return emptyList()
        val out = ArrayList<ForwardHint>()
        val n = ui.notify
        if (!ui.ownNotificationAllowed && ui.phase in setOf(ForwardPhase.CONNECTING, ForwardPhase.CONNECTED, ForwardPhase.WAITING, ForwardPhase.PROBLEM)) {
            out += ForwardHint.ALLOW_OWN_NOTIFICATION
        }
        when (ui.phase) {
            ForwardPhase.OFF, ForwardPhase.NEEDS_ACCESS, ForwardPhase.NEEDS_WATCH, ForwardPhase.NEEDS_BLUETOOTH, ForwardPhase.CONNECTING -> Unit
            ForwardPhase.WAITING, ForwardPhase.PROBLEM -> {
                if (garminConnectInstalled) out += ForwardHint.CLOSE_GARMIN_CONNECT
                if (ui.everConnected) out += ForwardHint.BATTERY // it worked and then dropped: the usual cause is the phone saving power
            }
            ForwardPhase.CONNECTED -> when {
                n.watch == WatchAsk.OFF -> out += ForwardHint.WATCH_SAID_NO
                n.watch == WatchAsk.ASSUMED -> out += ForwardHint.WATCH_NOT_ASKING
                n.announced > 0 && n.asked == 0 -> out += ForwardHint.WATCH_IGNORES
            }
        }
        val accessOk = ui.phase != ForwardPhase.NEEDS_ACCESS
        if (accessOk && ui.phase != ForwardPhase.OFF) {
            if (!ui.listenerBound) out += ForwardHint.LISTENER_NOT_STARTED
            else if (ui.seen > 0 && ui.apps.isEmpty()) out += ForwardHint.CHOOSE_APPS
            else if (ui.seen == 0 && ui.phase == ForwardPhase.CONNECTED) out += ForwardHint.NOTHING_SEEN
        }
        return out
    }
}
