package nl.tufein.openconnect.core

import java.time.Duration
import java.time.Instant

/**
 * Decides when the app is locked. It does not ask for a fingerprint itself (that is Android's job); it only keeps the
 * rule: with the lock on, the app is locked at start and again after it has been out of sight for more than
 * [grace]. The grace period keeps the lock from firing while you pick a file or answer the Health Connect screen,
 * which both leave the app for a moment.
 */
class LockGate(
    var enabled: Boolean,
    private val grace: Duration = Duration.ofSeconds(60),
    private val now: () -> Instant = Instant::now,
) {
    private var unlocked = false
    private var leftAt: Instant? = null

    val locked: Boolean get() = enabled && !unlocked

    /** The user passed the check. */
    fun markUnlocked() {
        unlocked = true
        leftAt = null
    }

    /** The app went out of sight. */
    fun left() {
        if (leftAt == null) leftAt = now()
    }

    /** The app came back; locks it again if it was away longer than the grace period. */
    fun returned() {
        val gone = leftAt ?: return
        leftAt = null
        if (Duration.between(gone, now()) > grace) unlocked = false
    }

    /** Locks now, for instance when the lock is switched off and on again. */
    fun lock() {
        unlocked = false
    }
}
