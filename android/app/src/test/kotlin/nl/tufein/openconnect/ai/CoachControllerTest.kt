package nl.tufein.openconnect.ai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import nl.tufein.openconnect.core.PlanConfig
import nl.tufein.openconnect.core.PlanGoal
import nl.tufein.openconnect.core.PlanProposal
import nl.tufein.openconnect.core.TrainingPlans
import nl.tufein.openconnect.core.reports.AiVendor
import nl.tufein.openconnect.core.reports.coachFacts
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoachControllerTest {
    private val settings = FakeAiSettings().also { it.setKey(AiVendor.GEMINI, "AIzaSyA-0123456789abcdefghijklmnopqrstu") }
    private val transport = FakeAiTransport { FakeAiTransport.gemini("You are doing fine.") }
    private val ai = AiController(settings, transport, CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined)
    private val plan = TrainingPlans.build(PlanConfig(PlanGoal.FIVE_K, 3, 12, "2026-11-02", 15.0))
    private val progress = TrainingPlans.progress(plan, emptyList(), LocalDate.parse("2026-11-12"))
    private val proposal = PlanProposal(
        proposalId = "v1:1:5:EASY,TEMPO,LONG",
        source = progress.weeks[0],
        target = plan.weeks[4],
        sessionTypes = progress.weeks[0].week.sessions.map { it.type },
    )
    private val facts = coachFacts(progress, proposal)

    private fun coach() = CoachController(
        ai = ai,
        scope = CoroutineScope(Dispatchers.Unconfined),
        io = Dispatchers.Unconfined,
        facts = { facts },
    )

    @Test
    fun `without an AI key the coach cannot be asked anything`() {
        settings.setKey(AiVendor.GEMINI, null)
        val c = coach()
        assertFalse(c.state.value.canAsk)
        c.ask("How am I doing?")
        assertTrue(c.state.value.messages.isEmpty())
        assertTrue(transport.calls.isEmpty())
    }

    @Test
    fun `a question and its answer join the conversation`() {
        val c = coach()
        assertTrue(c.state.value.canAsk)
        c.ask("  How am I doing?  ")
        val ui = c.state.value
        assertFalse(ui.busy)
        assertNull(ui.error)
        assertEquals(listOf("user", "assistant"), ui.messages.map { it.role })
        assertEquals("How am I doing?", ui.messages[0].text)
        assertEquals("You are doing fine.", ui.messages[1].text)
        assertEquals("gemini", ui.messages[1].provider)
        assertEquals("gemini-fake", ui.messages[1].model)
        // The data that was sent is the digest, with the question in front of it.
        val body = Json.parseToJsonElement(transport.calls.single().body).jsonObject
        val last = body.getValue("contents").jsonArray.last().jsonObject.getValue("parts").jsonArray[0].jsonObject.getValue("text").jsonPrimitive.content
        assertTrue("How am I doing?" in last)
        assertTrue("\"planned_km\"" in last)
        assertTrue("\"actual_km\"" in last)
        assertTrue("\"proposal\"" in last)
        assertTrue("Copy the ordered easy, tempo, long session pattern" in last)
    }

    @Test
    fun `a follow-up question carries the conversation so far`() {
        val c = coach()
        c.ask("First?")
        c.ask("And then?")
        assertEquals(4, c.state.value.messages.size)
        val body = Json.parseToJsonElement(transport.calls.last().body).jsonObject
        val roles = body.getValue("contents").jsonArray.map { it.jsonObject.getValue("role").jsonPrimitive.content }
        assertEquals(listOf("user", "model", "user"), roles)
    }

    @Test
    fun `the reports language is the language of the answer`() {
        settings.language = "nl"
        coach().ask("Hoe gaat het?")
        val body = transport.calls.single().body
        assertTrue("Write the answer in Dutch" in body)
    }

    @Test
    fun `a failed question shows the reason and the next one can still work`() {
        transport.handler = { FakeAiTransport.status(429, "{}") }
        val c = coach()
        c.ask("Question one")
        var ui = c.state.value
        assertFalse(ui.busy)
        assertTrue("rate limit" in ui.error.orEmpty())
        assertEquals(listOf("user"), ui.messages.map { it.role }, "the question stays so it can be seen and asked again")

        transport.handler = { FakeAiTransport.gemini("Now it works.") }
        c.ask("Question two")
        ui = c.state.value
        assertNull(ui.error)
        assertEquals("Now it works.", ui.messages.last().text)
    }

    @Test
    fun `blank questions are ignored`() {
        val c = coach()
        c.ask("   ")
        assertTrue(c.state.value.messages.isEmpty())
        assertTrue(transport.calls.isEmpty())
    }

    @Test
    fun `asking while an answer is on its way is ignored`() {
        val c = coach()
        var nested = 0
        transport.handler = {
            nested++
            c.ask("pushy second question") // arrives while the first is still being answered
            FakeAiTransport.gemini("first answer")
        }
        c.ask("first question")
        assertEquals(1, nested)
        assertEquals(listOf("first question", "first answer"), c.state.value.messages.map { it.text })
    }

    @Test
    fun `clearing the conversation also drops an answer that was still coming`() {
        val c = coach()
        transport.handler = {
            c.clear()
            FakeAiTransport.gemini("too late")
        }
        c.ask("question")
        assertTrue(c.state.value.messages.isEmpty())
        assertFalse(c.state.value.busy)
        assertNull(c.state.value.error)
    }

    @Test
    fun `the coach follows the AI settings when a key is removed`() {
        val c = coach()
        assertTrue(c.state.value.canAsk)
        ai.forgetKey(AiVendor.GEMINI)
        c.refresh()
        assertFalse(c.state.value.canAsk)
    }

    @Test
    fun `the request contains only bounded plan facts and a read-only prompt`() {
        coach().ask("What does the proposal do?")
        val body = Json.parseToJsonElement(transport.calls.single().body).jsonObject
        val system = body.getValue("system_instruction").jsonObject.getValue("parts").jsonArray[0].jsonObject.getValue("text").jsonPrimitive.content
        val last = body.getValue("contents").jsonArray.last().jsonObject.getValue("parts").jsonArray[0].jsonObject.getValue("text").jsonPrimitive.content
        assertTrue("cannot apply, dismiss or edit" in system)
        assertTrue("Do not invent alternative schedule changes" in system)
        assertTrue("only the current proposal" in system)

        val data = last.substringAfter("```json\n").substringBefore("\n```")
        val factsJson = Json.parseToJsonElement(data).jsonObject
        assertEquals(setOf("weeks", "proposal"), factsJson.keys)
        assertTrue(factsJson.getValue("weeks").jsonArray.size <= 12)
        for (week in factsJson.getValue("weeks").jsonArray) {
            assertEquals(setOf("week", "planned_km", "actual_km", "state"), week.jsonObject.keys)
        }
        assertEquals(setOf("source_week", "target_week", "session_types", "text"), factsJson.getValue("proposal").jsonObject.keys)
        for (forbidden in listOf("52.123456", "activities", "journal", "own_workout", "decisions", "accepted", "dismissed")) {
            assertFalse(forbidden in transport.calls.single().body, "$forbidden must never enter the coach request")
        }
    }
}
