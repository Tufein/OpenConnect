package nl.tufein.openconnect.garmin

import java.time.Duration
import java.time.Instant

/** The one-line sync status shown at the top of the Home screen, like the one in Garmin Connect. */
sealed interface SyncBadge {
    /** No Garmin account connected yet. */
    data object NoAccount : SyncBadge

    data class Syncing(val done: Int, val total: Int, val cancelling: Boolean) : SyncBadge

    /** Garmin refuses the key, so nothing will sync until a new one is saved. */
    data object KeyRejected : SyncBadge

    /** The last attempt did not work out; [lastSync] is the last good one, if there was one. */
    data class Failed(val lastSync: Instant?) : SyncBadge

    data class Fresh(val lastSync: Instant) : SyncBadge

    /** An account is connected but nothing has been synced yet. */
    data object NeverSynced : SyncBadge
}

fun syncBadge(ui: GarminUi): SyncBadge {
    if (!ui.hasKey) return SyncBadge.NoAccount
    val sync = ui.sync
    if (sync is GarminSyncUi.Running) return SyncBadge.Syncing(sync.done, sync.total, sync.cancelling)
    if (ui.keyCheck == KeyCheck.REJECTED) return SyncBadge.KeyRejected
    if (sync is GarminSyncUi.Failed || (sync is GarminSyncUi.Done && sync.report.stopped != null)) return SyncBadge.Failed(ui.lastSync)
    val last = ui.lastSync ?: return SyncBadge.NeverSynced
    return SyncBadge.Fresh(last)
}

/** A time that has passed, in the whole unit people say it in. */
sealed interface Ago {
    data object JustNow : Ago

    data class Minutes(val n: Int) : Ago

    data class Hours(val n: Int) : Ago

    data class Days(val n: Int) : Ago
}

fun ago(then: Instant, now: Instant): Ago {
    val d = Duration.between(then, now)
    return when {
        d.seconds < 60 -> Ago.JustNow
        d.toMinutes() < 60 -> Ago.Minutes(d.toMinutes().toInt())
        d.toHours() < 48 -> Ago.Hours(d.toHours().toInt())
        else -> Ago.Days(d.toDays().toInt())
    }
}
