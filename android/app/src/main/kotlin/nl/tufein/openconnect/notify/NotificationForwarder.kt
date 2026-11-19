// OpenConnect, SPDX-License-Identifier: AGPL-3.0-or-later
package nl.tufein.openconnect.notify

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.tufein.openconnect.core.watch.ForwardDecision
import nl.tufein.openconnect.core.watch.ForwardSettings
import nl.tufein.openconnect.core.watch.NotificationRules
import nl.tufein.openconnect.core.watch.PhoneNotification
import nl.tufein.openconnect.core.watch.RepeatFilter
import nl.tufein.openconnect.core.watch.SkipReason
import nl.tufein.openconnect.core.watch.ble.NotifyStats
import nl.tufein.openconnect.core.watch.ble.PostResult
import nl.tufein.openconnect.core.watch.ble.WatchNotification
import nl.tufein.openconnect.core.watch.ble.WatchNotificationCategory
import nl.tufein.openconnect.watch.GarminBleLink
import nl.tufein.openconnect.watch.GarminStep
import nl.tufein.openconnect.watch.GarminWatchMemory
import nl.tufein.openconnect.watch.WatchLinkException
import java.time.Instant

/**
 * Where forwarding stands. The first four are things the person has to do; the rest is the connection to the watch.
 * [PROBLEM] is a failure that trying again will not fix (the watch is not paired, a permission is missing): it waits for
 * the person, and the next [NotificationForwarder.refresh] tries again.
 */
enum class ForwardPhase { OFF, NEEDS_ACCESS, NEEDS_WATCH, NEEDS_BLUETOOTH, CONNECTING, CONNECTED, WAITING, PROBLEM }

data class ForwardUi(
    val enabled: Boolean = false,
    val phase: ForwardPhase = ForwardPhase.OFF,
    val watchName: String? = null,
    /** The last thing that went wrong with the connection, as the link reported it. */
    val error: String? = null,
    val apps: Set<String> = emptySet(),
    val hideContent: Boolean = false,
    val onlyWhenScreenOff: Boolean = false,
    /** How many notifications were announced to the watch since the app started. */
    val sent: Int = 0,
    val lastSentAt: Instant? = null,
    /** Why the most recent notification was not sent, so "it did not arrive" has an answer. */
    val lastSkipped: SkipReason? = null,
    /** How many times each reason held a notification back since the app started. */
    val skippedBy: Map<SkipReason, Int> = emptyMap(),
    /** Android has started the listener that hands us the phone's notifications. */
    val listenerBound: Boolean = false,
    /** How many notifications Android handed us since the app started, chosen or not. */
    val seen: Int = 0,
    val lastSeenAt: Instant? = null,
    /** Where the connection is while [phase] is [ForwardPhase.CONNECTING]. */
    val connectStep: GarminStep? = null,
    /** The connection worked at least once since the app started. */
    val everConnected: Boolean = false,
    /** Android lets this app show notifications; without it the persistent notification is hidden and the app is ended sooner. */
    val ownNotificationAllowed: Boolean = true,
    /** What the watch asked for and what we told and gave it, on the current connection. */
    val notify: NotifyStats = NotifyStats(),
)

/** What "send a test" came to: the watch was told, there is no connection, the watch said no, or it has not asked yet. */
enum class TestResult { SENT, NOT_CONNECTED, WATCH_OFF, WATCH_NOT_ASKING }

/**
 * Forwards phone notifications to the remembered Garmin watch. It decides (with the rules in core), keeps one
 * Bluetooth connection open (and opens it again after the watch went out of reach, with longer waits each time) and
 * counts what it did, for the card on the Watch screen. It has no Android types: the phone's notifications, Bluetooth,
 * permissions and the persistent notification are passed in, so all of this is tested with a fake watch.
 */
