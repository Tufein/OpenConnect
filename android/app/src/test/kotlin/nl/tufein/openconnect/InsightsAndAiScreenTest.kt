package nl.tufein.openconnect

import android.content.Context
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import nl.tufein.openconnect.ai.FakeAiTransport
import nl.tufein.openconnect.core.reports.AiVendor
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The screens new in v0.5.0, on the JVM: Gemini and Claude keys in Settings, the coach, trophies, readiness, the sync
 * status at the top of Home, searching the activities and the theme. A fake provider answers; what this cannot show
 * is the real Claude or Gemini service, and the phone's keystore.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi", application = OpenConnectApp::class)
class InsightsAndAiScreenTest : AppTestBase() {
    private val transport = FakeAiTransport { FakeAiTransport.gemini("Your sleep looks steady this month.") }
    private val garminSettings = FakeGarminSettings()
    private val geminiKey = "AIzaSyFAKEFAKEFAKEFAKEFAKE0123456789abc"
    private val claudeKey = "sk-ant-api03-FAKE0123456789abcdefghijklmnop"

    private fun launchWithAi() = launchFresh { app ->
        app.containerOverride = AppContainer(
            SqliteRepository(app),
            // Plain preferences stand in for the encrypted ones, which need the phone's keystore.
            SettingsStore(app, secure = { app.getSharedPreferences("secure-test", Context.MODE_PRIVATE) }),
            FakeUsb { OpenedWatch(nl.tufein.openconnect.core.watch.InMemoryWatchSource("W", emptyMap())) { } },
            FakeBle(),
            garminHttp = FakeGarminHttp().also { it.answerLikeGarmin(Instant.now()) },
            garminSettings = garminSettings,
            aiTransport = transport,
        )
    }

    private fun container() = ApplicationProvider.getApplicationContext<OpenConnectApp>().container

    private fun loadDemo() {
        waitForText("Load demo data")
        rule.onNodeWithText("Load demo data").performClick()
        rule.waitUntil(15_000) { !hasText("Load demo data", substring = false) }
        // Let the "Demo data loaded" message go, so the screenshots show the screen and not the message over it.
        rule.waitUntil(15_000) { !hasText("Demo data loaded", substring = true) }
    }

    private fun typeInto(label: String, text: String) {
        if (!hasText(label, substring = false)) {
            rule.onAllNodes(androidx.compose.ui.test.hasScrollAction())[0].performScrollToNode(hasText(label))
        }
        rule.onNode(hasText(label) and hasSetTextAction()).performTextInput(text)
    }

    @Test
    fun aGeminiKeyIsSavedCheckedAndThenTheCoachAnswers() {
        launchWithAi()
        waitForText("Load demo data")
        tab("Settings").performClick()
        waitForText("Appearance")
        // Claude is the default pick; choosing Gemini puts its card in place of Claude's.
        clickText("Gemini")
        rule.waitUntil(5_000) { !hasText("Claude key", substring = false) }
        typeInto("Gemini key", geminiKey)
        clickText("Save Gemini key")
        waitForText("Gemini accepted the key.")
        shot("20-settings-gemini-key-ok")

        assertEquals(geminiKey, container().settings.key(AiVendor.GEMINI))
        assertNull(container().settings.key(AiVendor.CLAUDE), "the other provider's key is untouched")
        // The three key buttons wrap instead of squeezing the last one into a thin strip (seen in the first screenshot).
        scrollToText("Remove key")
        rule.onNodeWithText("Remove key").assertWidthIsAtLeast(90.dp)
        rule.onNodeWithText("Check key").assertWidthIsAtLeast(90.dp)
        val check = transport.calls.first()
        assertEquals(geminiKey, check.headers["x-goog-api-key"], "the key goes in a header, to Google only")
        assertTrue(check.url.startsWith("https://generativelanguage.googleapis.com/"), check.url)
        assertFalse(geminiKey in check.url, "never in the address")

        tab("Insights").performClick()
        waitForText("Coach")
        tab("Coach").performClick()
        waitForText("Answers come from Gemini.")
        rule.onNode(hasSetTextAction()).performTextInput("How did I sleep this month?")
        rule.onNodeWithText("Send").performClick()
        waitForText("Your sleep looks steady this month.", substring = true)
        shot("21-coach-answer")

        val ask = transport.calls.last()
        assertTrue("How did I sleep this month?" in ask.body)
        assertFalse(geminiKey in ask.body, "the key is not part of what is sent")
        assertFalse("\"lat\"" in ask.body, "no GPS in what the coach is told")
    }

