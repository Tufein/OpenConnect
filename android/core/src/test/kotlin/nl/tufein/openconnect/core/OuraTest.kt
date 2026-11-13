package nl.tufein.openconnect.core

import nl.tufein.openconnect.core.oura.OuraAuth
import nl.tufein.openconnect.core.oura.OuraClient
import nl.tufein.openconnect.core.oura.OuraException
import nl.tufein.openconnect.core.oura.OuraHttp
import nl.tufein.openconnect.core.oura.OuraResponse
import nl.tufein.openconnect.core.oura.OuraStore
import nl.tufein.openconnect.core.oura.OuraSync
import nl.tufein.openconnect.core.oura.OuraTokens
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MemoryOuraStore : OuraStore {
    override val canStoreSecrets = true
    override var clientId: String? = "client-1"
    override var clientSecret: String? = "secret-FAKE"
    override var redirectUri: String = OuraAuth.DEFAULT_REDIRECT
    override var tokens: OuraTokens? = null
    override var pendingLogin: String? = null
}

/** Answers like Oura from memory and records what was asked. */
class FakeOuraHttp : OuraHttp {
    class Call(val method: String, val url: String, val headers: Map<String, String>, val form: String?)

    val calls = ArrayList<Call>()
    var tokenStatus = 200
    var tokenCount = 0
    var apiStatus: (Call) -> Int = { 200 }
    val collections = HashMap<String, String>()
    var offline = false

    override fun send(method: String, url: String, headers: Map<String, String>, form: String?, timeoutMs: Int): OuraResponse {
        val call = Call(method, url, headers, form)
        calls += call
        if (offline) throw IOException("no connection")
        if (url == OuraAuth.TOKEN_URL) {
            if (tokenStatus != 200) return OuraResponse(tokenStatus, """{"error":"invalid_grant"}""")
            tokenCount++
            return OuraResponse(200, """{"token_type":"bearer","access_token":"access-$tokenCount","expires_in":86400,"refresh_token":"refresh-$tokenCount"}""")
        }
        val status = apiStatus(call)
        if (status != 200) return OuraResponse(status, "{}")
        val name = url.substringAfter("usercollection/").substringBefore('?')
        if ("next_token=" in url) return OuraResponse(200, """{"data":[],"next_token":null}""")
        return OuraResponse(200, collections[name] ?: """{"data":[],"next_token":null}""")
    }
}

class OuraTest {
    private val now = Instant.parse("2026-11-13T12:00:00Z")
    private val store = MemoryOuraStore()
    private val http = FakeOuraHttp()
    private val auth = OuraAuth(http, store) { now }
    private val repo = InMemoryRepository()
    private val sync = OuraSync(OuraClient(http, auth), repo)
    private val today = LocalDate.parse("2026-11-13")

    private fun login() {
        val url = auth.begin()
        val state = OuraAuth.params(url).getValue("state")
        auth.finish("openconnect://oura?code=the-code&state=$state")
    }

    @Test
    fun `the login address carries the client, the redirect, the scopes and a PKCE challenge`() {
        val p = OuraAuth.params(auth.begin())
        assertEquals("code", p["response_type"])
        assertEquals("client-1", p["client_id"])
        assertEquals("openconnect://oura", p["redirect_uri"])
        assertEquals("daily workout spo2 personal", p["scope"])
        assertEquals("S256", p["code_challenge_method"])
        val verifier = store.pendingLogin!!.split(' ')[0]
        assertEquals(OuraAuth.challenge(verifier), p["code_challenge"])
        assertFalse("secret-FAKE" in auth.begin(), "the secret never goes into the browser")
    }

