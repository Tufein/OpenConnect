package nl.tufein.openconnect

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import nl.tufein.openconnect.data.SettingsStore
import nl.tufein.openconnect.data.SqliteRepository
import nl.tufein.openconnect.garmin.FakeGarminHttp
import nl.tufein.openconnect.garmin.FakeGarminSettings
import nl.tufein.openconnect.watch.FakeBle
import nl.tufein.openconnect.watch.FakeUsb
import nl.tufein.openconnect.watch.OpenedWatch
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Garmin account screen on the JVM: a fake Garmin answers over the same client the phone uses, the key sits in
 * memory instead of Android's encrypted preferences. What this cannot show is Garmin's real server and the phone's
 * keystore.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi", application = OpenConnectApp::class)
class GarminScreenTest : AppTestBase() {
    private val now = Instant.parse("2026-11-12T10:00:00Z")
    private val http = FakeGarminHttp().also { it.answerLikeGarmin(now) }
    private val settings = FakeGarminSettings()
    private val key = "GCDC_0123456789abcdef.fedcba9876543210.AbCdEf/12+34=="

    private fun launchWithGarmin() = launchFresh { app ->
        app.containerOverride = AppContainer(
            SqliteRepository(app),
            SettingsStore(app),
            FakeUsb { OpenedWatch(nl.tufein.openconnect.core.watch.InMemoryWatchSource("W", emptyMap())) { } },
            FakeBle(),
            garminHttp = http,
            garminSettings = settings,
            garminNow = { now },
        )
    }

    private fun repo() = ApplicationProvider.getApplicationContext<OpenConnectApp>().container.repository

    private fun openGarminScreen() {
        waitForText("Garmin account")
        rule.onNodeWithText("Garmin account").performClick()
        waitForText("Your personal key")
    }

    @Test
    fun pastingTheKeyAndSyncingBringsInTheGarminData() {
        launchWithGarmin()
        openGarminScreen()
        shot("16-garmin-empty")

        rule.onNodeWithText("Garmin key").performTextInput(key)
        rule.onNodeWithText("Save").performClick()
        waitForText("Garmin accepted the key.")
        shot("17-garmin-key-ok")
        assertEquals(key, settings.key)
        assertEquals("ApiKey $key", http.calls.first().headers["Authorization"])

        clickText("7 days")
        clickText("Sync now")
        waitForText("Garmin synced")
        shot("18-garmin-synced")
        assertTrue(hasText("Days updated: 2, nights: 1", substring = false))
        assertTrue(hasText("steps: 2 found", substring = false), "per data type, in words")
        assertTrue(hasText("daily summaries: failed", substring = false) || hasText("daily summaries: nothing in this period", substring = false))

        val day = repo().listDaily(LocalDate.parse("2026-11-10"), LocalDate.parse("2026-11-10")).single()
        assertEquals(8421, day.steps)
        assertEquals(1, repo().listSleep(LocalDate.parse("2026-11-11"), LocalDate.parse("2026-11-11")).size)
        assertEquals(7, settings.days)
        assertTrue(settings.lastSyncMs > 0)
    }

    @Test
    fun aKeyGarminRefusesIsExplainedAndCanBeRemoved() {
        http.status("POST", "/personal-api-key/token", 401)
        launchWithGarmin()
        openGarminScreen()

        rule.onNodeWithText("Garmin key").performTextInput(key)
        rule.onNodeWithText("Save").performClick()
        waitForText("Garmin did not accept the key", substring = true)
        shot("19-garmin-key-rejected")

        clickText("Remove key")
        rule.waitUntil(5_000) { settings.key == null }
        assertFalse(hasText("Garmin did not accept the key", substring = true))
    }

    @Test
    fun somethingThatIsNotAKeyIsNotSentAnywhere() {
        launchWithGarmin()
        openGarminScreen()
        rule.onNodeWithText("Garmin key").performTextInput("this is not a key")
        rule.onNodeWithText("Save").performClick()
        waitForText("That does not look like a Garmin key", substring = true)
        assertNull(settings.key)
        assertTrue(http.calls.isEmpty())
    }

    @Test
    fun syncIsNotOfferedWithoutAKeyAndTheTermsAreShown() {
        launchWithGarmin()
        openGarminScreen()
        scrollToText("Sync now")
        assertTrue(hasText("Sync now", substring = false))
        rule.onNodeWithText("Sync now").performScrollTo().assertIsNotEnabled()
        rule.onAllNodes(androidx.compose.ui.test.hasScrollAction())[0]
            .performScrollToNode(androidx.compose.ui.test.hasText("Garmin allows the personal key", substring = true))
        assertTrue(hasText("Garmin allows the personal key", substring = true))
    }

    @Test
    @Config(qualifiers = "nl-w411dp-h891dp-notnight-xxhdpi")
    fun theGarminScreenIsInDutchToo() {
        launchWithGarmin()
        waitForText("Garmin-account")
        rule.onNodeWithText("Garmin-account").performClick()
        waitForText("Je persoonlijke key")
        shot("nl-light-garmin")
        assertTrue(hasText("Garmins key-pagina openen", substring = false))
    }
}
