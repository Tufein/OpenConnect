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
    var tokenHeaders: Map<String, String> = emptyMap()
    var apiHeaders: Map<String, String> = emptyMap()

    override fun send(method: String, url: String, headers: Map<String, String>, form: String?, timeoutMs: Int): OuraResponse {
        val call = Call(method, url, headers, form)
        calls += call
        if (offline) throw IOException("no connection")
        if (url == OuraAuth.TOKEN_URL) {
            if (tokenStatus != 200) return OuraResponse(tokenStatus, """{"error":"invalid_grant"}""", tokenHeaders)
            tokenCount++
            return OuraResponse(200, """{"token_type":"bearer","access_token":"access-$tokenCount","expires_in":86400,"refresh_token":"refresh-$tokenCount"}""")
        }
        val status = apiStatus(call)
        if (status != 200) return OuraResponse(status, "{}", apiHeaders)
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
    fun `a sync stores the ring's days, scores and workouts apart and follows the next page`() {
        login()
        ouraData()
        http.collections["daily_readiness"] = """{"data":[{"id":"r1","day":"2026-11-13","score":81,"temperature_deviation":-0.12}],"next_token":null}"""
        http.collections["daily_activity"] = """{"data":[{"id":"a1","day":"2026-11-12","score":77,"steps":8123,"active_calories":410}],"next_token":"page2"}"""
        val report = sync.sync(7, today)
        assertEquals(2, report.daysUpdated)
        assertEquals(1, report.nightsUpdated)
        assertEquals(1, report.activitiesAdded)
        val yesterday = repo.listOuraDays(LocalDate.parse("2026-11-12"), LocalDate.parse("2026-11-12")).single()
        assertEquals(8123, yesterday.steps)
        assertEquals(77, yesterday.activityScore)
        val run = yesterday.workouts.single()
        assertEquals("Running", run.name)
        assertEquals(Instant.parse("2026-11-12T06:00:00Z"), run.start)
        assertEquals(2700, run.durationS)
        assertEquals(520, run.calories)
        val morning = repo.listOuraDays(today, today).single()
        assertEquals(81, morning.readiness)
        assertEquals(84, morning.sleepScore)
        assertEquals(27000, morning.sleepS)
        assertEquals(47, morning.lowestHr)
        assertEquals(62, morning.averageHrv)
        assertEquals(96.6, morning.spo2Avg)
        assertEquals(-0.12, morning.temperatureDeviation)
        assertEquals(morning, nl.tufein.openconnect.core.oura.OuraDay.fromJson(morning.toJson()), "it survives storage")
        assertTrue(http.calls.any { "next_token=page2" in it.url }, "the second page was asked for")
        val get = http.calls.first { it.method == "GET" }
        assertEquals("Bearer access-1", get.headers["Authorization"])
        assertTrue("start_date=2026-11-07" in get.url, get.url)
        assertEquals(OuraSyncReport0, sync.sync(7, today), "a second sync changes nothing")
    }

    private val OuraSyncReport0 = nl.tufein.openconnect.core.oura.OuraSyncReport()

    @Test
    fun `the watch's data is never touched by the ring`() {
        login()
        ouraData()
        val watchDay = DailySummary(date = LocalDate.parse("2026-11-12"), steps = 9999, restingHr = 50)
        val watchNight = SleepSummary(date = today, durationS = 20000, score = 70)
        repo.upsertDaily(watchDay)
        repo.upsertSleep(watchNight)
        sync.sync(7, today)
        assertEquals(listOf(watchDay), repo.listDaily(LocalDate.parse("2026-11-01"), today))
        assertEquals(listOf(watchNight), repo.listSleep(LocalDate.parse("2026-11-01"), today))
        assertTrue(repo.listActivities().isEmpty(), "a ring workout is not added to the activities")
        assertEquals(2, repo.listOuraDays(LocalDate.parse("2026-11-01"), today).size)
        repo.clearAll()
        assertTrue(repo.listOuraDays(LocalDate.parse("2026-11-01"), today).isEmpty(), "clearing everything clears the ring's days too")
    }

    @Test
    fun `refusals from Oura are told apart`() {
        login()
        http.apiStatus = { 403 }
        assertEquals(OuraException.Kind.FORBIDDEN, assertFailsWith<OuraException> { sync.sync(7, today) }.kind)
        http.apiStatus = { 429 }
        assertEquals(OuraException.Kind.RATE_LIMITED, assertFailsWith<OuraException> { sync.sync(7, today) }.kind)
        var first = true
        http.apiStatus = { if (first) { first = false; 401 } else 200 }
        sync.sync(7, today)
        assertEquals("access-2", store.tokens?.access, "a 401 gets one fresh token and a second try")
        store.tokens = null
        assertEquals(OuraException.Kind.NOT_CONNECTED, assertFailsWith<OuraException> { sync.sync(7, today) }.kind)
    }

    // ------------------------------------------------------------------------ hiccups, rate limits and the login

    @Test
    fun `a rate limit or a server error on the refresh keeps the login, only a real refusal ends it`() {
        login()
        for ((status, kind) in listOf(
            429 to OuraException.Kind.RATE_LIMITED,
            503 to OuraException.Kind.SERVER,
            500 to OuraException.Kind.SERVER,
            403 to OuraException.Kind.SERVER,
            408 to OuraException.Kind.SERVER,
        )) {
            store.tokens = store.tokens!!.copy(expiresAt = now)
            http.tokenStatus = status
            assertEquals(kind, assertFailsWith<OuraException> { auth.accessToken() }.kind, "status $status")
            assertTrue(auth.connected, "status $status is no reason to log out")
        }
        store.tokens = store.tokens!!.copy(expiresAt = now)
        http.tokenStatus = 401
        assertEquals(OuraException.Kind.LOGIN_EXPIRED, assertFailsWith<OuraException> { auth.accessToken() }.kind)
        assertFalse(auth.connected, "a refused refresh token (invalid_grant, invalid_client) does")
    }

    @Test
    fun `a rate limit on the token address reports how long Oura asks to wait`() {
        login()
        store.tokens = store.tokens!!.copy(expiresAt = now)
        http.tokenStatus = 429
        http.tokenHeaders = mapOf("retry-after" to "300")
        assertEquals(300L, assertFailsWith<OuraException> { auth.accessToken() }.retryAfterS)
    }

    private val waits = ArrayList<Long>()
    private fun retrying() = OuraSync(OuraClient(http, auth, RetryPolicy(listOf(1_500L, 5_000L)) { waits += it }, { now }), repo)

    @Test
    fun `a busy Oura is asked again, and a short Retry-After is waited out`() {
        login()
        var calls = 0
        http.apiStatus = { if (calls++ < 2) 503 else 200 }
        retrying().sync(7, today)
        assertEquals(listOf(1_500L, 5_000L), waits)

        waits.clear()
        calls = 0
        http.apiHeaders = mapOf("retry-after" to "3")
        http.apiStatus = { if (calls++ < 1) 429 else 200 }
        retrying().sync(7, today)
        assertEquals(listOf(3_000L), waits)
    }

    @Test
    fun `a long Retry-After is not waited for but handed to the caller`() {
        login()
        http.apiStatus = { 429 }
        http.apiHeaders = mapOf("retry-after" to "900")
        val error = assertFailsWith<OuraException> { retrying().sync(7, today) }
        assertEquals(OuraException.Kind.RATE_LIMITED, error.kind)
        assertEquals(900L, error.retryAfterS)
        assertEquals(1, http.calls.count { "usercollection" in it.url }, "asked once, not hammered")
        assertEquals(emptyList(), waits)
    }

    @Test
    fun `a dropped connection is retried before it is reported`() {
        login()
        var calls = 0
        val flaky = object : OuraHttp {
            override fun send(method: String, url: String, headers: Map<String, String>, form: String?, timeoutMs: Int): OuraResponse {
                if ("usercollection" in url && calls++ == 0) throw IOException("reset")
                return http.send(method, url, headers, form, timeoutMs)
            }
        }
        OuraSync(OuraClient(flaky, auth, RetryPolicy(listOf(10L)) { waits += it }, { now }), repo).sync(7, today)
        assertEquals(listOf(10L), waits)
    }
}
