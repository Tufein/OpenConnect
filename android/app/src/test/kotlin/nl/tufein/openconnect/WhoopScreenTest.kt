package nl.tufein.openconnect

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.test.core.app.ApplicationProvider
import nl.tufein.openconnect.core.Activity
import nl.tufein.openconnect.core.Sport
import nl.tufein.openconnect.core.oura.OuraAuth
import nl.tufein.openconnect.core.oura.OuraResponse
import nl.tufein.openconnect.core.whoop.WhoopAuth
import nl.tufein.openconnect.core.whoop.WhoopDay
import nl.tufein.openconnect.core.whoop.WhoopHttp
import nl.tufein.openconnect.core.whoop.WhoopWorkout
import nl.tufein.openconnect.data.SettingsStore
import nl.tufein.openconnect.data.SqliteRepository
import nl.tufein.openconnect.watch.FakeBle
import nl.tufein.openconnect.watch.FakeUsb
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Answers like WHOOP: a token for the login, then one cycle, one night, one recovery and one run, all of yesterday. */
class FakeWhoop : WhoopHttp {
    val urls = ArrayList<String>()
    val forms = ArrayList<String?>()
    private val day = LocalDate.now().minusDays(1)

    override fun send(method: String, url: String, headers: Map<String, String>, form: String?, timeoutMs: Int): OuraResponse {
        urls += url
        forms += form
        if (url == WhoopAuth.TOKEN_URL) return OuraResponse(200, """{"access_token":"access-FAKE","expires_in":3600,"refresh_token":"refresh-FAKE"}""")
        val body = when (url.substringAfter("/developer/v2/").substringBefore('?')) {
            "cycle" -> """{"records":[{"id":501,"start":"${day.minusDays(1)}T22:40:00.000Z","end":null,"timezone_offset":"+00:00","score_state":"SCORED","score":{"strain":12.3,"kilojoule":9500.0,"average_heart_rate":68,"max_heart_rate":172}}],"next_token":null}"""
            "activity/sleep" -> """{"records":[{"id":"s-1","cycle_id":501,"start":"${day.minusDays(1)}T22:40:00.000Z","end":"${day}T06:40:00.000Z","timezone_offset":"+00:00","nap":false,"score_state":"SCORED","score":{"stage_summary":{"total_in_bed_time_milli":28800000,"total_awake_time_milli":1800000,"total_light_sleep_time_milli":14400000,"total_slow_wave_sleep_time_milli":5400000,"total_rem_sleep_time_milli":7200000},"sleep_needed":{"baseline_milli":27000000,"need_from_sleep_debt_milli":1800000,"need_from_recent_strain_milli":900000,"need_from_recent_nap_milli":0},"respiratory_rate":15.1,"sleep_performance_percentage":92,"sleep_consistency_percentage":85,"sleep_efficiency_percentage":94}}],"next_token":null}"""
            "recovery" -> """{"records":[{"cycle_id":501,"sleep_id":"s-1","score_state":"SCORED","score":{"recovery_score":81,"resting_heart_rate":52,"hrv_rmssd_milli":64.2,"spo2_percentage":96.6,"skin_temp_celsius":33.7}}],"next_token":null}"""
            "activity/workout" -> """{"records":[{"id":"w-9","start":"${day}T18:00:00.000Z","end":"${day}T18:40:00.000Z","timezone_offset":"+00:00","sport_name":"running","score_state":"SCORED","score":{"strain":9.4,"average_heart_rate":151,"max_heart_rate":178,"kilojoule":2092.0,"distance_meter":6000.0}}],"next_token":null}"""
            else -> """{"records":[],"next_token":null}"""
        }
        return OuraResponse(200, body)
    }
}

