package nl.tufein.openconnect.garmin

import nl.tufein.openconnect.core.garmin.GarminApiException
import nl.tufein.openconnect.core.garmin.GarminSyncReport
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncBadgeTest {
    private val now = Instant.parse("2026-11-12T10:00:00Z")
    private val earlier = now.minusSeconds(600)

    private fun report(stopped: GarminApiException? = null) = GarminSyncReport(
        days = 7, endpoints = emptyList(), daysUpdated = 0, nightsUpdated = 0, activitiesAdded = 0,
        activitiesKnown = 0, activitiesWithoutFile = 0, activitiesPending = 0, stopped = stopped,
    )

    @Test
    fun `without a key there is no account to show`() {
        assertEquals(SyncBadge.NoAccount, syncBadge(GarminUi(hasKey = false)))
        assertEquals(SyncBadge.NoAccount, syncBadge(GarminUi(hasKey = false, lastSync = earlier)))
    }

    @Test
    fun `a sync in progress shows its progress`() {
        val ui = GarminUi(hasKey = true, lastSync = earlier, sync = GarminSyncUi.Running(2, 7, "Sleep"))
        assertEquals(SyncBadge.Syncing(2, 7, cancelling = false), syncBadge(ui))
        val cancelling = ui.copy(sync = GarminSyncUi.Running(2, 7, "Sleep", cancelling = true))
        assertEquals(SyncBadge.Syncing(2, 7, cancelling = true), syncBadge(cancelling))
    }

    @Test
    fun `a refused key is shown before anything else about the last sync`() {
        val ui = GarminUi(hasKey = true, keyCheck = KeyCheck.REJECTED, lastSync = earlier, sync = GarminSyncUi.Done(report()))
        assertEquals(SyncBadge.KeyRejected, syncBadge(ui))
    }

    @Test
    fun `a sync that failed or stopped early says so and remembers the last good one`() {
        val failed = GarminUi(hasKey = true, lastSync = earlier, sync = GarminSyncUi.Failed("boom"))
        assertEquals(SyncBadge.Failed(earlier), syncBadge(failed))

        val stopped = GarminUi(
            hasKey = true, lastSync = null,
            sync = GarminSyncUi.Done(report(GarminApiException(GarminApiException.Kind.NETWORK, "offline"))),
        )
        assertEquals(SyncBadge.Failed(null), syncBadge(stopped))
    }

    @Test
    fun `a finished sync shows how long ago it was, and a first sync is waiting`() {
        assertEquals(SyncBadge.Fresh(earlier), syncBadge(GarminUi(hasKey = true, lastSync = earlier, sync = GarminSyncUi.Done(report()))))
        assertEquals(SyncBadge.Fresh(earlier), syncBadge(GarminUi(hasKey = true, lastSync = earlier)))
        assertEquals(SyncBadge.NeverSynced, syncBadge(GarminUi(hasKey = true)))
    }

    @Test
    fun `ago turns a time into a whole unit`() {
        fun ago(seconds: Long) = ago(now.minusSeconds(seconds), now)
        assertEquals(Ago.JustNow, ago(0))
        assertEquals(Ago.JustNow, ago(59))
        assertEquals(Ago.Minutes(1), ago(60))
        assertEquals(Ago.Minutes(59), ago(59 * 60 + 59))
        assertEquals(Ago.Hours(1), ago(3600))
        assertEquals(Ago.Hours(47), ago(47 * 3600 + 3599))
        assertEquals(Ago.Days(2), ago(48 * 3600))
        assertEquals(Ago.Days(30), ago(Duration.ofDays(30).seconds))
        assertEquals(Ago.JustNow, ago(-300), "a clock that went backwards still reads as now")
    }
}
