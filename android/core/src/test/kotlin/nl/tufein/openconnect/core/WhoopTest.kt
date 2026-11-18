package nl.tufein.openconnect.core

import nl.tufein.openconnect.core.oura.OuraAuth
import nl.tufein.openconnect.core.oura.OuraResponse
import nl.tufein.openconnect.core.whoop.WhoopAuth
import nl.tufein.openconnect.core.whoop.WhoopClient
import nl.tufein.openconnect.core.whoop.WhoopDay
import nl.tufein.openconnect.core.whoop.WhoopDemo
import nl.tufein.openconnect.core.whoop.WhoopException
import nl.tufein.openconnect.core.whoop.WhoopHttp
import nl.tufein.openconnect.core.whoop.WhoopStore
import nl.tufein.openconnect.core.whoop.WhoopSync
import nl.tufein.openconnect.core.whoop.WhoopSyncReport
import nl.tufein.openconnect.core.whoop.WhoopTokens
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MemoryWhoopStore : WhoopStore {
    override val canStoreSecrets = true
    override var clientId: String? = "client-1"
    override var clientSecret: String? = "secret-FAKE"
    override var redirectUri: String = WhoopAuth.DEFAULT_REDIRECT
    override var tokens: WhoopTokens? = null
    override var pendingLogin: String? = null
}

/** Answers like WHOOP's developer API from memory and records what was asked. */
class FakeWhoopHttp : WhoopHttp {
    class Call(val method: String, val url: String, val headers: Map<String, String>, val form: String?)

    val calls = ArrayList<Call>()
    var tokenStatus = 200
    var tokenCount = 0
    var apiStatus: (Call) -> Int = { 200 }

    /** The first page of a collection, by path such as "cycle" or "activity/sleep". */
    val pages = HashMap<String, String>()

    /** What the page after "page2" holds. */
    val secondPages = HashMap<String, String>()
    var offline = false
    var tokenHeaders: Map<String, String> = emptyMap()
    var apiHeaders: Map<String, String> = emptyMap()

    override fun send(method: String, url: String, headers: Map<String, String>, form: String?, timeoutMs: Int): OuraResponse {
        val call = Call(method, url, headers, form)
        calls += call
        if (offline) throw IOException("no connection")
        if (url == WhoopAuth.TOKEN_URL) {
            if (tokenStatus != 200) return OuraResponse(tokenStatus, """{"error":"invalid_grant"}""", tokenHeaders)
            tokenCount++
            return OuraResponse(200, """{"token_type":"bearer","access_token":"access-$tokenCount","expires_in":3600,"refresh_token":"refresh-$tokenCount","scope":"offline"}""")
        }
        val status = apiStatus(call)
        if (status != 200) return OuraResponse(status, "{}", apiHeaders)
        val path = url.substringAfter("/developer/v2/").substringBefore('?')
        val empty = """{"records":[],"next_token":null}"""
        if ("nextToken=page2" in url) return OuraResponse(200, secondPages[path] ?: empty)
        return OuraResponse(200, pages[path] ?: empty)
    }
}

class WhoopTest {
    private val now = Instant.parse("2026-11-13T12:00:00Z")
    private val store = MemoryWhoopStore()
    private val http = FakeWhoopHttp()
    private val auth = WhoopAuth(http, store) { now }
    private val repo = InMemoryRepository()
    private val sync = WhoopSync(WhoopClient(http, auth), repo)
    private val today = LocalDate.parse("2026-11-13")

    private fun login() {
        val url = auth.begin()
        val state = OuraAuth.params(url).getValue("state")
        auth.finish("openconnect://whoop?code=the-code&state=$state")
    }

    // ------------------------------------------------------------------------------------------------ the login

