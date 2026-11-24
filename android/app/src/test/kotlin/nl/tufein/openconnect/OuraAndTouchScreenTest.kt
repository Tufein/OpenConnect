package nl.tufein.openconnect

import android.content.Context
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ApplicationProvider
import nl.tufein.openconnect.core.oura.OuraAuth
import nl.tufein.openconnect.core.oura.OuraHttp
import nl.tufein.openconnect.core.oura.OuraResponse
import nl.tufein.openconnect.data.SettingsStore
import nl.tufein.openconnect.data.SqliteRepository
import nl.tufein.openconnect.watch.FakeBle
import nl.tufein.openconnect.watch.FakeUsb
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Answers like Oura: a token for the login, then one day, one night and one run. Records every address asked. */
class FakeOura : OuraHttp {
    val urls = ArrayList<String>()
    val forms = ArrayList<String?>()
    private val day = LocalDate.now().minusDays(1).toString()

    override fun send(method: String, url: String, headers: Map<String, String>, form: String?, timeoutMs: Int): OuraResponse {
        urls += url
        forms += form
        if (url == OuraAuth.TOKEN_URL) return OuraResponse(200, """{"access_token":"access-FAKE","expires_in":86400,"refresh_token":"refresh-FAKE"}""")
        val body = when (url.substringAfter("usercollection/").substringBefore('?')) {
            "daily_activity" -> """{"data":[{"day":"$day","steps":7777,"active_calories":300}]}"""
            "sleep" -> """{"data":[{"day":"$day","type":"long_sleep","total_sleep_duration":26000,"lowest_heart_rate":48,"average_hrv":55}]}"""
            "workout" -> """{"data":[{"id":"w9","activity":"running","start_datetime":"${day}T18:00:00+00:00","end_datetime":"${day}T18:40:00+00:00","distance":6000}]}"""
            else -> """{"data":[]}"""
        }
        return OuraResponse(200, body)
    }
}