    @Test
    fun claudeIsTheDefaultPickAndItsKeyIsKeptSeparately() {
        launchWithAi()
        waitForText("Load demo data")
        tab("Settings").performClick()
        waitForText("Appearance")
        typeInto("Claude key", claudeKey)
        clickText("Save Claude key")
        waitForText("Claude accepted the key.", substring = false)
        assertEquals(claudeKey, container().settings.key(AiVendor.CLAUDE))
        assertEquals("anthropic", container().ai.provider().name)
        assertNotNull(transport.calls.firstOrNull { it.url.contains("anthropic.com") })
    }

    @Test
    fun aKeyThatIsRefusedIsExplained() {
        transport.handler = { FakeAiTransport.status(400, """{"error":{"code":400,"status":"INVALID_ARGUMENT","message":"API key not valid.","details":[{"reason":"API_KEY_INVALID"}]}}""") }
        launchWithAi()
        waitForText("Load demo data")
        tab("Settings").performClick()
        waitForText("Appearance")
        clickText("Gemini")
        typeInto("Gemini key", geminiKey)
        clickText("Save Gemini key")
        waitForText("Gemini did not accept the key", substring = true)
        shot("22-settings-gemini-key-rejected")
        clickText("Remove key")
        rule.waitUntil(5_000) { container().settings.key(AiVendor.GEMINI) == null }
    }

    @Test
    fun theCoachAsksForAKeyWhenThereIsNone() {
        launchWithAi()
        waitForText("Load demo data")
        tab("Insights").performClick()
        tab("Coach").performClick()
        waitForText("The coach needs an AI", substring = true)
        clickText("Open Settings")
        waitForText("Appearance")
    }

    @Test
    fun trophiesShowTheLevelAndHowFarTheNextOnesAre() {
        launchWithAi()
        loadDemo()
        tab("Insights").performClick()
        tab("Trophies").performClick()
        waitForText("trophies won", substring = true)
        waitForText("Press Start")
        assertTrue(hasText("Level ", substring = true))
        shot("23-trophies")
    }

    @Test
    fun homeShowsReadinessAndTheSyncStatus() {
        launchWithAi()
        loadDemo()
        waitForText("Readiness")
        waitForText("Connect your Garmin account")
        shot("24-home-readiness")
        rule.onAllNodes(androidx.compose.ui.test.hasScrollAction())[0].performScrollToNode(hasText("Recent activities"))
        shot("24b-home-lower")
    }

    @Test
    fun homeShowsWhenTheLastSyncWas() {
        garminSettings.key = "GCDC_0123456789abcdef.fedcba9876543210.AbCdEf/12+34=="
        garminSettings.lastSyncMs = System.currentTimeMillis() - 5 * 60_000
        launchWithAi()
        loadDemo()
        waitForText("Synced 5 min ago")
        shot("25-home-synced")
    }

    @Test
    fun theWeekAndTheHealthScreenUseTheSignalColours() {
        launchWithAi()
        loadDemo()
        tab("Insights").performClick()
        waitForText("Week of", substring = true)
        shot("28-insights-week")
        tab("Health").performClick()
        waitForText("Scores")
        scrollToText("Steps")
        shot("29-health")
    }

    @Test
    fun activitiesCanBeSearchedAndSorted() {
        launchWithAi()
        loadDemo()
        tab("Activities").performClick()
        waitForText("Easy Run", substring = true)
        val all = container().repository.countActivities()
        waitForText("$all activities")

        rule.onNode(hasSetTextAction()).performTextInput("Easy Run")
        rule.waitUntil(5_000) { !hasText("$all activities", substring = false) }
        shot("26-activities-search")

        rule.onNode(hasSetTextAction()).performTextInput(" zzzzqq")
        waitForText("Nothing matches.")
        assertTrue(hasText("0 activities", substring = false))
    }

    @Test
    fun thePickedThemeIsRemembered() {
        launchWithAi()
        waitForText("Load demo data")
        tab("Settings").performClick()
        waitForText("Appearance")
        clickText("Light")
        rule.waitUntil(5_000) { container().settings.themeMode == "light" }
        shot("27-settings-light")
        clickText("Dark")
        rule.waitUntil(5_000) { container().settings.themeMode == "dark" }
    }

    @Test
    @Config(qualifiers = "nl-w411dp-h891dp-notnight-xxhdpi")
    fun theNewScreensAreInDutchToo() {
        launchWithAi()
        waitForText("Demodata laden")
        tab("Inzichten").performClick()
        tab("Coach").performClick()
        waitForText("De coach heeft een AI nodig", substring = true)
        shot("nl-light-coach")
        tab("Instellingen").performClick()
        waitForText("Uiterlijk")
        scrollToText("Claude-key opslaan")
        assertTrue(hasText("Claude-key opslaan", substring = false))
        assertFalse(hasText("Gemini-key opslaan", substring = false), "only the AI that was picked is shown")
    }
}