    @Test
    fun `the login address carries the client, the redirect, the scopes and a state of at least eight characters`() {
        val p = OuraAuth.params(auth.begin())
        assertEquals("https://api.prod.whoop.com/oauth/oauth2/auth", WhoopAuth.AUTHORIZE_URL)
        assertEquals("code", p["response_type"])
        assertEquals("client-1", p["client_id"])
        assertEquals("openconnect://whoop", p["redirect_uri"])
        assertEquals("offline read:recovery read:cycles read:workout read:sleep", p["scope"])
        assertTrue(p.getValue("state").length >= 8, "WHOOP wants a state of at least eight characters")
        assertEquals(p["state"], store.pendingLogin)
        assertFalse("code_challenge" in p, "WHOOP does not document PKCE, so none is sent")
        assertFalse("secret-FAKE" in auth.begin(), "the secret never goes into the browser")
    }

    @Test
    fun `finishing the login trades the code for tokens and sends the secret only to the token address`() {
        login()
        assertEquals("access-1", store.tokens?.access)
        assertEquals("refresh-1", store.tokens?.refresh)
        assertNull(store.pendingLogin)
        val call = http.calls.single()
        assertEquals("https://api.prod.whoop.com/oauth/oauth2/token", call.url)
        val form = OuraAuth.params("x?" + call.form)
        assertEquals("authorization_code", form["grant_type"])
        assertEquals("the-code", form["code"])
        assertEquals("openconnect://whoop", form["redirect_uri"])
        assertEquals("client-1", form["client_id"])
        assertEquals("secret-FAKE", form["client_secret"])
        assertTrue(call.headers["User-Agent"]!!.startsWith("OpenConnect"))
    }

    @Test
    fun `an address of another login, an error or no login at all is refused`() {
        assertFailsWith<WhoopException> { auth.finish("openconnect://whoop?code=x&state=y") }
        auth.begin()
        assertEquals(WhoopException.Kind.BAD_REDIRECT, assertFailsWith<WhoopException> { auth.finish("openconnect://whoop?code=x&state=wrong") }.kind)
        assertEquals(WhoopException.Kind.BAD_REDIRECT, assertFailsWith<WhoopException> { auth.finish("openconnect://whoop?error=access_denied") }.kind)
        assertNull(store.tokens)
    }

    @Test
    fun `a token that is about to run out is refreshed with the offline scope and the new single-use refresh token is kept`() {
        login()
        assertEquals("access-1", auth.accessToken())
        store.tokens = store.tokens!!.copy(expiresAt = now.plusSeconds(30))
        assertEquals("access-2", auth.accessToken())
        assertEquals("refresh-2", store.tokens?.refresh)
        val form = OuraAuth.params("x?" + http.calls.last().form)
        assertEquals("refresh_token", form["grant_type"])
        assertEquals("refresh-1", form["refresh_token"])
        assertEquals("offline", form["scope"])
        assertEquals("secret-FAKE", form["client_secret"])
    }

    @Test
    fun `a refused refresh ends the login, and no connection or a busy WHOOP does not`() {
        login()
        store.tokens = store.tokens!!.copy(expiresAt = now)
        http.offline = true
        assertEquals(WhoopException.Kind.NETWORK, assertFailsWith<WhoopException> { auth.accessToken() }.kind)
        assertTrue(auth.connected, "no connection is not a reason to log out")
        http.offline = false
        for ((status, kind) in listOf(429 to WhoopException.Kind.RATE_LIMITED, 503 to WhoopException.Kind.SERVER, 500 to WhoopException.Kind.SERVER, 403 to WhoopException.Kind.SERVER)) {
            http.tokenStatus = status
            assertEquals(kind, assertFailsWith<WhoopException> { auth.accessToken() }.kind, "status $status")
            assertTrue(auth.connected, "status $status is no reason to log out")
        }
        http.tokenStatus = 400
        assertEquals(WhoopException.Kind.LOGIN_EXPIRED, assertFailsWith<WhoopException> { auth.accessToken() }.kind)
        assertFalse(auth.connected, "invalid_grant ends the login")
    }

    @Test
    fun `a login without a refresh token cannot be renewed and says so`() {
        login()
        store.tokens = WhoopTokens("only-access", null, now)
        assertEquals(WhoopException.Kind.LOGIN_EXPIRED, assertFailsWith<WhoopException> { auth.accessToken() }.kind)
        assertFalse(auth.connected)
    }