    @Test
    fun `the PKCE challenge matches the example of RFC 7636`() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", OuraAuth.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
    }

    @Test
    fun `finishing the login trades the code for tokens and sends the secret only to the token address`() {
        login()
        assertEquals("access-1", store.tokens?.access)
        assertEquals("refresh-1", store.tokens?.refresh)
        assertNull(store.pendingLogin)
        val call = http.calls.single()
        assertEquals(OuraAuth.TOKEN_URL, call.url)
        val form = OuraAuth.params("x?" + call.form)
        assertEquals("authorization_code", form["grant_type"])
        assertEquals("the-code", form["code"])
        assertEquals("secret-FAKE", form["client_secret"])
        assertTrue(form["code_verifier"]!!.length >= 43)
        assertTrue(call.headers["User-Agent"]!!.startsWith("OpenConnect"))
    }

    @Test
    fun `an address of another login, an error or no login at all is refused`() {
        assertFailsWith<OuraException> { auth.finish("openconnect://oura?code=x&state=y") }
        auth.begin()
        assertEquals(OuraException.Kind.BAD_REDIRECT, assertFailsWith<OuraException> { auth.finish("openconnect://oura?code=x&state=wrong") }.kind)
        assertEquals(OuraException.Kind.BAD_REDIRECT, assertFailsWith<OuraException> { auth.finish("openconnect://oura?error=access_denied") }.kind)
        assertNull(store.tokens)
    }

    @Test
    fun `a token that is about to run out is refreshed and the new single-use refresh token is kept`() {
        login()
        assertEquals("access-1", auth.accessToken())
        store.tokens = store.tokens!!.copy(expiresAt = now.plusSeconds(30))
        assertEquals("access-2", auth.accessToken())
        assertEquals("refresh-2", store.tokens?.refresh)
        assertEquals("refresh-1", OuraAuth.params("x?" + http.calls.last().form)["refresh_token"])
    }

    @Test
    fun `a refused refresh ends the login, and no connection does not`() {
        login()
        store.tokens = store.tokens!!.copy(expiresAt = now)
        http.offline = true
        assertEquals(OuraException.Kind.NETWORK, assertFailsWith<OuraException> { auth.accessToken() }.kind)
        assertTrue(auth.connected, "no connection is not a reason to log out")
        http.offline = false
        http.tokenStatus = 400
        assertEquals(OuraException.Kind.LOGIN_EXPIRED, assertFailsWith<OuraException> { auth.accessToken() }.kind)
        assertFalse(auth.connected)
    }

    private fun ouraData() {
        http.collections["daily_activity"] = """{"data":[{"id":"a1","day":"2026-11-12","steps":8123,"active_calories":410,"equivalent_walking_distance":6200,"medium_activity_time":1800,"high_activity_time":600}],"next_token":"page2"}"""
        http.collections["sleep"] = """{"data":[
            {"id":"s1","day":"2026-11-13","type":"long_sleep","total_sleep_duration":27000,"deep_sleep_duration":5400,"light_sleep_duration":15000,"rem_sleep_duration":6600,"awake_time":1800,"lowest_heart_rate":47,"average_hrv":62,"average_breath":14.5},
            {"id":"s2","day":"2026-11-13","type":"late_nap","total_sleep_duration":1500},
            {"id":"s3","day":"2026-11-12","type":"deleted","total_sleep_duration":30000}],"next_token":null}"""
        http.collections["daily_sleep"] = """{"data":[{"id":"d1","day":"2026-11-13","score":84}],"next_token":null}"""
        http.collections["daily_spo2"] = """{"data":[{"id":"o1","day":"2026-11-13","spo2_percentage":{"average":96.6}}],"next_token":null}"""
        http.collections["workout"] = """{"data":[{"id":"w1","activity":"running","label":null,"day":"2026-11-12","start_datetime":"2026-11-12T07:00:00+01:00","end_datetime":"2026-11-12T07:45:00+01:00","distance":7500.0,"calories":520.0}],"next_token":null}"""
    }

    @Test
    fun `a sync fills days, nights and workouts and follows the next page`() {
        login()
        ouraData()
        val report = sync.sync(7, today, preferSleep = false)
        assertEquals(2, report.daysUpdated)
        assertEquals(1, report.nightsUpdated)
        assertEquals(1, report.activitiesAdded)
        val day = repo.listDaily(LocalDate.parse("2026-11-12"), LocalDate.parse("2026-11-12")).single()
        assertEquals(8123, day.steps)
        assertEquals(410, day.activeKcal)
        assertEquals(30, day.moderateIntensityMin)
        assertEquals(10, day.vigorousIntensityMin)
        val morning = repo.listDaily(today, today).single()
        assertEquals(47, morning.restingHr)
        assertEquals(62, morning.hrvMs)
        assertEquals(97, morning.spo2Avg)
        val night = repo.listSleep(today, today).single()
        assertEquals(27000, night.durationS)
        assertEquals(84, night.score)
        val run = repo.getActivity("oura-w1")!!
        assertEquals(Sport.RUNNING, run.sport)
        assertEquals(Instant.parse("2026-11-12T06:00:00Z"), run.startTime)
        assertEquals(2700.0, run.durationS)
        assertEquals("Running", run.name)
        assertTrue(http.calls.any { "next_token=page2" in it.url }, "the second page was asked for")
        val get = http.calls.first { it.method == "GET" }
        assertEquals("Bearer access-1", get.headers["Authorization"])
        assertTrue("start_date=2026-11-07" in get.url, get.url)
        assertEquals(OuraSyncReport0, sync.sync(7, today, preferSleep = false), "a second sync changes nothing")
    }

    private val OuraSyncReport0 = nl.tufein.openconnect.core.oura.OuraSyncReport()

    @Test
    fun `what the watch gave stays, unless the ring leads for sleep`() {
        login()
        ouraData()
        repo.upsertDaily(DailySummary(date = LocalDate.parse("2026-11-12"), steps = 9999, restingHr = 50))
        repo.upsertSleep(SleepSummary(date = today, durationS = 20000, score = 70))
        repo.upsertActivity(Activity(id = "garmin-1", name = "Morning run", sport = Sport.RUNNING, startTime = Instant.parse("2026-11-12T06:01:00Z"), durationS = 2700.0))
        val report = sync.sync(7, today, preferSleep = false)
        assertEquals(9999, repo.listDaily(LocalDate.parse("2026-11-12"), LocalDate.parse("2026-11-12")).single().steps)
        assertEquals(410, repo.listDaily(LocalDate.parse("2026-11-12"), LocalDate.parse("2026-11-12")).single().activeKcal, "gaps are filled")
        assertEquals(20000, repo.listSleep(today, today).single().durationS)
        assertEquals(0, report.activitiesAdded, "the same run from the watch is not added twice")
        assertNull(repo.getActivity("oura-w1"))
        assertEquals(1, sync.sync(7, today, preferSleep = true).nightsUpdated)
        assertEquals(27000, repo.listSleep(today, today).single().durationS)
    }

    @Test
    fun `refusals from Oura are told apart`() {
        login()
        http.apiStatus = { 403 }
        assertEquals(OuraException.Kind.FORBIDDEN, assertFailsWith<OuraException> { sync.sync(7, today, false) }.kind)
        http.apiStatus = { 429 }
        assertEquals(OuraException.Kind.RATE_LIMITED, assertFailsWith<OuraException> { sync.sync(7, today, false) }.kind)
        var first = true
        http.apiStatus = { if (first) { first = false; 401 } else 200 }
        sync.sync(7, today, false)
        assertEquals("access-2", store.tokens?.access, "a 401 gets one fresh token and a second try")
        store.tokens = null
        assertEquals(OuraException.Kind.NOT_CONNECTED, assertFailsWith<OuraException> { sync.sync(7, today, false) }.kind)
    }
}
