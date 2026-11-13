package nl.tufein.openconnect

import android.content.Context
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
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

    private fun launchWithOura() = launchFresh { app ->
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
        tab("Settings").performClick()
        clickText("Oura Ring")
        waitForText("1. Make an application at Oura")
        rule.onNode(hasText("Client ID") and hasSetTextAction()).performTextInput("client-1")
        rule.onNode(hasText("Client secret") and hasSetTextAction()).performTextInput("secret-FAKE")
        clickText("Save")
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
        waitForText("Connected")
        waitForText("1 days, 1 nights and 1 workouts updated")
        shot("39-oura-connected")
        assertTrue(oura.forms.first()!!.contains("code_verifier="), "PKCE verifier sent with the code")
        val secure = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("secure-test", Context.MODE_PRIVATE)
        assertEquals("access-FAKE", secure.getString("oura_access", null), "the token is kept with the other secrets")
        val day = LocalDate.now().minusDays(1)
        assertEquals(7777, container().repository.listDaily(day, day).single().steps)
        assertNotNull(container().repository.getActivity("oura-w9"))
    }

    @Test
    fun aTileOpensItsChartAndAChartIsReadByTouch() {
        launchWithOura()
        waitForText("Load demo data")
        rule.onNodeWithText("Load demo data").performClick()
        rule.waitUntil(15_000) { !hasText("Load demo data", substring = false) }
        rule.waitUntil(15_000) { !hasText("Demo data loaded", substring = true) }
        scrollToText("Resting heart rate") // a tile further down Home
        rule.onNodeWithText("Resting heart rate").performClick()
        waitForText("Latest:", substring = true) // the resting heart rate chart on the Health screen
        val hints = rule.onAllNodesWithText("Tap or slide along the chart to read a day.").fetchSemanticsNodes().size
        assertTrue(hints >= 1)
        rule.onNode(hasContentDescription("Resting heart rate")).performTouchInput { click(center) }
        rule.waitUntil(5_000) { rule.onAllNodesWithText(" bpm", substring = true).fetchSemanticsNodes().size >= 2 }
        assertEquals(hints - 1, rule.onAllNodesWithText("Tap or slide along the chart to read a day.").fetchSemanticsNodes().size)
        shot("40-chart-touch")
    }
}