    // ------------------------------------------------------------------------------------------------ the data

    private fun whoopData() {
        http.pages["cycle"] = """{"records":[
            {"id":93845,"user_id":1,"created_at":"2026-11-12T22:40:00.000Z","updated_at":"2026-11-13T09:00:00.000Z","start":"2026-11-12T22:40:00.000Z","end":null,"timezone_offset":"+01:00","score_state":"SCORED","score":{"strain":12.3,"kilojoule":9500.0,"average_heart_rate":68,"max_heart_rate":172}}
        ],"next_token":"page2"}"""
        http.secondPages["cycle"] = """{"records":[
            {"id":93844,"user_id":1,"start":"2026-11-11T22:30:00.000Z","end":"2026-11-12T22:40:00.000Z","timezone_offset":"+01:00","score_state":"SCORED","score":{"strain":8.1,"kilojoule":8000.0,"average_heart_rate":61,"max_heart_rate":150}}
        ],"next_token":null}"""
        http.pages["activity/sleep"] = """{"records":[
            {"id":"ecfc6a15-4661-442f-a9a4-f160dd7afae8","cycle_id":93845,"v1_id":1,"user_id":1,"start":"2026-11-12T22:40:00.000Z","end":"2026-11-13T06:40:00.000Z","timezone_offset":"+01:00","nap":false,"score_state":"SCORED",
             "score":{"stage_summary":{"total_in_bed_time_milli":28800000,"total_awake_time_milli":1800000,"total_no_data_time_milli":0,"total_light_sleep_time_milli":14400000,"total_slow_wave_sleep_time_milli":5400000,"total_rem_sleep_time_milli":7200000,"sleep_cycle_count":4,"disturbance_count":9},
                      "sleep_needed":{"baseline_milli":27000000,"need_from_sleep_debt_milli":1800000,"need_from_recent_strain_milli":900000,"need_from_recent_nap_milli":-300000},
                      "respiratory_rate":15.1,"sleep_performance_percentage":92,"sleep_consistency_percentage":85,"sleep_efficiency_percentage":93.5}},
            {"id":"nap-1","cycle_id":93845,"start":"2026-11-13T11:00:00.000Z","end":"2026-11-13T11:30:00.000Z","timezone_offset":"+01:00","nap":true,"score_state":"SCORED",
             "score":{"stage_summary":{"total_in_bed_time_milli":1800000,"total_awake_time_milli":0,"total_light_sleep_time_milli":1800000,"total_slow_wave_sleep_time_milli":0,"total_rem_sleep_time_milli":0},"sleep_performance_percentage":10}},
            {"id":"night-0","cycle_id":93844,"start":"2026-11-11T22:30:00.000Z","end":"2026-11-12T05:30:00.000Z","timezone_offset":"+01:00","nap":false,"score_state":"PENDING_SCORE"}
        ],"next_token":null}"""
        http.pages["recovery"] = """{"records":[
            {"cycle_id":93845,"sleep_id":"ecfc6a15-4661-442f-a9a4-f160dd7afae8","user_id":1,"created_at":"2026-11-13T06:45:00.000Z","score_state":"SCORED","score":{"user_calibrating":false,"recovery_score":81,"resting_heart_rate":52,"hrv_rmssd_milli":64.2,"spo2_percentage":96.6,"skin_temp_celsius":33.7}},
            {"cycle_id":93844,"sleep_id":"night-0","user_id":1,"created_at":"2026-11-12T05:35:00.000Z","score_state":"PENDING_SCORE"}
        ],"next_token":null}"""
        http.pages["activity/workout"] = """{"records":[
            {"id":"w-1","v1_id":1043,"user_id":1,"start":"2026-11-12T06:00:00.000Z","end":"2026-11-12T06:45:00.000Z","timezone_offset":"+01:00","sport_name":"running","sport_id":0,"score_state":"SCORED",
             "score":{"strain":9.4,"average_heart_rate":151,"max_heart_rate":178,"kilojoule":2092.0,"percent_recorded":100,"distance_meter":7500.0,"altitude_gain_meter":42.1,"altitude_change_meter":3.0}},
            {"id":"w-2","start":"2026-11-12T17:00:00.000Z","end":"2026-11-12T17:30:00.000Z","timezone_offset":"+01:00","sport_name":"weightlifting","score_state":"UNSCORABLE"}
        ],"next_token":null}"""
    }