/**
 * v0.16.0 on the JVM: the Oura login and sync (with a fake Oura), tiles that open their chart, charts that can be read
 * by touch, and only one AI on screen. The real Oura service and the browser round trip are not part of this.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi", application = OpenConnectApp::class)
class OuraAndTouchScreenTest : AppTestBase() {
    private val oura = FakeOura()

    private fun launchWithOura(onboarded: Boolean = true, ring: Boolean = true) = launchFresh(onboarded, ring) { app ->
        app.containerOverride = AppContainer(
            SqliteRepository(app),
            SettingsStore(app, secure = { app.getSharedPreferences("secure-test", Context.MODE_PRIVATE) }),
            FakeUsb { throw IllegalStateException() },
            FakeBle(),
            ouraHttp = oura,
        )
    }

    private fun container() = ApplicationProvider.getApplicationContext<OpenConnectApp>().container

    @Test
    fun anOuraApplicationIsSavedTheLoginFinishedAndTheRingSynced() {
        launchWithOura()
        waitForText("Load demo data")
        tab("Oura").performClick()
        waitForText("1. Make an application at Oura")
        rule.onNode(hasText("Client ID") and hasSetTextAction()).performTextInput("client-1")
        rule.onNode(hasText("Client secret") and hasSetTextAction()).performTextInput("secret-FAKE")
        clickText("Save")
        scrollToText("3. Log in at Oura")
        waitForText("3. Log in at Oura")
        shot("38-oura-setup")
        // The browser part: what "Log in at Oura" opens, and the address Oura sends the browser back to.
        val login = assertNotNull(container().oura.loginUrl())
        val p = OuraAuth.params(login)
        assertEquals("openconnect://oura", p["redirect_uri"])
        assertFalse("secret-FAKE" in login)
        scrollToText("Address after the login")
        rule.onNode(hasText("Address after the login") and hasSetTextAction()).performTextInput("openconnect://oura?code=abc&state=${p["state"]}")
        clickText("Finish the login")
        scrollToTop() // the setup list left the dashboard scrolled to the bottom
        // The ring's own dashboard on top of the tab, the link and what the sync brought below it.
        waitForText("Readiness")
        // The day's three scores live in the hero's orbs; the duplicate tile row under it is gone.
        rule.onNodeWithTag("oura-hero-orbs").assertIsDisplayed()
        rule.onNodeWithTag("oura-section-night").assertIsDisplayed()
        assertTrue(
            rule.onAllNodes(hasTestTag("oura-section-scores")).fetchSemanticsNodes().isEmpty(),
            "the duplicate score row is gone",
        )
        scrollToText("7:13 asleep", substring = true)
        shot("39-oura-tab")
        scrollToText("1 days, 1 nights and 1 workouts updated")
        assertTrue(oura.forms.first()!!.contains("code_verifier="), "PKCE verifier sent with the code")
        val secure = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("secure-test", Context.MODE_PRIVATE)
        assertEquals("access-FAKE", secure.getString("oura_access", null), "the token is kept with the other secrets")
        val day = LocalDate.now().minusDays(1)
        val stored = container().repository.listOuraDays(day, day).single()
        assertEquals(7777, stored.steps)
        assertEquals("w9", stored.workouts.single().id)
        assertTrue(container().repository.listDaily(day, day).isEmpty(), "the ring is kept apart from the watch's days")
        assertEquals(null, container().repository.getActivity("oura-w9"), "and from the activities")
        scrollToText("Workouts from the ring")
    }

    @Test
    fun theFirstStartAsksForTheDevicesAndShowsOnlyWhatWasChosen() {
        launchWithOura(onboarded = false, ring = false)
        waitForText("Welcome to OpenConnect")
        shot("41-onboarding")
        clickText("Garmin") // off: this person only has a ring
        clickText("Oura Ring")
        clickText("Start")
        waitForText("Load demo data")
        assertFalse(hasText("Connect your Garmin account", substring = true), "no Garmin prompts for someone without one")
        assertFalse(hasText("Garmin", substring = false))
        val settings = container().settings
        assertTrue(settings.onboarded && settings.useOura && !settings.useGarmin)
        tab("Oura").performClick()
        waitForText("1. Make an application at Oura")
        tab("Settings").performClick()
        scrollToText("Your devices")
        clickText("Oura Ring") // switch the ring off again
        rule.waitUntil(5_000) { !hasText("Oura", substring = false) }
        assertFalse(settings.useOura)
    }

    @Test
    fun withoutTheRingThereIsNoOuraTabAndNoOuraCall() {
        launchWithOura(ring = false)
        waitForText("Load demo data")
        assertFalse(hasText("Oura", substring = false), "no Oura tab for someone who did not choose the ring")
        assertTrue(oura.urls.isEmpty())
    }

    @Test
    @Config(qualifiers = "nl-w411dp-h891dp-notnight-xxhdpi")
    fun theDemoRingDaysFillTheOuraTabAndSixTabsFitInDutch() {
        launchWithOura()
        waitForText("Demodata laden")
        rule.onNodeWithText("Demodata laden").performClick()
        rule.waitUntil(45_000) { !hasText("Demodata laden", substring = false) }
        tab("Oura").performClick()
        waitForText("Paraatheid")
        waitForText("Afgelopen nacht")
        rule.waitUntil(45_000) { !hasText("Demodata geladen", substring = true) }
        shot("nl-42-oura-tab")
        // With six tabs the bar uses short words, and none of them is cut off.
        for (label in listOf("Overzicht", "Sport", "Gezond", "Inzichten", "Oura", "Opties")) {
            tab(label).assertIsDisplayed()
            val layout = ArrayList<androidx.compose.ui.text.TextLayoutResult>()
            rule.onNode(hasText(label) and androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Role, androidx.compose.ui.semantics.Role.Tab)), useUnmergedTree = true)
                .fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action!!.invoke(layout)
            val l = layout.single()
            assertTrue(l.multiParagraph.maxIntrinsicWidth <= l.layoutInput.constraints.maxWidth, "\"$label\" fits its tab")
        }
        tab("Opties").performClick()
        scrollToText("Je toestellen")
        shot("nl-43-settings-devices")
    }

    @Test
    fun aTileOpensItsChartAndAChartIsReadByTouch() {
        launchWithOura()
        waitForText("Load demo data")
        rule.onNodeWithText("Load demo data").performClick()
        rule.waitUntil(45_000) { !hasText("Load demo data", substring = false) }
        rule.waitUntil(45_000) { !hasText("Demo data loaded", substring = true) }
        scrollToText("Resting heart rate") // a tile further down Home
        // The tile can end up behind the navigation bar after the battery card; click through its semantics action.
        clickText("Resting heart rate")
        scrollToText("Latest:", substring = true) // the card now sits further down, so scroll to it
        waitForText("Latest:", substring = true) // the resting heart rate chart on the Health screen
        val hints = rule.onAllNodesWithText("Tap or slide along the chart to read a day.").fetchSemanticsNodes().size
        assertTrue(hints >= 1)
        // The chart is composed a little beyond the viewport; scroll the chart node itself into view, then tap it.
        rule.onNode(hasContentDescription("Resting heart rate", substring = true)).performScrollTo()
        rule.onNode(hasContentDescription("Resting heart rate", substring = true)).performTouchInput { click(center) }
        rule.waitUntil(5_000) { rule.onAllNodesWithText(" bpm", substring = true).fetchSemanticsNodes().size >= 2 }
        assertEquals(hints - 1, rule.onAllNodesWithText("Tap or slide along the chart to read a day.").fetchSemanticsNodes().size)
        shot("40-chart-touch")
    }
}
