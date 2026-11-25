package nl.tufein.openconnect.ui

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import nl.tufein.openconnect.AppContainer
import nl.tufein.openconnect.OpenConnectApp
import nl.tufein.openconnect.core.Activity
import nl.tufein.openconnect.core.BodyEntry
import nl.tufein.openconnect.core.DailySummary
import nl.tufein.openconnect.core.Sport
import nl.tufein.openconnect.core.SyncLogEntry
import nl.tufein.openconnect.core.WaterEntry
import nl.tufein.openconnect.core.WeeklyGoals
import nl.tufein.openconnect.core.oura.OuraTokens
import nl.tufein.openconnect.core.reports.AiVendor
import nl.tufein.openconnect.data.SettingsStore
import nl.tufein.openconnect.data.SqliteRepository
import nl.tufein.openconnect.watch.FakeBle
import nl.tufein.openconnect.watch.FakeUsb
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/** *Delete all data* empties the database and the settings that hold user data, and leaves keys and preferences alone. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = OpenConnectApp::class)
class AppViewModelClearAllTest {
    private val app get() = ApplicationProvider.getApplicationContext<OpenConnectApp>()

    @After
    fun close() {
        app.containerOverride = null
        app.deleteDatabase("openconnect.db")
    }

    private fun await(message: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + 15_000_000_000L
        while (!condition()) {
            shadowOf(Looper.getMainLooper()).idle()
            if (System.nanoTime() >= deadline) fail("Timed out waiting for $message")
            Thread.sleep(10)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun clearAllDeletesDatabaseAndUserSettingsButKeepsKeysAndPreferences() {
        app.deleteDatabase("openconnect.db")
        app.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
        app.getSharedPreferences("secure-clear-test", Context.MODE_PRIVATE).edit().clear().commit()
        val settings = SettingsStore(app, secure = { app.getSharedPreferences("secure-clear-test", Context.MODE_PRIVATE) })
        settings.journal = "2026-05-01:good"
        settings.gear = "shoes"
        settings.trainingPlan = "plan"
        settings.raceGoal = "race"
        settings.maxHr = 191
        settings.weeklyGoals = WeeklyGoals(200, 50, 5)
        settings.workouts = "workouts"
        settings.healthLedger.remember(mapOf("openconnect-a1" to "f1"))
        settings.nudges.sent = listOf("2026-05-01:goal")
        settings.onboarded = true
        settings.themeMode = "dark"
        settings.appLanguage = "nl"
        settings.setKey(AiVendor.CLAUDE, "sk-ant-claude-key-0123456789")
        settings.garmin.key = "GCDC_0123456789abcdef.fedcba9876543210.AbCdEf"
        settings.garmin.autoSync = false
        settings.oura.tokens = OuraTokens("access-token", "refresh-token", Instant.parse("2026-05-02T08:00:00Z"))

        val container = AppContainer(
            repository = SqliteRepository(app),
            settings = settings,
            usb = FakeUsb { error("no watch in this test") },
            ble = FakeBle(),
        )
        container.syncLog.record(SyncLogEntry(Instant.parse("2026-05-01T07:00:00Z"), "garmin", ok = true, added = 2))
        app.containerOverride = container

        val today = LocalDate.now()
        val repo = container.repository
        repo.upsertActivity(
            Activity(
                id = "clear:1",
                source = "import",
                name = "Run",
                sport = Sport.RUNNING,
                startTime = Instant.now(),
                durationS = 1800.0,
                distanceM = 5000.0,
                elevationGainM = 10.0,
            ),
        )
        repo.upsertDaily(DailySummary(date = today, steps = 1234))
        repo.upsertBody(BodyEntry(today, 78_400, 18.5))
        repo.upsertWater(WaterEntry(today, 1_750))

        val vm = AppViewModel(app)
        vm.clearAll()

        await("the clear to finish") {
            repo.listBody(today, today).isEmpty() &&
                repo.listWater(today, today).isEmpty() &&
                settings.journal.isEmpty() &&
                vm.goals.value == WeeklyGoals()
        }

        assertEquals(emptyList(), repo.listActivities(), "activities are deleted")
        assertEquals(emptyList(), repo.listDaily(today, today), "daily summaries are deleted")
        assertEquals(emptyList(), repo.listBody(today, today), "body weight is deleted")
        assertEquals(emptyList(), repo.listWater(today, today), "water is deleted")

        assertEquals("", settings.gear)
        assertEquals("", settings.trainingPlan)
        assertEquals("", settings.raceGoal)
        assertEquals(0, settings.maxHr)
        assertEquals(WeeklyGoals(), settings.weeklyGoals)
        assertEquals("", settings.workouts)
        assertEquals("", settings.syncLogText)
        assertTrue(container.syncLog.entries.value.isEmpty(), "the in-memory sync log is emptied too")
        assertEquals(emptyMap(), settings.healthLedger.fingerprints())
        assertEquals(emptyList(), settings.nudges.sent)

        assertTrue(settings.onboarded, "onboarding is a preference, not data")
        assertEquals("dark", settings.themeMode)
        assertEquals("nl", settings.appLanguage)
        assertEquals("sk-ant-claude-key-0123456789", settings.key(AiVendor.CLAUDE), "the key vault has its own clear flow")
        assertEquals("GCDC_0123456789abcdef.fedcba9876543210.AbCdEf", settings.garmin.key)
        assertEquals("access-token", settings.oura.tokens?.access)
        assertEquals("refresh-token", settings.oura.tokens?.refresh)
    }
}