    @Test
    fun `a sync builds each day from the cycle, the night and the recovery that belong together and follows the next page`() {
        login()
        whoopData()
        val report = sync.sync(7, today)
        assertEquals(2, report.daysUpdated)
        assertEquals(1, report.nightsUpdated, "only the scored night has stages")
        assertEquals(2, report.activitiesAdded)

        val morning = repo.listWhoopDays(today, today).single()
        assertEquals("2026-11-13", morning.day, "a night counts for the morning it ends")
        assertEquals(81, morning.recovery)
        assertEquals(12.3, morning.strain)
        assertEquals(92, morning.sleepPerformance)
        assertEquals(85, morning.sleepConsistency)
        assertEquals(94, morning.sleepEfficiency)
        assertEquals(27_000, morning.sleepS, "asleep is light + slow-wave + REM, not the time in bed")
        assertEquals(5_400, morning.deepS)
        assertEquals(14_400, morning.lightS)
        assertEquals(7_200, morning.remS)
        assertEquals(1_800, morning.awakeS)
        assertEquals(29_400, morning.sleepNeedS)
        assertEquals(52, morning.restingHr)
        assertEquals(64.2, morning.hrv)
        assertEquals(15.1, morning.respiration)
        assertEquals(96.6, morning.spo2)
        assertEquals(33.7, morning.skinTempC)
        assertEquals(2271, morning.kcal, "kilojoules of the day as kilocalories")
        assertEquals(68, morning.avgHr)
        assertEquals(172, morning.maxHr)
        assertEquals(morning, WhoopDay.fromJson(morning.toJson()), "it survives storage")

        val yesterday = repo.listWhoopDays(LocalDate.parse("2026-11-12"), LocalDate.parse("2026-11-12")).single()
        assertEquals(8.1, yesterday.strain)
        assertNull(yesterday.recovery, "a recovery that is still being scored has no numbers")
        assertNull(yesterday.sleepPerformance)
        assertNull(yesterday.sleepS)
        assertEquals(2, yesterday.workouts.size)
        val run = yesterday.workouts.first()
        assertEquals("Running", run.name)
        assertEquals(Instant.parse("2026-11-12T06:00:00Z"), run.start)
        assertEquals(2_700, run.durationS)
        assertEquals(9.4, run.strain)
        assertEquals(151, run.avgHr)
        assertEquals(178, run.maxHr)
        assertEquals(500, run.calories)
        assertEquals(7_500.0, run.distanceM)
        assertEquals("Weightlifting", yesterday.workouts.last().name)
        assertNull(yesterday.workouts.last().calories)

        val get = http.calls.first { it.method == "GET" }
        assertEquals("Bearer access-1", get.headers["Authorization"])
        val query = OuraAuth.params(get.url)
        assertEquals("25", query["limit"], "25 is the most WHOOP gives per page")
        assertEquals("2026-11-06T00:00:00Z", query["start"], "a day before the first day, for the night that began the evening before")
        assertTrue(http.calls.any { "nextToken=page2" in it.url }, "the second page was asked for")
        assertTrue(http.calls.none { "/v1/" in it.url }, "only the v2 API is used")
        assertEquals(WhoopSyncReport(), sync.sync(7, today), "a second sync changes nothing")
    }

