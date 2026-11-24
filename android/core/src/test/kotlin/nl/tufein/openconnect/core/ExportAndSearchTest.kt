package nl.tufein.openconnect.core

import nl.tufein.openconnect.core.reports.ChatTurn
import nl.tufein.openconnect.core.reports.CoachRequest
import nl.tufein.openconnect.core.reports.CoachService
import nl.tufein.openconnect.core.reports.ReportError
import nl.tufein.openconnect.core.reports.ReportProvider
import nl.tufein.openconnect.core.reports.ReportRequest
import nl.tufein.openconnect.core.reports.ReportResult
import nl.tufein.openconnect.core.reports.coachFacts
import nl.tufein.openconnect.core.reports.toJson
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExportAndSearchTest {
    // ------------------------------------------------------------------------------------ CSV

    @Test
    fun dailyCsvHasOneRowPerDayWithSleepAlongside() {
        val d1 = TestData.daily(LocalDate.parse("2026-05-01"), steps = 9000).copy(hrvMs = 48, spo2Avg = 96, spo2Min = 91, respirationAvg = 14.5)
        val d2 = DailySummary(date = LocalDate.parse("2026-05-02"), steps = 100)
        val csv = Csv.dailies(listOf(d2, d1), listOf(TestData.sleep(LocalDate.parse("2026-05-01"), hours = 7.25, score = 82), TestData.sleep(LocalDate.parse("2026-04-30"), hours = 6.0, score = null)))
        val lines = csv.trimEnd().split("\r\n")
        assertEquals(
            "date,steps,steps_goal,distance_m,active_kcal,floors,moderate_min,vigorous_min,resting_hr,min_hr,max_hr," +
                "avg_stress,max_stress,body_battery_high,body_battery_low,hrv_ms,spo2_avg,spo2_min,respiration_avg," +
                "sleep_hours,sleep_deep_hours,sleep_light_hours,sleep_rem_hours,sleep_awake_hours,sleep_score",
            lines[0],
        )
        assertEquals(3, lines.size - 1, "three dates: a night with no day, and two days")
        assertEquals("2026-04-30", lines[1].substringBefore(','), "oldest first, also for a night without a day")
        assertTrue(lines[1].startsWith("2026-04-30" + ",".repeat(19) + "6.00,"), lines[1])
        val day1 = lines[2].split(',')
        assertEquals("2026-05-01", day1[0])
        assertEquals("9000", day1[1])
        assertEquals("10000", day1[2])
        assertEquals("6750.0", day1[3])
        assertEquals("48", day1[15])
        assertEquals("14.5", day1[18])
        assertEquals("7.25", day1[19])
        assertEquals("82", day1.last())
        assertEquals(25, day1.size, "every row has as many columns as the header")
        assertEquals(25, lines[3].split(',').size)
    }

    @Test
    fun dailyCsvUsesDotsForDecimalsWhateverTheLanguage() {
        val previous = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale("nl", "NL"))
            val csv = Csv.dailies(
                listOf(DailySummary(date = LocalDate.parse("2026-05-01"), distanceM = 1234.5, respirationAvg = 13.25)),
                listOf(TestData.sleep(LocalDate.parse("2026-05-01"), hours = 7.5)),
            )
            assertContains(csv, "1234.5")
            assertContains(csv, "13.25")
            assertContains(csv, "7.50")
            assertFalse("7,50" in csv)
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }

    @Test
    fun activitiesCsvQuotesWhatNeedsQuoting() {
        val a = Activity(
            id = "a:1", source = "garmin", name = "Run, \"fast\"\nlap", sport = Sport.RUNNING,
            startTime = Instant.parse("2026-05-02T07:00:00Z"), durationS = 1500.0, distanceM = 5000.0, elevationGainM = 12.0,
            avgHr = 150, maxHr = 170, calories = 420, deviceName = "Forerunner 965",
        )
        val b = Activity(id = "a:2", name = "Plain", sport = Sport.OTHER, startTime = Instant.parse("2026-05-01T07:00:00Z"))
        val csv = Csv.activities(listOf(a, b))
        val header = csv.substringBefore("\r\n")
        assertEquals("id,source,name,sport,start_utc,duration_s,distance_m,elevation_gain_m,avg_hr,max_hr,calories,device", header)
        assertContains(csv, "a:1,garmin,\"Run, \"\"fast\"\"\nlap\",running,2026-05-02T07:00:00Z,1500.0,5000.0,12.0,150,170,420,Forerunner 965\r\n")
        assertContains(csv, "a:2,import,Plain,other,2026-05-01T07:00:00Z,0.0,0.0,0.0,,,,\r\n")
    }

    @Test
    fun spreadsheetFormulasInNamesAreDefused() {
        // A name that starts like a formula would be run by a spreadsheet; a leading apostrophe stops that.
        val a = Activity(id = "a:1", name = "=HYPERLINK(\"http://x\")", sport = Sport.OTHER, startTime = Instant.parse("2026-05-01T07:00:00Z"))
        val csv = Csv.activities(listOf(a))
        assertContains(csv, "\"'=HYPERLINK(\"\"http://x\"\")\"")
        val plus = Csv.activities(listOf(a.copy(name = "+1 run")))
        assertContains(plus, ",'+1 run,")
    }

    // ----------------------------------------------------------------------------- searching

    private fun act(id: String, name: String, sport: Sport, start: String, km: Double, minutes: Double) = Activity(
        id = id, name = name, sport = sport, startTime = Instant.parse(start), distanceM = km * 1000, durationS = minutes * 60,
    )

    private val all = listOf(
        act("1", "Morning Run", Sport.RUNNING, "2026-05-02T07:00:00Z", 5.0, 30.0),
        act("2", "Evening ride", Sport.CYCLING, "2026-05-03T18:00:00Z", 40.0, 90.0),
        act("3", "Park run Zandvoort", Sport.RUNNING, "2026-04-20T08:00:00Z", 10.0, 55.0),
        act("4", "Yoga flow", Sport.YOGA, "2026-04-28T19:00:00Z", 0.0, 45.0),
    )

    @Test
    fun searchMatchesNameSportAndDateWordByWord() {
        assertEquals(listOf("1", "3"), all.search("run", ActivitySort.NEWEST).map { it.id }.sorted(), "name and sport both say 'run'")
        assertEquals(listOf("2"), all.search("EVENING", ActivitySort.NEWEST).map { it.id }, "case does not matter")
        assertEquals(listOf("3"), all.search("run 2026-04", ActivitySort.NEWEST).map { it.id }, "every word has to match something")
        assertEquals(listOf("2", "1"), all.search("2026-05", ActivitySort.NEWEST).map { it.id })
        assertEquals(listOf("4"), all.search("yoga", ActivitySort.NEWEST).map { it.id })
        assertEquals(emptyList(), all.search("swim", ActivitySort.NEWEST))
        assertEquals(all.size, all.search("   ", ActivitySort.NEWEST).size, "blank search shows everything")
    }

    @Test
    fun sortsByDateDistanceAndTime() {
        assertEquals(listOf("2", "1", "4", "3"), all.search("", ActivitySort.NEWEST).map { it.id })
        assertEquals(listOf("3", "4", "1", "2"), all.search("", ActivitySort.OLDEST).map { it.id })
        assertEquals(listOf("2", "3", "1", "4"), all.search("", ActivitySort.DISTANCE).map { it.id })
        assertEquals(listOf("2", "3", "4", "1"), all.search("", ActivitySort.DURATION).map { it.id })
    }

    // ----------------------------------------------------------------------------- coach facts

    private data class CoachFixture(val plan: TrainingPlan, val progress: PlanProgress, val proposal: PlanProposal)

    private fun coachFixture(): CoachFixture {
        val plan = TrainingPlans.build(PlanConfig(PlanGoal.FIVE_K, 3, 12, "2026-11-02", 15.0))
        val weeks = plan.weeks.mapIndexed { index, week ->
            WeekProgress(
                week = week,
                doneKm = if (index == 1) 3.456 else 0.0,
                doneRuns = if (index == 1) 1 else 0,
                state = if (index == 1) WeekState.BEHIND else WeekState.UPCOMING,
            )
        }
        val progress = PlanProgress(weeks, current = null, next = null, finished = false, notStarted = false)
        val proposal = PlanProposal(
            proposalId = "v1:2:5:EASY,INTERVALS,LONG",
            source = weeks[1],
            target = plan.weeks[4],
            sessionTypes = weeks[1].week.sessions.map { it.type },
        )
        return CoachFixture(plan, progress, proposal)
    }

    @Test
    fun coachFactsAreBoundedToPlanWeeksAndOneProposal() {
        val f = coachFixture()
        val facts = coachFacts(f.progress, f.proposal)
        assertEquals(12, facts.weeks.size)
        assertEquals(2, facts.weeks[1].week)
        assertEquals(f.plan.weeks[1].volumeKm, facts.weeks[1].plannedKm)
        assertEquals(3.46, facts.weeks[1].actualKm)
        assertEquals("behind", facts.weeks[1].state)
        val proposalFacts = checkNotNull(facts.proposal)
        assertEquals(2, proposalFacts.sourceWeek)
        assertEquals(5, proposalFacts.targetWeek)
        assertEquals(listOf("easy", "intervals", "long"), proposalFacts.sessionTypes)
        assertTrue(facts.toJson().length < 5_000, "a prompt-sized digest, not the database")

        val oversized = f.progress.copy(weeks = List(20) { f.progress.weeks[it % 12] })
        assertEquals(12, coachFacts(oversized, null).weeks.size)
    }

    @Test
    fun coachFactsNeverSerializeRawOrHistoricalData() {
        val facts = coachFacts(coachFixture().progress, coachFixture().proposal)
        val json = facts.toJson()
        assertContains(json, "\"planned_km\"")
        assertContains(json, "\"actual_km\"")
        assertContains(json, "\"proposal\"")
        for (forbidden in listOf("activities", "days", "today", "readiness", "decisions", "accepted", "dismissed", "track", "journal", "own_workout", "name")) {
            assertFalse(forbidden in json, "$forbidden must not leave the phone in coach facts")
        }
    }

    @Test
    fun coachFactsOfAnEmptyAppAreStillValid() {
        val facts = coachFacts(null, null)
        assertTrue(facts.weeks.isEmpty())
        assertNull(facts.proposal)
        assertContains(facts.toJson(), "\"weeks\": []")
        assertContains(facts.toJson(), "\"proposal\": null")
    }

    // ----------------------------------------------------------------------------- coach service

    private class RecordingProvider(private val reply: String = "Answer") : ReportProvider {
        override val name = "fake"
        override val modelId = "m1"
        var request: CoachRequest? = null

        override fun generate(request: ReportRequest) = ReportResult("report", name, modelId)

        override fun answer(request: CoachRequest): ReportResult {
            this.request = request
            return ReportResult(reply, name, modelId)
        }
    }

    @Test
    fun theCoachServiceInjectsTheCurrentFactsAndPassesTheConversation() {
        val f = coachFixture()
        val facts = coachFacts(f.progress, f.proposal)
        val provider = RecordingProvider("The proposal copies the missed week's pattern.")
        val service = CoachService { facts }
        val history = listOf(ChatTurn("user", "hi"), ChatTurn("assistant", "hello"))

        val result = service.ask("  What does the proposal change?  ", history, provider, "nl")

        assertEquals("The proposal copies the missed week's pattern.", result.text)
        val sent = provider.request!!
        assertEquals("What does the proposal change?", sent.question, "the question is trimmed")
        assertEquals(history, sent.history)
        assertEquals("nl", sent.language)
        assertEquals(facts, sent.facts)
        assertEquals(12, sent.facts.weeks.size)
        assertEquals(5, sent.facts.proposal!!.targetWeek)
    }

    @Test
    fun anEmptyQuestionNeverReachesTheProvider() {
        val provider = RecordingProvider()
        val service = CoachService()
        assertFailsWith<ReportError> { service.ask("   ", emptyList(), provider, "en") }
        assertNull(provider.request)
    }
}
