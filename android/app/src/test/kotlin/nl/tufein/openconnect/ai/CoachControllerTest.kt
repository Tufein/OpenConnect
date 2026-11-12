package nl.tufein.openconnect.ai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import nl.tufein.openconnect.core.InMemoryRepository
import nl.tufein.openconnect.core.StatsService
import nl.tufein.openconnect.core.TrackPoint
import nl.tufein.openconnect.core.reports.AiVendor
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoachControllerTest {
    private val settings = FakeAiSettings().also { it.setKey(AiVendor.GEMINI, "AIzaSyA-0123456789abcdefghijklmnopqrstu") }
    private val transport = FakeAiTransport { FakeAiTransport.gemini("You are doing fine.") }
    private val repo = InMemoryRepository().also {
        it.upsertDaily(nl.tufein.openconnect.core.DailySummary(date = LocalDate.parse("2026-11-12"), steps = 9000, restingHr = 52))
    }
    private val ai = AiController(settings, transport, CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined)

    private fun coach() = CoachController(
        repo = repo,
        stats = StatsService(repo),
        ai = ai,
        scope = CoroutineScope(Dispatchers.Unconfined),
        io = Dispatchers.Unconfined,
        clock = { Instant.parse("2026-11-12T10:00:00Z") },
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
        assertTrue("\"steps\": 9000" in last)
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
    fun `nothing about tracks or files is sent, only the digest`() {
        repo.upsertActivity(
            nl.tufein.openconnect.core.Activity(
                id = "a:1", name = "Evening run", startTime = Instant.parse("2026-11-11T17:00:00Z"), distanceM = 5000.0, durationS = 1500.0,
                track = listOf(TrackPoint(Instant.parse("2026-11-11T17:00:00Z"), lat = 52.123456, lon = 4.654321)),
            ),
        )
        coach().ask("How was my run?")
        val sent = transport.calls.single().body
        assertTrue("Evening run" in sent)
        assertFalse("52.123456" in sent, "GPS positions never leave the phone")
        assertNotNull(sent)
    }
}