    @Test
    fun `a night is labelled in the time zone it was slept in and the longest night of a day wins`() {
        login()
        http.pages["activity/sleep"] = """{"records":[
            {"id":"a","cycle_id":1,"start":"2026-11-12T23:30:00.000Z","end":"2026-11-13T00:30:00.000Z","timezone_offset":"-05:00","nap":false,"score_state":"SCORED",
             "score":{"stage_summary":{"total_light_sleep_time_milli":3600000,"total_slow_wave_sleep_time_milli":0,"total_rem_sleep_time_milli":0,"total_awake_time_milli":0},"sleep_performance_percentage":20}},
            {"id":"b","cycle_id":2,"start":"2026-11-13T02:00:00.000Z","end":"2026-11-13T09:00:00.000Z","timezone_offset":"-05:00","nap":false,"score_state":"SCORED",
             "score":{"stage_summary":{"total_light_sleep_time_milli":25200000,"total_slow_wave_sleep_time_milli":0,"total_rem_sleep_time_milli":0,"total_awake_time_milli":0},"sleep_performance_percentage":90}}
        ],"next_token":null}"""
        sync.sync(7, today)
        // Both end on 12 November in UTC-5 (19:30 on the 12th and 04:00 on the 13th): the first one is the 12th, the second the 13th.
        assertEquals(20, repo.listWhoopDays(LocalDate.parse("2026-11-12"), LocalDate.parse("2026-11-12")).single().sleepPerformance)
        assertEquals(90, repo.listWhoopDays(today, today).single().sleepPerformance)
    }

    @Test
    fun `days older than the window are left out and a sleep without a cycle in the data still makes a day`() {
        login()
        http.pages["activity/sleep"] = """{"records":[
            {"id":"old","cycle_id":7,"start":"2026-10-01T22:00:00.000Z","end":"2026-10-02T06:00:00.000Z","timezone_offset":"+01:00","nap":false,"score_state":"SCORED",
             "score":{"stage_summary":{"total_light_sleep_time_milli":3600000,"total_slow_wave_sleep_time_milli":0,"total_rem_sleep_time_milli":0,"total_awake_time_milli":0},"sleep_performance_percentage":50}},
            {"id":"new","cycle_id":8,"start":"2026-11-12T22:00:00.000Z","end":"2026-11-13T06:00:00.000Z","timezone_offset":"+01:00","nap":false,"score_state":"SCORED",
             "score":{"stage_summary":{"total_light_sleep_time_milli":7200000,"total_slow_wave_sleep_time_milli":0,"total_rem_sleep_time_milli":0,"total_awake_time_milli":0},"sleep_performance_percentage":70}}
        ],"next_token":null}"""
        sync.sync(7, today)
        assertEquals(listOf("2026-11-13"), repo.listWhoopDays(LocalDate.parse("2026-01-01"), today).map { it.day })
    }

    @Test
    fun `the watch's data is never touched by WHOOP`() {
        login()
        whoopData()
        val watchDay = DailySummary(date = LocalDate.parse("2026-11-12"), steps = 9999, restingHr = 50)
        val watchNight = SleepSummary(date = today, durationS = 20000, score = 70)
        repo.upsertDaily(watchDay)
        repo.upsertSleep(watchNight)
        sync.sync(7, today)
        assertEquals(listOf(watchDay), repo.listDaily(LocalDate.parse("2026-11-01"), today))
        assertEquals(listOf(watchNight), repo.listSleep(LocalDate.parse("2026-11-01"), today))
        assertTrue(repo.listActivities().isEmpty(), "a WHOOP workout is not added to the activities")
        assertEquals(2, repo.listWhoopDays(LocalDate.parse("2026-11-01"), today).size)
        assertTrue(repo.listOuraDays(LocalDate.parse("2026-11-01"), today).isEmpty(), "nor does it end up with the ring")
        repo.clearAll()
        assertTrue(repo.listWhoopDays(LocalDate.parse("2026-11-01"), today).isEmpty(), "clearing everything clears WHOOP's days too")
    }

    @Test
    fun `a changed day is stored again and counted`() {
        login()
        whoopData()
        sync.sync(7, today)
        http.pages["cycle"] = http.pages.getValue("cycle").replace("12.3", "14.0")
        val report = sync.sync(7, today)
        assertEquals(1, report.daysUpdated)
        assertEquals(0, report.nightsUpdated)
        assertEquals(14.0, repo.listWhoopDays(today, today).single().strain)
    }

    // ------------------------------------------------------------------------------------------------ refusals and rate limits

