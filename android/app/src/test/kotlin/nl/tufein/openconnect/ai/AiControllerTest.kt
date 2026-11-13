package nl.tufein.openconnect.ai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import nl.tufein.openconnect.core.reports.AiVendor
import nl.tufein.openconnect.core.reports.AnthropicReportProvider
import nl.tufein.openconnect.core.reports.GeminiReportProvider
import nl.tufein.openconnect.core.reports.TemplateReportProvider
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AiControllerTest {
    private val settings = FakeAiSettings()
    private val transport = FakeAiTransport()

    /** Everything runs on the calling thread, so a test can look at the state right after an action. */
    private fun controller() = AiController(settings, transport, CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined)

    private val geminiKey = "AIzaSyA-0123456789abcdefghijklmnopqrstu"
    private val claudeKey = "sk-ant-api03-AbCdEfGhIjKlMnOpQrStUvWxYz_0123456789-AbCdEf"

    private fun AiController.vendor(v: AiVendor) = state.value.vendors.first { it.vendor == v }

    @Test
    fun `the state starts from the settings with defaults filled in`() {
        settings.choice = "gemini"
        settings.language = "nl"
        settings.setKey(AiVendor.CLAUDE, claudeKey)
        settings.setModel(AiVendor.CLAUDE, "claude-sonnet-5-5")
        val ui = controller().state.value
        assertEquals("gemini", ui.choice)
        assertEquals("nl", ui.language)
        assertTrue(ui.canStoreKeys)
        assertEquals(listOf(AiVendor.CLAUDE, AiVendor.GEMINI), ui.vendors.map { it.vendor })
        val claude = ui.vendors[0]
        assertTrue(claude.hasKey)
        assertEquals("claude-sonnet-5-5", claude.model)
        assertEquals(AiKeyCheck.UNKNOWN, claude.check)
        val gemini = ui.vendors[1]
        assertFalse(gemini.hasKey)
        assertEquals(AiVendor.GEMINI.defaultModel, gemini.model)
        assertEquals(ActiveAi.TEMPLATE, ui.active, "Gemini is chosen but has no key, so the offline reports are used")
    }

    @Test
    fun `an unknown choice in the settings counts as auto`() {
        settings.choice = "banana"
        assertEquals("auto", controller().state.value.choice)
    }

    @Test
    fun `a pasted Gemini key is cleaned up, saved and checked with Google`() {
        val c = controller()
        c.saveKey(AiVendor.GEMINI, "  \"$geminiKey\"\n")
        assertEquals(geminiKey, settings.key(AiVendor.GEMINI))
        assertEquals(AiKeyCheck.OK, c.vendor(AiVendor.GEMINI).check)
        assertTrue(c.vendor(AiVendor.GEMINI).hasKey)
        val call = transport.calls.single()
        assertTrue(call.url.startsWith("https://generativelanguage.googleapis.com/"))
        assertEquals(geminiKey, call.headers["x-goog-api-key"])
        assertFalse(geminiKey in call.url)
    }

    @Test
    fun `a pasted Claude key is checked with Anthropic`() {
        transport.handler = { FakeAiTransport.claude("OK") }
        val c = controller()
        c.saveKey(AiVendor.CLAUDE, claudeKey)
        assertEquals(AiKeyCheck.OK, c.vendor(AiVendor.CLAUDE).check)
        assertTrue(transport.calls.single().url.startsWith("https://api.anthropic.com/"))
        assertEquals(claudeKey, transport.calls.single().headers["x-api-key"])
    }

    @Test
    fun `a key wrapped over two lines by the clipboard is joined`() {
        controller().saveKey(AiVendor.GEMINI, geminiKey.take(15) + "\n" + geminiKey.drop(15))
        assertEquals(geminiKey, settings.key(AiVendor.GEMINI))
    }

    @Test
    fun `something that cannot be a key is not saved and nothing is sent`() {
        val c = controller()
        for (bad in listOf("", "   ", "short", "<html>this is not a key at all</html>", "key with ünïcode and more text")) {
            c.saveKey(AiVendor.GEMINI, bad)
            assertEquals(AiKeyProblem.MALFORMED, c.vendor(AiVendor.GEMINI).problem, bad)
        }
        assertNull(settings.key(AiVendor.GEMINI))
        assertTrue(transport.calls.isEmpty())
    }

    @Test
    fun `a phone without secure storage keeps no key`() {
        settings.canStoreKeys = false
        val c = controller()
        assertFalse(c.state.value.canStoreKeys)
        c.saveKey(AiVendor.CLAUDE, claudeKey)
        assertEquals(AiKeyProblem.CANNOT_STORE, c.vendor(AiVendor.CLAUDE).problem)
        assertNull(settings.key(AiVendor.CLAUDE))
        assertTrue(transport.calls.isEmpty())
    }

    @Test
    fun `a key the provider refuses is reported but stays until the person removes it`() {
        transport.handler = { FakeAiTransport.status(400, """{"error":{"message":"API key not valid.","details":[{"reason":"API_KEY_INVALID"}]}}""") }
        val c = controller()
        c.saveKey(AiVendor.GEMINI, geminiKey)
        val ui = c.vendor(AiVendor.GEMINI)
        assertEquals(AiKeyCheck.REJECTED, ui.check)
        assertEquals(geminiKey, settings.key(AiVendor.GEMINI))
        assertFalse(geminiKey in ui.detail.orEmpty())

        c.forgetKey(AiVendor.GEMINI)
        assertNull(settings.key(AiVendor.GEMINI))
        assertFalse(c.vendor(AiVendor.GEMINI).hasKey)
        assertEquals(AiKeyCheck.UNKNOWN, c.vendor(AiVendor.GEMINI).check)
    }

    @Test
    fun `no connection is not the same as a refused key`() {
        transport.handler = { throw IOException("Unable to resolve host") }
        val c = controller()
        c.saveKey(AiVendor.GEMINI, geminiKey)
        assertEquals(AiKeyCheck.FAILED, c.vendor(AiVendor.GEMINI).check)
        assertTrue("Could not reach" in c.vendor(AiVendor.GEMINI).detail.orEmpty())
        assertEquals(geminiKey, settings.key(AiVendor.GEMINI))
    }

    @Test
    fun `checking a saved key again asks again`() {
        val c = controller()
        c.saveKey(AiVendor.GEMINI, geminiKey)
        transport.handler = { FakeAiTransport.status(429) }
        c.checkKey(AiVendor.GEMINI)
        assertEquals(2, transport.calls.size)
        assertEquals(AiKeyCheck.FAILED, c.vendor(AiVendor.GEMINI).check, "a rate limit does not mean the key is wrong")
        assertTrue("rate limit" in c.vendor(AiVendor.GEMINI).detail.orEmpty())
        c.checkKey(AiVendor.CLAUDE) // no key: nothing to ask
        assertEquals(2, transport.calls.size)
    }

    @Test
    fun `models are saved trimmed and a blank one means the default`() {
        val c = controller()
        c.setModel(AiVendor.GEMINI, "  gemini-3.5-flash-lite ")
        assertEquals("gemini-3.5-flash-lite", settings.model(AiVendor.GEMINI))
        assertEquals("gemini-3.5-flash-lite", c.vendor(AiVendor.GEMINI).model)
        c.setModel(AiVendor.GEMINI, "   ")
        assertNull(settings.model(AiVendor.GEMINI))
        assertEquals(AiVendor.GEMINI.defaultModel, c.vendor(AiVendor.GEMINI).model)
    }

    @Test
    fun `choosing a provider and a language is remembered`() {
        val c = controller()
        c.setChoice("template")
        assertEquals("template", settings.choice)
        c.setChoice("nonsense")
        assertEquals("template", settings.choice, "an unknown value is ignored")
        c.setLanguage("nl")
        assertEquals("nl", settings.language)
        c.setLanguage("xx")
        assertEquals("nl", settings.language)
        assertEquals("nl", c.state.value.language)
    }

    @Test
    fun `which provider writes the reports depends on the choice and the keys`() {
        fun active(choice: String, claude: Boolean, gemini: Boolean): ActiveAi {
            val s = FakeAiSettings(choice = choice)
            if (claude) s.setKey(AiVendor.CLAUDE, claudeKey)
            if (gemini) s.setKey(AiVendor.GEMINI, geminiKey)
            return AiController(s, transport, CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined).active()
        }
        assertEquals(ActiveAi.TEMPLATE, active("auto", claude = false, gemini = false))
        assertEquals(ActiveAi.CLAUDE, active("auto", claude = true, gemini = false))
        assertEquals(ActiveAi.GEMINI, active("auto", claude = false, gemini = true))
        assertEquals(ActiveAi.CLAUDE, active("auto", claude = true, gemini = true), "Claude comes first when both are there")
        assertEquals(ActiveAi.GEMINI, active("gemini", claude = true, gemini = true))
        assertEquals(ActiveAi.CLAUDE, active("anthropic", claude = true, gemini = true))
        assertEquals(ActiveAi.TEMPLATE, active("template", claude = true, gemini = true))
        assertEquals(ActiveAi.TEMPLATE, active("gemini", claude = true, gemini = false), "a chosen provider without a key falls back to the offline reports")
        assertEquals(ActiveAi.TEMPLATE, active("anthropic", claude = false, gemini = true))
    }

    @Test
    fun `the provider that is built matches the active one`() {
        settings.setKey(AiVendor.GEMINI, geminiKey)
        settings.setModel(AiVendor.GEMINI, "gemini-3.5-flash-lite")
        val c = controller()
        val gemini = assertIs<GeminiReportProvider>(c.provider())
        assertEquals("gemini-3.5-flash-lite", gemini.modelId)
        assertEquals(ActiveAi.GEMINI, c.state.value.active)

        settings.setKey(AiVendor.CLAUDE, claudeKey)
        c.setChoice("anthropic")
        assertIs<AnthropicReportProvider>(c.provider())
        c.setChoice("template")
        assertIs<TemplateReportProvider>(c.provider())
    }

    @Test
    fun `the active provider follows the keys the person adds and removes`() {
        val c = controller()
        assertEquals(ActiveAi.TEMPLATE, c.state.value.active)
        c.saveKey(AiVendor.GEMINI, geminiKey)
        assertEquals(ActiveAi.GEMINI, c.state.value.active)
        c.forgetKey(AiVendor.GEMINI)
        assertEquals(ActiveAi.TEMPLATE, c.state.value.active)
    }
}