/**
 * v0.49.0 on the JVM: the WHOOP application is saved, the login finished and the band synced (with a fake WHOOP); the WHOOP tab
 * shows recovery, strain and sleep apart from the watch and the ring; a workout only the band recorded is listed among the
 * activities without being stored as one. The real WHOOP service and the browser round trip are not part of this.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi", application = OpenConnectApp::class)
class WhoopScreenTest : AppTestBase() {
    private val whoop = FakeWhoop()
    private val zone = ZoneId.systemDefault()

    private fun launchWithWhoop(band: Boolean = true, oura: Boolean = false, seed: (SqliteRepository) -> Unit = {}) = launchFresh(oura = oura, whoop = band) { app ->
        val container = AppContainer(
            SqliteRepository(app),
            SettingsStore(app, secure = { app.getSharedPreferences("secure-test", Context.MODE_PRIVATE) }),
            FakeUsb { throw IllegalStateException() },
            FakeBle(),
            whoopHttp = whoop,
        )
        app.containerOverride = container
        seed(container.repository)
    }

    private fun container() = ApplicationProvider.getApplicationContext<OpenConnectApp>().container

    @Test
    fun aWhoopApplicationIsSavedTheLoginFinishedAndTheBandSynced() {
        launchWithWhoop()
        waitForText("Load demo data")
        tab("WHOOP").performClick()
        waitForText("1. Make an application at WHOOP")
        rule.onNode(hasText("Client ID") and hasSetTextAction()).performTextInput("client-1")
        rule.onNode(hasText("Client secret") and hasSetTextAction()).performTextInput("secret-FAKE")
        clickText("Save")
        waitForText("3. Log in at WHOOP")
        shot("whoop-setup")
        // The browser part: what "Log in at WHOOP" opens, and the address WHOOP sends the browser back to.
        val login = assertNotNull(container().whoop.loginUrl())
        val p = OuraAuth.params(login)
        assertEquals("openconnect://whoop", p["redirect_uri"])
        assertTrue(p.getValue("state").length >= 8)
        assertFalse("secret-FAKE" in login)
        scrollToText("Address after the login")
        rule.onNode(hasText("Address after the login") and hasSetTextAction()).performTextInput("openconnect://whoop?code=abc&state=${p["state"]}")
        clickText("Finish the login")
        scrollToTop() // the setup list left the dashboard scrolled to the bottom
        // The band's own dashboard on top of the tab, the link and what the sync brought below it.
        waitForText("7:30 asleep", substring = true)
        scrollToText("Latest day:", substring = true) // the tiles at the top of the tab
        assertTrue(hasText("Recovery", substring = false))
        assertTrue(hasText("81", substring = false), "recovery 81 in its tile")
        assertTrue(hasText("12.3", substring = false), "strain 12.3 in its tile")
        shot("whoop-tab")
        scrollToText("1 days, 1 nights and 1 workouts updated")
        val form = whoop.forms.first()!!
        assertTrue("code=abc" in form && "client_secret=secret-FAKE" in form, form)
        assertFalse("code_verifier" in form, "WHOOP gets no PKCE verifier")
        val secure = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("secure-test", Context.MODE_PRIVATE)
        assertEquals("access-FAKE", secure.getString("whoop_access", null), "the token is kept with the other secrets")
        val day = LocalDate.now().minusDays(1)
        val stored = container().repository.listWhoopDays(day, day).single()
        assertEquals(81, stored.recovery)
        assertEquals("w-9", stored.workouts.single().id)
        assertTrue(container().repository.listDaily(day, day).isEmpty(), "WHOOP is kept apart from the watch's days")
        assertTrue(container().repository.listOuraDays(day, day).isEmpty(), "and from the ring's")
        assertNull(container().repository.getActivity("whoop:w-9"), "and from the activities")
        scrollToText("Workouts from the band")
    }

    @Test
    fun withoutChoosingTheBandThereIsNoWhoopTabAndNoWhoopCall() {
        launchWithWhoop(band = false)
        waitForText("Load demo data")
        assertFalse(hasText("WHOOP", substring = false), "no WHOOP tab for someone who did not choose the band")
        assertTrue(whoop.urls.isEmpty())
    }

    @Test
    fun theFirstStartOffersTheBandAndSettingsCanSwitchItOff() {
        launchFresh(onboarded = false) { app ->
            app.containerOverride = AppContainer(
                SqliteRepository(app),
                SettingsStore(app, secure = { app.getSharedPreferences("secure-test", Context.MODE_PRIVATE) }),
                FakeUsb { throw IllegalStateException() },
                FakeBle(),
                whoopHttp = whoop,
            )
        }
        waitForText("Welcome to OpenConnect")
        clickText("WHOOP band")
        clickText("Start")
        waitForText("Load demo data")
        val settings = container().settings
        assertTrue(settings.onboarded && settings.useWhoop && settings.useGarmin && !settings.useOura)
        tab("WHOOP").performClick()
        waitForText("1. Make an application at WHOOP")
        tab("Settings").performClick()
        scrollToText("Your devices")
        clickText("WHOOP band") // switch the band off again
        rule.waitUntil(5_000) { !hasText("WHOOP", substring = false) }
        assertFalse(settings.useWhoop)
    }

    @Test
    @Config(qualifiers = "nl-w411dp-h891dp-notnight-xxhdpi")
    fun theDemoDaysFillTheTabAndSevenTabsFitInDutch() {
        launchWithWhoop(oura = true)
        waitForText("Demodata laden")
        rule.onNodeWithText("Demodata laden").performClick()
        rule.waitUntil(45_000) { !hasText("Demodata laden", substring = false) }
        tab("WHOOP").performClick()
        waitForText("Herstel")
        waitForText("Afgelopen nacht")
        rule.waitUntil(45_000) { !hasText("Demodata geladen", substring = true) }
        shot("nl-whoop-tab")
        // With seven tabs the bar uses short words, and none of them is cut off.
        for (label in listOf("Overzicht", "Sport", "Gezond", "Inzichten", "Oura", "WHOOP", "Opties")) {
            tab(label).assertIsDisplayed()
            val layout = ArrayList<androidx.compose.ui.text.TextLayoutResult>()
            rule.onNode(hasText(label) and androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Role, androidx.compose.ui.semantics.Role.Tab)), useUnmergedTree = true)
                .fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action!!.invoke(layout)
            val l = layout.single()
            assertTrue(l.multiParagraph.maxIntrinsicWidth <= l.layoutInput.constraints.maxWidth, "\"$label\" fits its tab")
        }
    }

    @Test
    fun aWorkoutOnlyTheBandRecordedIsListedButNeverStoredAsAnActivity() {
        var repository: SqliteRepository? = null
        val day = LocalDate.now(zone).minusDays(2)
        val start = day.atTime(12, 30).atZone(zone).toInstant()
        val walk = WhoopWorkout("w-7", "pilates", start.toEpochMilli(), start.plusSeconds(1800).toEpochMilli(), strain = 5.2, avgHr = 104, maxHr = 121, calories = 150, distanceM = 2_400.0)
        launchWithWhoop { repo ->
            repository = repo
            repo.upsertWhoopDay(WhoopDay(day.toString(), recovery = 70, strain = 8.0, workouts = listOf(walk)))
            val run = Activity(id = "run1", source = "garmin-ble", name = "Evening run", sport = Sport.RUNNING, startTime = day.atTime(18, 0).atZone(zone).toInstant(), durationS = 3600.0, distanceM = 9_800.0)
            repo.upsertActivity(run)
        }
        tab("Activities").performClick()
        waitForText("Pilates")
        assertTrue(hasText("2 activities", substring = false))
        assertTrue(hasText("WHOOP", substring = true))
        clickText("Pilates")
        waitForText("Recorded by the WHOOP only", substring = true)
        assertFalse(hasText("Delete activity", substring = false), "a WHOOP workout cannot be deleted: it is not stored")
        shot("whoop-workout-row")
        // Only what a watch or a file recorded is in the activities table, so totals and trophies do not count the band's.
        assertEquals(listOf("run1"), repository!!.listActivities(limit = 100).map { it.id })
        assertEquals(Instant.ofEpochMilli(walk.startMs), repository!!.listWhoopDays(day, day).single().workouts.single().start)
    }

    @Test
    fun theDayTabOffersTheBandAsASourceNextToTheWatch() {
        val day = LocalDate.now(zone).minusDays(1)
        launchWithWhoop { repo ->
            repo.upsertWhoopDay(WhoopDay(day.toString(), recovery = 77, strain = 10.4, sleepPerformance = 90, sleepS = 27_000, restingHr = 51, hrv = 66.0))
        }
        waitForText("Load demo data")
        tab("Insights").performClick()
        waitForText("Week of", substring = true)
        tab("Day").performClick()
        // The watch is the first source and the band the second; both are in use, so the dropdown is there.
        waitForText("Source")
        rule.onNode(hasText("Garmin") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("WHOOP") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(hasText("WHOOP") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Strain")
        assertTrue(hasText("10.4", substring = false), "the day's strain is shown")
        assertTrue(hasText("Recovery", substring = false).not(), "recovery stands in for readiness, which keeps its name")
        shot("whoop-day")
    }
}