    @Test
    fun `refusals from WHOOP are told apart`() {
        login()
        http.apiStatus = { 403 }
        assertEquals(WhoopException.Kind.FORBIDDEN, assertFailsWith<WhoopException> { sync.sync(7, today) }.kind)
        http.apiStatus = { 429 }
        assertEquals(WhoopException.Kind.RATE_LIMITED, assertFailsWith<WhoopException> { sync.sync(7, today) }.kind)
        var first = true
        http.apiStatus = { if (first) { first = false; 401 } else 200 }
        sync.sync(7, today)
        assertEquals("access-2", store.tokens?.access, "a 401 gets one fresh token and a second try")
        store.tokens = null
        assertEquals(WhoopException.Kind.NOT_CONNECTED, assertFailsWith<WhoopException> { sync.sync(7, today) }.kind)
    }

    @Test
    fun `a rate limit reports how long WHOOP asks to wait, from Retry-After or from X-RateLimit-Reset`() {
        login()
        http.apiStatus = { 429 }
        http.apiHeaders = mapOf("x-ratelimit-reset" to "45")
        assertEquals(45L, assertFailsWith<WhoopException> { sync.sync(7, today) }.retryAfterS)
        http.apiHeaders = mapOf("retry-after" to "120", "x-ratelimit-reset" to "45")
        assertEquals(120L, assertFailsWith<WhoopException> { sync.sync(7, today) }.retryAfterS)
        store.tokens = store.tokens!!.copy(expiresAt = now)
        http.tokenStatus = 429
        http.tokenHeaders = mapOf("retry-after" to "300")
        assertEquals(300L, assertFailsWith<WhoopException> { auth.accessToken() }.retryAfterS)
    }

    private val waits = ArrayList<Long>()
    private fun retrying() = WhoopSync(WhoopClient(http, auth, RetryPolicy(listOf(1_500L, 5_000L)) { waits += it }, { now }), repo)

    @Test
    fun `a busy WHOOP is asked again, and a short rate limit window is waited out`() {
        login()
        var calls = 0
        http.apiStatus = { if (calls++ < 2) 503 else 200 }
        retrying().sync(7, today)
        assertEquals(listOf(1_500L, 5_000L), waits)

        waits.clear()
        calls = 0
        http.apiHeaders = mapOf("x-ratelimit-reset" to "3")
        http.apiStatus = { if (calls++ < 1) 429 else 200 }
        retrying().sync(7, today)
        assertEquals(listOf(3_000L), waits)
    }

    @Test
    fun `a long rate limit window is not waited for but handed to the caller`() {
        login()
        http.apiStatus = { 429 }
        http.apiHeaders = mapOf("x-ratelimit-reset" to "900")
        val error = assertFailsWith<WhoopException> { retrying().sync(7, today) }
        assertEquals(WhoopException.Kind.RATE_LIMITED, error.kind)
        assertEquals(900L, error.retryAfterS)
        assertEquals(1, http.calls.count { "/developer/v2/" in it.url }, "asked once, not hammered")
        assertEquals(emptyList(), waits)
    }

    @Test
    fun `a dropped connection is retried before it is reported`() {
        login()
        var calls = 0
        val flaky = object : WhoopHttp {
            override fun send(method: String, url: String, headers: Map<String, String>, form: String?, timeoutMs: Int): OuraResponse {
                if ("/developer/v2/" in url && calls++ == 0) throw IOException("reset")
                return http.send(method, url, headers, form, timeoutMs)
            }
        }
        WhoopSync(WhoopClient(flaky, auth, RetryPolicy(listOf(10L)) { waits += it }, { now }), repo).sync(7, today)
        assertEquals(listOf(10L), waits)
    }

    // ------------------------------------------------------------------------------------------------ demo and insights

    @Test
    fun `the demo days are the same every time and fill every number`() {
        val a = WhoopDemo.days(today)
        assertEquals(a, WhoopDemo.days(today))
        assertEquals(30, a.size)
        assertEquals(today.toString(), a.last().day)
        assertTrue(a.all { it.recovery in 0..100 && it.strain!! in 0.0..21.0 && it.sleepS!! > 0 && it.hrv!! > 0 })
        assertTrue(a.any { it.workouts.isNotEmpty() })
        assertNotEquals(a.first(), a.last())
    }

