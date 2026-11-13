package nl.tufein.openconnect.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Constraints
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import nl.tufein.openconnect.AppContainer
import nl.tufein.openconnect.OpenConnectApp
import nl.tufein.openconnect.data.SettingsStore
import nl.tufein.openconnect.data.SqliteRepository
import nl.tufein.openconnect.garmin.FakeGarminHttp
import nl.tufein.openconnect.garmin.FakeGarminSettings
import nl.tufein.openconnect.watch.FakeBle
import nl.tufein.openconnect.watch.FakeUsb
import nl.tufein.openconnect.watch.OpenedWatch
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The Android side of syncing in the background: the job that is scheduled and the worker it runs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = OpenConnectApp::class)
class BackgroundSyncTest {
    private val now = Instant.parse("2026-11-12T10:00:00Z")
    private val http = FakeGarminHttp().also { it.answerLikeGarmin(now) }
    private val settings = FakeGarminSettings(key = "GCDC_0123456789abcdef.fedcba9876543210.AbCdEf/12+34==", autoSync = true)
    private val wanted = ArrayList<Boolean>()
    private lateinit var app: OpenConnectApp

    @Before
    fun install() {
        app = ApplicationProvider.getApplicationContext()
        app.deleteDatabase("openconnect.db")
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
    }

    private fun containerWith(h: nl.tufein.openconnect.core.garmin.GarminHttp = http) {
        app.containerOverride = AppContainer(
            SqliteRepository(app),
            SettingsStore(app),
            FakeUsb { OpenedWatch(nl.tufein.openconnect.core.watch.InMemoryWatchSource("W", emptyMap())) { } },
            FakeBle(),
            garminHttp = h,
            garminSettings = settings,
            garminNow = { now },
            backgroundSync = { wanted.add(it) },
        )
    }

    private fun work(attempt: Int = 0): ListenableWorker =
        TestListenableWorkerBuilder<GarminSyncWorker>(app as Context).setRunAttemptCount(attempt).build()

    private fun run(attempt: Int = 0) = runBlocking { work(attempt).startWork().get() }

    // ------------------------------------------------------------------------------- the worker

    @Test
    fun `the worker syncs and stores what Garmin has`() {
        containerWith()
        assertEquals(ListenableWorker.Result.success(), run())
        val day = app.container.repository.listDaily(LocalDate.parse("2026-11-10"), LocalDate.parse("2026-11-10")).single()
        assertEquals(8421, day.steps)
        assertEquals(now.toEpochMilli(), settings.lastSyncMs)
    }

    @Test
    fun `the worker does nothing, successfully, when auto-sync is off or just ran`() {
        settings.autoSync = false
        containerWith()
        assertEquals(ListenableWorker.Result.success(), run())
        assertTrue(http.calls.isEmpty())

        settings.autoSync = true
        settings.lastSyncMs = now.minusSeconds(60).toEpochMilli()
        containerWith()
        assertEquals(ListenableWorker.Result.success(), run())
        assertTrue(http.calls.isEmpty())
    }

    @Test
    fun `without a connection the worker asks to be tried again, but not for ever`() {
        containerWith(FakeGarminHttp.networkDown())
        assertEquals(ListenableWorker.Result.retry(), run(attempt = 0))
        assertEquals(ListenableWorker.Result.retry(), run(attempt = 2))
        // The next scheduled run is a better time to try than a fourth backoff.
        assertEquals(ListenableWorker.Result.success(), run(attempt = 3))
    }

    @Test
    fun `a refused key makes the worker give up`() {
        http.status("POST", "/personal-api-key/token", 401)
        containerWith()
        assertEquals(ListenableWorker.Result.failure(), run())
        assertEquals(false, wanted.last(), "and the job is cancelled until a new key is saved")
    }

    // ------------------------------------------------------------------------------ the schedule

    private fun infos() = WorkManager.getInstance(app).getWorkInfosForUniqueWork(WorkManagerBackgroundSync.NAME).get()

    @Test
    fun `turning it on schedules one periodic job that needs a connection, turning it off cancels it`() {
        val sync = WorkManagerBackgroundSync(app)
        sync.set(true)
        val first = infos().single()
        assertEquals(WorkInfo.State.ENQUEUED, first.state)
        assertEquals(NetworkType.CONNECTED, first.constraints.requiredNetworkType)
        assertTrue(first.constraints.requiresBatteryNotLow())
        assertEquals(TimeUnit.HOURS.toMillis(3), first.periodicityInfo!!.repeatIntervalMillis)

        sync.set(true) // again, for example at the next start of the app
        assertEquals(listOf(first.id), infos().map { it.id }, "the existing job is kept, not doubled or reset")

        sync.set(false)
        assertEquals(WorkInfo.State.CANCELLED, infos().single().state)

        sync.set(true)
        assertTrue(infos().any { it.state == WorkInfo.State.ENQUEUED })
        assertEquals(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build(), infos().last().constraints)
    }

    @Test
    fun `the controller asks the phone for the job through the container`() {
        containerWith()
        app.container // creating the container starts the controller
        assertEquals(listOf(true), wanted, "there is a key and auto-sync is on")
    }
}
