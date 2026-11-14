package nl.tufein.openconnect.core.garmin

import nl.tufein.openconnect.core.Activity
import nl.tufein.openconnect.core.DailySummary
import nl.tufein.openconnect.core.InMemoryRepository
import nl.tufein.openconnect.core.SleepSummary
import nl.tufein.openconnect.core.Sport
import nl.tufein.openconnect.core.formats.Formats
import nl.tufein.openconnect.core.garmin.FakeGarminHttp.Companion.bytes
import nl.tufein.openconnect.core.garmin.FakeGarminHttp.Companion.jwt
import nl.tufein.openconnect.core.garmin.FakeGarminHttp.Companion.text
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GarminSyncTest {
    private val now = Instant.parse("2026-11-12T10:00:00Z")
    private val http = FakeGarminHttp()
    private val repo = InMemoryRepository()
    private val client = PersonalApiClient(http, apiKey = { "KEY-1234567890" }, clock = { now })
    private val d10 = LocalDate.parse("2026-11-10")
    private val d11 = LocalDate.parse("2026-11-11")
    private val d12 = LocalDate.parse("2026-11-12")

    private fun service(maxNew: Int = 30) = GarminSyncService(client, repo, ZoneOffset.UTC, { now }, maxNew)

    private fun resource(path: String): ByteArray =
        checkNotNull(GarminSyncTest::class.java.getResourceAsStream(path)) { "missing $path" }.readBytes()

    private fun answerEverything() {
        http.on("POST", "/personal-api-key/token") { text(200, jwt(now.plusSeconds(86_400))) }
        http.json("/usersummary-service/v2/dailysummaries", resource("/garmin/dailysummaries.json").decodeToString())
        http.json("/usersummary-service/stats/steps/daily", resource("/garmin/steps.json").decodeToString())
        http.json("/usersummary-service/stats/stress/daily", resource("/garmin/stress.json").decodeToString())
        http.json("/hrv-service/hrv/daily", resource("/garmin/hrv.json").decodeToString())
        http.json("/sleep-service/sleep/dailySleepSummariesByDate", resource("/garmin/sleep.json").decodeToString())
        http.json("/wellness-service/wellness/bodyBattery/reports/daily", resource("/garmin/bodybattery.json").decodeToString())
        http.json("/activitylist-service/activities/search/activities", "[]")
    }

    private fun results(report: GarminSyncReport) = report.endpoints.associateBy { it.id }

    // ------------------------------------------------------------------------------------------ days

    @Test
    fun `a sync stores the days and nights Garmin has`() {
        answerEverything()
        val report = service().sync(days = 7)

        assertNull(report.stopped)
        assertFalse(report.hasProblems)
        val day = repo.listDaily(d10, d10).single()
        assertEquals(8421, day.steps)
        assertEquals(6310.5, day.distanceM) // the larger of the two answers (6310 and 6310.5)
        assertEquals(10_000, day.stepsGoal)
        assertEquals(52, day.restingHr)
        assertEquals(31, day.avgStress)
        assertEquals(92, day.bodyBatteryHigh)
        assertEquals(18, day.bodyBatteryLow)
        assertEquals(96, day.spo2Avg)
        assertEquals(410, day.activeKcal)

        val eleven = repo.listDaily(d11, d11).single()
        assertEquals(10_234, eleven.steps)
        assertEquals(49, eleven.hrvMs) // from the HRV answer
        assertEquals(88, eleven.bodyBatteryHigh)
        assertEquals(40, eleven.bodyBatteryLow)
        assertEquals(51, eleven.restingHr)

        val night = repo.listSleep(d11, d11).single()
        assertEquals(27_000, night.durationS)
        assertEquals(84, night.score)

        assertEquals(2, report.daysUpdated)
        assertEquals(1, report.nightsUpdated)
        assertNull(repo.listDaily(d12, d12).firstOrNull(), "today has no steps yet, so no row")
    }

    @Test
    fun `every data type is asked for once for a short period, with the dates in the paths`() {
        answerEverything()
        service().sync(days = 7)
        val paths = http.calls.filter { it.method == "GET" }.map { it.path + (if (it.query.isEmpty()) "" else "?" + it.query) }
        assertTrue("/usersummary-service/v2/dailysummaries?startDate=2026-11-06&endDate=2026-11-12" in paths, paths.toString())
        assertTrue("/usersummary-service/stats/steps/daily/2026-11-06/2026-11-12" in paths, paths.toString())
        assertTrue("/usersummary-service/stats/stress/daily/2026-11-06/2026-11-12" in paths, paths.toString())
        assertTrue("/hrv-service/hrv/daily/2026-11-06/2026-11-12" in paths, paths.toString())
        assertTrue("/sleep-service/sleep/dailySleepSummariesByDate?startDate=2026-11-06&endDate=2026-11-12" in paths, paths.toString())
        assertTrue("/wellness-service/wellness/bodyBattery/reports/daily?startDate=2026-11-06&endDate=2026-11-12" in paths, paths.toString())
        assertTrue(paths.any { it.startsWith("/activitylist-service/activities/search/activities?start=0&limit=50&startDate=2026-11-06&endDate=2026-11-12") })
        assertTrue(http.calls.filter { it.method == "GET" }.all { it.headers["Authorization"]!!.startsWith("Bearer ") })
    }

    @Test
    fun `long periods are split to stay within Garmin's limits per call`() {
        answerEverything()
        service().sync(days = 90) // 2026-08-15 .. 2026-11-12
        fun ranges(path: String) = http.callsTo(path).filter { it.method == "GET" }.map { c ->
            Regex("(\\d{4}-\\d{2}-\\d{2})[/&a-zA-Z=]*(\\d{4}-\\d{2}-\\d{2})").find(c.url.substringAfter(path))!!.destructured.let { (a, b) -> a to b }
        }
        assertEquals(
            listOf("2026-08-15" to "2026-09-11", "2026-09-12" to "2026-10-09", "2026-10-10" to "2026-11-06", "2026-11-07" to "2026-11-12"),
            ranges("/usersummary-service/stats/steps/daily"),
        ) // 28 days per call
        assertEquals(
            listOf("2026-08-15" to "2026-09-14", "2026-09-15" to "2026-10-15", "2026-10-16" to "2026-11-12"),
            ranges("/sleep-service/sleep/dailySleepSummariesByDate"),
        ) // 31 days per call
        assertEquals(1, http.callsTo("/usersummary-service/v2/dailysummaries").size) // up to a year per call
    }

    @Test
    fun `the period is limited to a year and at least a day`() {
        answerEverything()
        assertEquals(365, service().sync(days = 5000).days)
        assertEquals(1, service().sync(days = 0).days)
    }

    @Test
    fun `what a watch cable already stored is kept where it is larger or newer`() {
        answerEverything()
        repo.upsertDaily(DailySummary(d10, steps = 12_000, distanceM = 9000.0, restingHr = 55, hrvMs = 40, spo2Avg = 94))
        repo.upsertSleep(SleepSummary(d11, durationS = 30_000, deepS = 6000, lightS = 18_000, remS = 6000, score = 70))

        service().sync(days = 7)

        val day = repo.listDaily(d10, d10).single()
        assertEquals(12_000, day.steps)
        assertEquals(9000.0, day.distanceM)
        assertEquals(52, day.restingHr)
        assertEquals(96, day.spo2Avg)
        assertEquals(30_000, repo.listSleep(d11, d11).single().durationS, "a longer night from the watch is not replaced by a shorter one")
    }

    @Test
    fun `syncing twice gives the same result`() {
        answerEverything()
        service().sync(days = 7)
        val first = repo.listDaily(d10, d12)
        service().sync(days = 7)
        assertEquals(first, repo.listDaily(d10, d12))
        assertEquals(1, repo.listSleep(d10, d12).size)
    }

    @Test
    fun `a second sync that finds nothing new reports no updates and writes nothing`() {
        answerEverything()
        val first = service().sync(days = 7)
        assertTrue(first.daysUpdated > 0 && first.nightsUpdated > 0)

        val writes = CountingRepository(repo)
        val again = GarminSyncService(client, writes, ZoneOffset.UTC, { now }).sync(days = 7)

        assertEquals(0, again.daysUpdated, "the same numbers again are not an update")
        assertEquals(0, again.nightsUpdated)
        assertEquals(0, writes.writes, "so nothing is rewritten and the app is not told that data changed")
    }

    @Test
    fun `a day that really changed is stored and counted, the others are left alone`() {
        answerEverything()
        service().sync(days = 7)
        // the watch syncs again: the 10th has more steps now, everything else is as before
        http.json("/usersummary-service/stats/steps/daily", """[{"calendarDate":"2026-11-10","totalSteps":9000,"totalDistance":6900,"stepGoal":10000}]""")

        val writes = CountingRepository(repo)
        val again = GarminSyncService(client, writes, ZoneOffset.UTC, { now }).sync(days = 7)

        assertEquals(1, again.daysUpdated)
        assertEquals(0, again.nightsUpdated)
        assertEquals(9000, repo.listDaily(d10, d10).single().steps)
        assertEquals(1, writes.writes)
    }

    @Test
    fun `a night that changed is stored again, a longer one from elsewhere is kept`() {
        answerEverything()
        service().sync(days = 7)
        http.json("/sleep-service/sleep/dailySleepSummariesByDate", """[{"calendarDate":"2026-11-11","sleepTimeSeconds":27600,"deepSleepSeconds":5400,"lightSleepSeconds":15000,"remSleepSeconds":7200,"awakeSleepSeconds":900,"sleepScores":{"overall":{"value":86}}}]""")
        assertEquals(1, service().sync(days = 7).nightsUpdated)
        assertEquals(27_600, repo.listSleep(d11, d11).single().durationS)
    }

    @Test
    fun `answers about days outside the period are ignored`() {
        answerEverything()
        service().sync(days = 2) // 2026-11-11 .. 2026-11-12
        assertNull(repo.listDaily(d10, d10).firstOrNull())
        assertNotNull(repo.listDaily(d11, d11).firstOrNull())
    }

    @Test
    fun `the diagnostics text names layouts and counts and never values or the key`() {
        answerEverything()
        http.json("/usersummary-service/stats/stress/daily", """{"surprise": {"list": [{"level": 5, "secret": "value-should-not-show"}]}}""")
        http.status("GET", "/hrv-service/hrv/daily", 403)
        val text = service().sync(days = 7).diagnostics()

        assertTrue("stress: UNRECOGNISED" in text && "level: number" in text, text)
        assertTrue("hrv: FAILED" in text && "403" in text, text)
        assertTrue("dailySummaries: OK (2)" in text, text) // 10 and 11 November; today has only zero counters
        assertFalse("value-should-not-show" in text || "KEY-1234567890" in text, text)
    }

    // ---------------------------------------------------------------------------------------- problems

    @Test
    fun `one data type that fails does not stop the others and the report says which`() {
        answerEverything()
        http.status("GET", "/usersummary-service/stats/steps/daily", 404)
        http.status("GET", "/hrv-service/hrv/daily", 403)
        val report = service().sync(days = 7)

        val by = results(report)
        assertEquals(EndpointStatus.FAILED, by.getValue("steps").status)
        assertTrue("404" in by.getValue("steps").detail.orEmpty())
        assertEquals(EndpointStatus.FAILED, by.getValue("hrv").status)
        assertEquals(EndpointStatus.OK, by.getValue("dailySummaries").status)
        assertEquals(EndpointStatus.OK, by.getValue("sleep").status)
        assertTrue(report.hasProblems)
        assertNull(report.stopped)
        assertEquals(8421, repo.listDaily(d10, d10).single().steps) // still known from the daily summaries
    }

    @Test
    fun `an answer the mapping cannot read is reported with its layout and no values`() {
        answerEverything()
        http.json("/usersummary-service/stats/stress/daily", """{"surprise": {"list": [{"level": 5, "secret": "value-should-not-show"}]}}""")
        val report = service().sync(days = 7)

        val stress = results(report).getValue("stress")
        assertEquals(EndpointStatus.UNRECOGNISED, stress.status)
        val shape = stress.detail.orEmpty()
        assertTrue("surprise" in shape && "level: number" in shape, shape)
        assertFalse("value-should-not-show" in shape, shape)
        assertTrue(report.hasProblems)
    }

    @Test
    fun `empty answers are not a problem`() {
        answerEverything()
        http.json("/hrv-service/hrv/daily", "[]")
        http.json("/usersummary-service/stats/stress/daily", "")
        val by = results(service().sync(days = 7))
        assertEquals(EndpointStatus.EMPTY, by.getValue("hrv").status)
        assertEquals(EndpointStatus.EMPTY, by.getValue("stress").status)
    }

    @Test
    fun `a rejected key ends the sync at once and stores nothing`() {
        answerEverything()
        http.status("POST", "/personal-api-key/token", 401)
        val report = service().sync(days = 7)

        assertEquals(GarminApiException.Kind.KEY_REJECTED, report.stopped?.kind)
        assertTrue(repo.listDaily(d10, d12).isEmpty())
        assertEquals(1, http.calls.size, "no point asking for more")
        assertTrue(report.hasProblems)
    }

    @Test
    fun `a rate limit in the middle keeps what was already collected`() {
        answerEverything()
        http.status("GET", "/usersummary-service/stats/stress/daily", 429)
        val report = service().sync(days = 7)

        assertEquals(GarminApiException.Kind.RATE_LIMITED, report.stopped?.kind)
        assertEquals(8421, repo.listDaily(d10, d10).single().steps)
        assertTrue(http.callsTo("/hrv-service").isEmpty() && http.callsTo("/activitylist-service").isEmpty())
        assertEquals(EndpointStatus.FAILED, results(report).getValue("stress").status)
    }

    @Test
    fun `a rate limit tells the report how long Garmin wants us to wait`() {
        answerEverything()
        http.on("GET", "/usersummary-service/stats/stress/daily") { text(429, "", mapOf("retry-after" to "600")) }
        val report = service().sync(days = 7)
        assertEquals(GarminApiException.Kind.RATE_LIMITED, report.stopped?.kind)
        assertEquals(600L, report.stopped?.retryAfterS)
    }

    @Test
    fun `no connection ends the sync`() {
        val report = GarminSyncService(PersonalApiClient(networkDown(), { "KEY-1234567890" }), repo, ZoneOffset.UTC, { now }).sync(7)
        assertEquals(GarminApiException.Kind.NETWORK, report.stopped?.kind)
    }

    @Test
    fun `cancelling stops between requests`() {
        answerEverything()
        var asked = 0
        val report = service().sync(days = 7, isCancelled = { asked++ >= 2 })
        assertTrue(report.cancelled)
        assertTrue(http.calls.count { it.method == "GET" } <= 2)
    }

    @Test
    fun `progress runs up to the total`() {
        answerEverything()
        val seen = ArrayList<GarminProgress>()
        service().sync(days = 7, onProgress = { seen.add(it) })
        assertTrue(seen.isNotEmpty())
        assertTrue(seen.all { it.done <= it.total })
        assertEquals(seen.last().total, seen.last().done)
        assertEquals("dailySummaries", seen.first().label)
    }

    // -------------------------------------------------------------------------------------- activities

    private val fitBytes = resource("/fixtures/python-run.fit")
    private val fitActivity: Activity = Formats.import(fitBytes, "python-run.fit")

    private fun zip(name: String, content: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use {
            it.putNextEntry(ZipEntry(name))
            it.write(content)
            it.closeEntry()
        }
        return out.toByteArray()
    }

    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC)

    private fun summaryJson(id: Long, start: Instant, name: String, type: String = "running") =
        """{"activityId":$id,"activityName":"$name","activityType":{"typeKey":"$type"},"startTimeGMT":"${stamp.format(start)}","startTimeLocal":"${stamp.format(start.plusSeconds(3600))}","distance":10234.5,"duration":3120.4,"elevationGain":84.0,"averageHR":152.0,"maxHR":178.0,"calories":690.0}"""

    @Test
    fun `an activity is downloaded as the original file and keeps Garmin's name`() {
        answerEverything()
        http.json("/activitylist-service/activities/search/activities", "[" + summaryJson(20123456789, fitActivity.startTime, "Morning Run") + "]")
        http.on("GET", "/download-service/files/activity/20123456789") { bytes(200, zip("20123456789_ACTIVITY.fit", fitBytes)) }

        val report = service().sync(days = 7)

        assertEquals(1, report.activitiesAdded)
        assertEquals(0, report.activitiesWithoutFile)
        val stored = repo.getActivity(fitActivity.id)
        assertNotNull(stored, "stored under the same id the file gets when it is imported any other way")
        assertEquals("garmin", stored.source)
        assertEquals("20123456789", stored.externalId)
        assertEquals("Morning Run", stored.name)
        assertTrue(stored.track.isNotEmpty())
    }

    @Test
    fun `an activity whose file cannot be used is added from its summary`() {
        answerEverything()
        val start = Instant.parse("2026-11-11T06:12:33Z")
        http.json(
            "/activitylist-service/activities/search/activities",
            "[" + summaryJson(111, start, "Evening Walk", "walking") + "," + summaryJson(222, start.plusSeconds(86_400 / 2), "Broken file", "cycling") + "]",
        )
        http.status("GET", "/download-service/files/activity/111", 404)
        http.on("GET", "/download-service/files/activity/222") { bytes(200, zip("x.fit", "not a fit file".toByteArray())) }

        val report = service().sync(days = 7)

        assertEquals(2, report.activitiesAdded)
        assertEquals(2, report.activitiesWithoutFile)
        val walk = repo.getActivity("garmin-111")
        assertNotNull(walk)
        assertEquals(Sport.WALKING, walk.sport)
        assertEquals(start, walk.startTime)
        assertEquals(10_234.5, walk.distanceM)
        assertEquals(3120.4, walk.durationS)
        assertEquals(152, walk.avgHr)
        assertEquals(178, walk.maxHr)
        assertEquals(690, walk.calories)
        assertEquals(84.0, walk.elevationGainM)
        assertEquals(Sport.CYCLING, repo.getActivity("garmin-222")?.sport)
    }

    @Test
    fun `a summary uses the local start time when there is no gmt time`() {
        answerEverything()
        http.json(
            "/activitylist-service/activities/search/activities",
            """[{"activityId":5,"activityName":"Local only","activityType":{"typeKey":"running"},"startTimeLocal":"2026-11-11 07:00:00","distance":1000.0,"duration":300.0}]""",
        )
        http.status("GET", "/download-service/files/activity/5", 404)
        GarminSyncService(client, repo, java.time.ZoneId.of("Europe/Amsterdam"), { now }).sync(days = 7)
        assertEquals(Instant.parse("2026-11-11T06:00:00Z"), repo.getActivity("garmin-5")?.startTime)
    }

    @Test
    fun `an activity the app already has is not downloaded again`() {
        answerEverything()
        repo.upsertActivity(fitActivity.copy(source = "watch"))
        http.json(
            "/activitylist-service/activities/search/activities",
            "[" + summaryJson(20123456789, fitActivity.startTime.plusSeconds(30), "Morning Run") + "]", // clocks differ by seconds
        )
        val report = service().sync(days = 7)
        assertEquals(0, report.activitiesAdded)
        assertEquals(1, report.activitiesKnown)
        assertTrue(http.callsTo("/download-service").isEmpty())
        assertEquals("watch", repo.getActivity(fitActivity.id)?.source)
    }

    @Test
    fun `only a limited number of new activities is fetched per sync, newest first`() {
        answerEverything()
        val base = Instant.parse("2026-11-11T06:00:00Z")
        val list = (1..5).joinToString(",", "[", "]") { summaryJson(it.toLong(), base.plusSeconds(it * 7200L), "Run $it") }
        http.json("/activitylist-service/activities/search/activities", list)
        http.status("GET", "/download-service/files/activity", 404)

        val report = service(maxNew = 2).sync(days = 7)

        assertEquals(2, report.activitiesAdded)
        assertEquals(3, report.activitiesPending)
        assertNotNull(repo.getActivity("garmin-5"))
        assertNotNull(repo.getActivity("garmin-4"))
        assertNull(repo.getActivity("garmin-1"))

        val again = service(maxNew = 2).sync(days = 7)
        assertEquals(2, again.activitiesKnown) // the two already there
        assertEquals(2, again.activitiesAdded)
        assertEquals(1, again.activitiesPending)
    }

    @Test
    fun `activity lists are paged`() {
        answerEverything()
        val base = Instant.parse("2026-11-01T06:00:00Z")
        fun page(from: Int, count: Int) = (from until from + count).joinToString(",", "[", "]") { summaryJson(it.toLong() + 1000, base.plusSeconds(it * 7200L), "Run $it") }
        http.on("GET", "/activitylist-service/activities/search/activities") { call ->
            when {
                "start=0&" in call.url -> text(200, page(0, 50))
                "start=50&" in call.url -> text(200, page(50, 3))
                else -> text(200, "[]")
            }
        }
        http.status("GET", "/download-service/files/activity", 404)
        val report = service(maxNew = 0).sync(days = 30)
        assertEquals(53, report.activitiesPending)
        assertEquals(2, http.callsTo("/activitylist-service").size)
    }

    @Test
    fun `an unreadable activity list is reported with its layout`() {
        answerEverything()
        http.json("/activitylist-service/activities/search/activities", """[{"id": 5, "note": "x"}]""")
        val report = service().sync(days = 7)
        assertEquals(EndpointStatus.UNRECOGNISED, results(report).getValue("activities").status)
        assertEquals(0, report.activitiesAdded)
    }

    @Test
    fun `a rejected key during the downloads stops the sync and counts the rest as pending`() {
        answerEverything()
        http.json(
            "/activitylist-service/activities/search/activities",
            "[" + summaryJson(1, Instant.parse("2026-11-11T08:00:00Z"), "A") + "," + summaryJson(2, Instant.parse("2026-11-11T06:00:00Z"), "B") + "]",
        )
        http.status("GET", "/download-service/files/activity", 429)
        val report = service().sync(days = 7)
        assertEquals(GarminApiException.Kind.RATE_LIMITED, report.stopped?.kind)
        assertEquals(0, report.activitiesAdded)
        assertEquals(2, report.activitiesPending)
        assertEquals(8421, repo.listDaily(d10, d10).single().steps, "the days were stored before the activities")
    }

    @Test
    fun `a plain fit file without a zip is accepted`() {
        answerEverything()
        http.json("/activitylist-service/activities/search/activities", "[" + summaryJson(7, fitActivity.startTime, "Plain") + "]")
        http.on("GET", "/download-service/files/activity/7") { bytes(200, fitBytes) }
        assertEquals(1, service().sync(days = 7).activitiesAdded)
        assertEquals(0, service().sync(days = 7).activitiesAdded) // known the second time
        assertEquals("Plain", repo.getActivity(fitActivity.id)?.name)
    }
}

/** A repository that counts what is written to it, to show that a sync with nothing new writes nothing. */
private class CountingRepository(private val inner: nl.tufein.openconnect.core.Repository) : nl.tufein.openconnect.core.Repository by inner {
    var writes = 0

    override fun upsertDaily(daily: DailySummary) {
        writes++
        inner.upsertDaily(daily)
    }

    override fun upsertSleep(sleep: SleepSummary) {
        writes++
        inner.upsertSleep(sleep)
    }

    override fun upsertActivity(activity: Activity) {
        writes++
        inner.upsertActivity(activity)
    }
}