class NotificationForwarder(
    private val store: ForwardStore,
    private val memory: GarminWatchMemory,
    private val link: GarminBleLink,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    /** This app's own package, whose notifications are never forwarded. */
    private val ownPackage: String,
    /** Android's "notification access" for this app. */
    private val accessGranted: () -> Boolean,
    private val screenOn: () -> Boolean,
    /** The Bluetooth permission (Android 12 and up: "nearby devices"). */
    private val bluetoothAllowed: () -> Boolean,
    private val keeper: ForwardingKeeper = NoForwardingKeeper,
    /** The line the watch shows instead of the text when "hide the text" is on. The app passes a translated one. */
    private val hiddenBody: () -> String = { "New notification" },
    /** Swipes a notification away on the phone, after the person dismissed it on the watch. */
    private val dismissOnPhone: (key: String) -> Unit = {},
    /** True while a sync holds a connection of its own; the watch has room for one, so forwarding waits. */
    private val syncBusy: () -> Boolean = { false },
    private val waitWhileBusy: suspend () -> Unit = { delay(2_000) },
    private val now: () -> Instant = { Instant.now() },
    /** Waits before connection attempt number [attempt] (1, 2, ...) after a failure or a lost connection. */
    private val retryDelay: suspend (attempt: Int) -> Unit = { attempt -> delay(minOf(10_000L shl (attempt - 1).coerceIn(0, 5), 300_000L)) },
    /** What happened, for the "What is happening?" part of the card. Never holds the text of a notification. */
    val trail: ForwardTrail = ForwardTrail(),
    /** Asks Android to start the notification listener again, for when access is on but it never started. */
    private val rebindListener: () -> Unit = {},
    /** Whether Android lets this app show notifications (the persistent one needs it from Android 13). */
    private val notificationsAllowed: () -> Boolean = { true },
) {
    companion object {
        /** How many notifications are remembered by id, to find the one the person dismissed on the watch. */
        private const val MAX_KNOWN = 200

        /** A notification that did not reach a stuck watch is sent once more, if the new connection comes within this time. */
        val SEND_AGAIN_WITHIN: java.time.Duration = java.time.Duration.ofMinutes(2)
        private const val MAX_SEND_AGAIN = 8

        /** The phases in which the persistent notification is shown, because a connection is held or being sought. */
        private val HOLDING = setOf(ForwardPhase.CONNECTING, ForwardPhase.CONNECTED, ForwardPhase.WAITING)
    }

    private val _state = MutableStateFlow(initial())
    val state: StateFlow<ForwardUi> = _state.asStateFlow()

    private val lock = Any()
    private var job: Job? = null

    @Volatile private var connection: WatchNotifyConnection? = null

    private val repeats = RepeatFilter()

    /** True while the persistent notification is shown. */
    private var keeping = false
    private val keeperLock = Any()

    /** What was sent, by the id the watch knows, so a dismissal on the watch finds its notification. */
    private val known = object : LinkedHashMap<Int, String>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, String>?) = size > MAX_KNOWN
    }
    private var testCounter = 0

    /** Notifications a stuck watch never got, to send once more over the next connection. */
    private class SendAgain(val notification: WatchNotification, val at: Instant)

    private val sendAgain = ArrayList<SendAgain>()

    /** Ids that were already sent a second time: if the watch gets stuck on them again, they are let go. */
    private val sentAgain = LinkedHashSet<Int>()
    private val sendAgainLock = Any()

    /** How many notifications the current connection had announced when we last looked, to count only the new ones. */
    private var announcedSeen = 0
    private val statsLock = Any()

    private fun initial() = ForwardUi(
        enabled = store.enabled,
        phase = if (store.enabled) ForwardPhase.CONNECTING else ForwardPhase.OFF,
        watchName = memory.load()?.name,
        apps = store.apps,
        hideContent = store.hideContent,
        onlyWhenScreenOff = store.onlyWhenScreenOff,
    )

    // ------------------------------------------------------------------------------------- the switch

    fun setEnabled(on: Boolean) {
        store.enabled = on
        _state.update { it.copy(enabled = on) }
        refresh()
    }

    fun setApp(packageName: String, on: Boolean) {
        val apps = if (on) store.apps + packageName else store.apps - packageName
        store.apps = apps
        _state.update { it.copy(apps = apps) }
    }

    fun setHideContent(on: Boolean) {
        store.hideContent = on
        _state.update { it.copy(hideContent = on) }
    }

    fun setOnlyWhenScreenOff(on: Boolean) {
        store.onlyWhenScreenOff = on
        _state.update { it.copy(onlyWhenScreenOff = on) }
    }

    /**
     * Looks at what is needed and does the next thing: waits for the person (access, a synced watch, Bluetooth), or
     * starts the connection. Safe to call again and again; it does nothing while a connection is being kept.
     */
    fun refresh() {
        synchronized(lock) {
            val allowed = notificationsAllowed()
            if (_state.value.ownNotificationAllowed != allowed) {
                trail.add(if (allowed) "Android lets OpenConnect show notifications" else "Android does not let OpenConnect show notifications: the persistent notification stays hidden")
                _state.update { it.copy(ownNotificationAllowed = allowed) }
            }
            val blocked = when {
                !store.enabled -> ForwardPhase.OFF
                !accessGranted() -> ForwardPhase.NEEDS_ACCESS
                memory.load() == null -> ForwardPhase.NEEDS_WATCH
                !bluetoothAllowed() -> ForwardPhase.NEEDS_BLUETOOTH
                else -> null
            }
            if (blocked != null) {
                if (_state.value.phase != blocked) trail.add("waiting for the person: $blocked")
                stopJob()
                setPhase(blocked, error = null)
            } else {
                if (!_state.value.listenerBound) {
                    // access is on but Android has not started the listener (after an update, say): ask it to
                    trail.add("notification access is on but Android has not started the listener; asking it to")
                    runCatching { rebindListener() }
                }
                if (job?.isActive != true) startJob(memory.load()!!)
            }
        }
    }

    /** Android started (true) or stopped (false) the listener that hands us the phone's notifications. */
    fun listenerChanged(bound: Boolean) {
        trail.add(if (bound) "Android started the notification listener" else "Android stopped the notification listener")
        _state.update { it.copy(listenerBound = bound) }
    }

    /** "Try now": drops what is going on, including a long wait, and connects again. */
    fun reconnect() {
        synchronized(lock) { stopJob() }
        refresh()
    }

    // ------------------------------------------------------------------------------------ the link

    private fun startJob(watch: nl.tufein.openconnect.watch.GarminBleWatch) {
        trail.add("connecting to ${watch.name}")
        setPhase(ForwardPhase.CONNECTING, error = null, watchName = watch.name)
        job = scope.launch(io) { keepConnected(watch) }
    }

    /** Cancels the loop and closes the connection. Call with [lock] held. */
    private fun stopJob() {
        job?.cancel()
        job = null
        connection?.let { runCatching { it.close() } }
        connection = null
    }

    private suspend fun keepConnected(watch: nl.tufein.openconnect.watch.GarminBleWatch) {
        var attempt = 0
        while (true) {
            while (syncBusy()) waitWhileBusy()
            val lost = CompletableDeferred<String?>()
            val listener = object : NotifyListener {
                override fun onDismissedOnWatch(id: Int) = dismissedOnWatch(id)

                override fun onLost(reason: String?) {
                    trail.add("connection lost: ${reason ?: "no reason given"}")
                    lost.complete(reason)
                }

                override fun onStatus(stats: NotifyStats) = statusChanged(stats)

                override fun onLog(line: String) = trail.add(line)

                override fun onUndelivered(notifications: List<WatchNotification>) = keepForNextConnection(notifications)
            }
            synchronized(statsLock) { announcedSeen = 0 }
            _state.update { it.copy(notify = NotifyStats(), connectStep = null) }
            try {
                val opened = link.openNotifications(watch, listener) { step ->
                    trail.add("connecting: $step")
                    _state.update { it.copy(connectStep = step) }
                }
                try {
                    currentCoroutineContext().ensureActive() // stopped while connecting: close it, do not report it
                    connection = opened
                    repeats.clear()
                    trail.add("connected to ${opened.watchName.ifBlank { watch.name }}")
                    setPhase(ForwardPhase.CONNECTED, error = null, watchName = opened.watchName.ifBlank { watch.name })
                    attempt = 0
                    sendAgainOver(opened)
                    val reason = lost.await()
                    attempt = 1
                    forgetConnection(opened)
                    runCatching { opened.close() } // let the radio go before the wait starts
                    setPhase(ForwardPhase.WAITING, error = reason, watchName = watch.name)
                } finally {
                    forgetConnection(opened)
                    runCatching { opened.close() }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: WatchLinkException) {
                currentCoroutineContext().ensureActive()
                trail.add("could not connect (${e.reason}): ${e.message ?: "no detail"}")
                if (e.reason == WatchLinkException.Reason.NOT_PAIRED || e.reason == WatchLinkException.Reason.NO_PERMISSION) {
                    // Trying again does not help until the person pairs the watch or allows Bluetooth.
                    setPhase(ForwardPhase.PROBLEM, error = e.message ?: e.reason.name, watchName = watch.name)
                    return
                }
                attempt++
                setPhase(ForwardPhase.WAITING, error = e.message ?: e.reason.name, watchName = watch.name)
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                trail.add("could not connect: ${e::class.java.simpleName}: ${e.message ?: "no detail"}")
                attempt++
                setPhase(ForwardPhase.WAITING, error = e.message ?: e::class.java.simpleName, watchName = watch.name)
            }
            trail.add("trying again in ${retryWaitText(attempt)}")
            retryDelay(attempt)
        }
    }

    /** The watch got stuck before these arrived: keep them for the next connection, unless they already had a second go. */
    private fun keepForNextConnection(notifications: List<WatchNotification>) {
        val at = now()
        synchronized(sendAgainLock) {
            for (n in notifications) {
                sendAgain.removeAll { it.notification.id == n.id }
                if (n.id in sentAgain) {
                    trail.add("notification ${n.id} did not arrive on the second try either; letting it go")
                } else {
                    sendAgain += SendAgain(n, at)
                }
            }
            while (sendAgain.size > MAX_SEND_AGAIN) sendAgain.removeAt(0)
        }
    }

    /** Sends what a stuck watch missed over the new connection [open], if it is recent enough; each one only once. */
    private fun sendAgainOver(open: WatchNotifyConnection) {
        val due = synchronized(sendAgainLock) {
            val cutoff = now().minus(SEND_AGAIN_WITHIN)
            val fresh = sendAgain.filter { !it.at.isBefore(cutoff) }.map { it.notification }
            val stale = sendAgain.size - fresh.size
            if (stale > 0) trail.add("$stale missed notification(s) are too old to send again")
            sendAgain.clear()
            for (n in fresh) {
                sentAgain += n.id
                while (sentAgain.size > MAX_KNOWN) sentAgain.remove(sentAgain.first())
            }
            fresh
        }
        for (n in due) {
            val result = open.post(n)
            trail.add("notification ${n.id} sent again after the watch got stuck: $result")
        }
    }

    private fun forgetConnection(opened: WatchNotifyConnection) {
        synchronized(lock) { if (connection === opened) connection = null }
    }

    /** The conversation with the watch moved on: it asked, we announced, it confirmed. Counts what is new since last time. */
    private fun statusChanged(stats: NotifyStats) {
        val fresh = synchronized(statsLock) {
            val delta = (stats.announced - announcedSeen).coerceAtLeast(0)
            announcedSeen = stats.announced
            delta
        }
        val at = now()
        val before = _state.value.notify
        _state.update {
            it.copy(
                notify = stats,
                sent = it.sent + fresh,
                lastSentAt = if (fresh > 0) at else it.lastSentAt,
                lastSkipped = if (fresh > 0) null else it.lastSkipped,
            )
        }
        if (before.watch != stats.watch) trail.add("the watch's answer about notifications: ${stats.watch}")
        if (fresh > 0) keeper.update(_state.value)
    }

    private fun retryWaitText(attempt: Int): String {
        val seconds = minOf(10L shl (attempt - 1).coerceIn(0, 5), 300L)
        return if (seconds >= 60) "${seconds / 60} min" else "$seconds s"
    }

    private fun setPhase(phase: ForwardPhase, error: String?, watchName: String? = null) {
        val before = _state.value
        _state.update {
            it.copy(
                phase = phase,
                error = error,
                watchName = watchName ?: it.watchName,
                everConnected = it.everConnected || phase == ForwardPhase.CONNECTED,
                connectStep = if (phase == ForwardPhase.CONNECTING) it.connectStep else null,
            )
        }
        val after = _state.value
        // OFF and the "waiting for the person" phases hold nothing, so no persistent notification is needed.
        val holding = phase in HOLDING
        synchronized(keeperLock) {
            when {
                holding && !keeping -> {
                    keeping = true
                    keeper.start(after) { setEnabled(false) }
                }
                holding && (before.phase != after.phase || before.watchName != after.watchName) -> keeper.update(after)
                !holding && keeping -> {
                    keeping = false
                    keeper.stop()
                }
            }
        }
    }

    // ------------------------------------------------------------------------ the notifications

    /** A notification appeared (or changed) on the phone. */
    fun onPosted(notification: PhoneNotification) {
        val seenAt = now()
        _state.update { it.copy(seen = it.seen + 1, lastSeenAt = seenAt) }
        if (!store.enabled) return
        val settings = ForwardSettings(
            enabled = true,
            apps = store.apps,
            hideContent = store.hideContent,
            onlyWhenScreenOff = store.onlyWhenScreenOff,
            hiddenBody = hiddenBody(),
        )
        val decision = NotificationRules.decide(notification, settings, screenOn = screenOn(), ownPackage = ownPackage)
        val send = when (decision) {
            is ForwardDecision.Skip -> return skipped(decision.reason, notification.packageName)
            is ForwardDecision.Send -> decision.notification
        }
        val open = connection ?: return skipped(SkipReason.NOT_CONNECTED, notification.packageName)
        if (!repeats.isNew(send)) return skipped(SkipReason.DUPLICATE, notification.packageName)
        when (val result = open.post(send)) {
            PostResult.SENT, PostResult.WAITING -> {
                synchronized(known) { known[send.id] = notification.key }
                // the count of what reached the watch comes from the connection (statusChanged): a notification the
                // watch has not asked for yet is only waiting, and is not counted as sent
                trail.add("${notification.packageName}: ${if (result == PostResult.SENT) "announced to the watch" else "waiting, the watch has not asked for notifications yet"}")
                _state.update { it.copy(lastSkipped = null) }
            }
            PostResult.OFF -> {
                repeats.forget(send.id)
                skipped(SkipReason.WATCH_OFF, notification.packageName)
            }
        }
    }

    /** A notification was removed on the phone (swiped, or the app took it back): the watch loses it too. */
    fun onRemoved(key: String) {
        val id = NotificationRules.idOf(key)
        repeats.forget(id)
        synchronized(sendAgainLock) { sendAgain.removeAll { it.notification.id == id } } // gone from the phone: not sent again
        val wasSent = synchronized(known) { known.remove(id) != null }
        if (wasSent) connection?.remove(id)
    }

    private fun skipped(reason: SkipReason, packageName: String? = null) {
        if (packageName != null) trail.add("$packageName: held back, $reason")
        _state.update { it.copy(lastSkipped = reason, skippedBy = it.skippedBy + (reason to ((it.skippedBy[reason] ?: 0) + 1))) }
    }

    private fun dismissedOnWatch(id: Int) {
        val key = synchronized(known) { known.remove(id) } ?: return
        repeats.forget(id)
        dismissOnPhone(key)
    }

    /** Sends a made-up notification to the watch, whatever apps were chosen: the "does it work" button. */
    fun sendTest(title: String, body: String): TestResult {
        val open = connection ?: return TestResult.NOT_CONNECTED.also { trail.add("test: not connected") }
        val id = NotificationRules.idOf("openconnect-test-${testCounter++}")
        val test = WatchNotification(
            id = id,
            appId = ownPackage,
            appName = "OpenConnect",
            title = title,
            body = body,
            whenMillis = now().toEpochMilli(),
            category = WatchNotificationCategory.OTHER,
        )
        return when (open.post(test)) {
            PostResult.SENT -> TestResult.SENT
            PostResult.WAITING -> TestResult.WATCH_NOT_ASKING
            PostResult.OFF -> TestResult.WATCH_OFF
        }.also { trail.add("test: $it") }
    }
}