    @Test
    fun `WHOOP is its own source of insights per day and never mixed with the watch or the ring`() {
        val days = WhoopDemo.days(today)
        val set = DayRecords.whoop(today.minusDays(29), today, days)
        assertEquals(InsightSource.WHOOP, set.source)
        val record = set.record(today)!!
        val last = days.last()
        assertEquals(last.recovery!!.toDouble(), record.values[ChartMetric.READINESS], "recovery is the readiness of a band")
        assertEquals(last.strain, record.values[ChartMetric.STRAIN])
        assertEquals(last.sleepPerformance!!.toDouble(), record.values[ChartMetric.SLEEP_SCORE])
        assertEquals(last.restingHr!!.toDouble(), record.values[ChartMetric.RESTING_HR])
        assertEquals(last.hrv, record.values[ChartMetric.HRV])
        assertEquals(last.respiration, record.values[ChartMetric.RESPIRATION])
        assertEquals(last.spo2, record.values[ChartMetric.SPO2])
        assertTrue(InsightSource.WHOOP.metrics.containsAll(record.values.keys), "every number has a place on the day card")
        assertNull(ChartMetric.STRAIN.higherIsBetter, "more strain is neither good nor bad")
        val insight = DayInsights.of(set, today)
        assertEquals(InsightSource.WHOOP, insight.source)
        assertTrue(insight.readings.isNotEmpty())
        assertNull(insight.readings.first { it.metric == ChartMetric.STRAIN }.verdict, "a day's strain is not judged")
    }

    @Test
    fun `workouts of WHOOP become rows among the activities only when no watch or file covers them`() {
        val day = WhoopDemo.days(today).first { it.workouts.isNotEmpty() }
        val workout = day.workouts.first()
        val rows = BandWorkouts.extra(day.workouts, BandSource.WHOOP, emptyList())
        assertEquals("whoop:" + workout.id, rows.single().id)
        assertEquals("whoop", rows.single().source)
        assertEquals("WHOOP", rows.single().deviceName)
        assertEquals(Sport.RUNNING, rows.single().sport)
        assertEquals(workout, BandWorkouts.find(rows.single().id, BandSource.WHOOP, day.workouts))
        assertNull(BandWorkouts.find(rows.single().id, BandSource.OURA, day.workouts), "an id of another device is not found")
        assertEquals(BandSource.WHOOP, BandSource.ofRow(rows.single().id))
        assertNull(BandSource.ofRow("some-recorded-activity-id"))
        val recorded = rows.single().copy(id = "mine", source = "garmin")
        assertTrue(BandWorkouts.extra(day.workouts, BandSource.WHOOP, listOf(recorded)).isEmpty(), "the same effort recorded by the watch is not listed twice")
    }

    @Test
    fun `WHOOP's days are in a backup and an older backup without them still restores`() {
        val days = WhoopDemo.days(today, 5)
        days.forEach(repo::upsertWhoopDay)
        val bytes = Backup.write(repo)
        val other = InMemoryRepository()
        Backup.restore(bytes, other)
        assertEquals(days, other.listWhoopDays(LocalDate.parse("2026-01-01"), today))

        val text = java.util.zip.GZIPInputStream(bytes.inputStream()).use { String(it.readBytes(), Charsets.UTF_8) }
        val root = kotlinx.serialization.json.Json.parseToJsonElement(text) as kotlinx.serialization.json.JsonObject
        val withoutWhoop = kotlinx.serialization.json.JsonObject(root.filterKeys { it != "whoop" }).toString()
        val packed = java.io.ByteArrayOutputStream().also { out -> java.util.zip.GZIPOutputStream(out).use { it.write(withoutWhoop.toByteArray(Charsets.UTF_8)) } }.toByteArray()
        val third = InMemoryRepository()
        Backup.restore(packed, third)
        assertTrue(third.listWhoopDays(LocalDate.parse("2026-01-01"), today).isEmpty())
    }
}
