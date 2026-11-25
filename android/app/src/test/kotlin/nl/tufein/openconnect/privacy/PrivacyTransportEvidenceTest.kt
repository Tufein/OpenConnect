package nl.tufein.openconnect.privacy

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import nl.tufein.openconnect.ai.FakeAiTransport
import nl.tufein.openconnect.core.privacy.PrivacyRegister
import nl.tufein.openconnect.core.reports.AnthropicReportProvider
import nl.tufein.openconnect.core.reports.GeminiReportProvider
import nl.tufein.openconnect.core.updates.UpdateHttp
import nl.tufein.openconnect.updates.UpdateController
import org.junit.Test
import java.io.File
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A small evidence layer under the privacy register: the fields that are easiest to prove from the outside are checked
 * against the real providers and the updater. It does not replace a live network capture; it does catch a key moving into
 * a URL/body and the updater growing a host that the register does not name.
 */
class PrivacyTransportEvidenceTest {
    private val geminiKey = "AIzaSyA-0123456789abcdefghijklmnopqrstu"
    private val claudeKey = "sk-ant-api03-AbCdEfGhIjKlMnOpQrStUvWxYz_0123456789-AbCdEf"

    @Test
    fun aGeminiKeyTravelsOnlyInItsHeader() {
        val transport = FakeAiTransport { FakeAiTransport.gemini("OK") }
        GeminiReportProvider(geminiKey, transport).checkKey()
        val call = transport.calls.single()
        assertEquals(geminiKey, call.headers["x-goog-api-key"])
        assertFalse(geminiKey in call.url)
        assertFalse(geminiKey in call.body)
    }

    @Test
    fun aClaudeKeyTravelsOnlyInItsHeader() {
        val transport = FakeAiTransport { FakeAiTransport.claude("OK") }
        AnthropicReportProvider(claudeKey, transport).checkKey()
        val call = transport.calls.single()
        assertEquals(claudeKey, call.headers["x-api-key"])
        assertFalse(claudeKey in call.url)
        assertFalse(claudeKey in call.body)
    }

    @Test
    fun theUpdaterOnlyCallsHostsTheRegisterNames() = runBlocking<Unit> {
        val urls = mutableListOf<String>()
        val release = """{"tag_name":"v9.9.9","body":"","assets":[]}"""
        val http = UpdateHttp { url, _ ->
            urls += url
            if (url.contains("releases/latest")) release.toByteArray() else ByteArray(0)
        }
        val dir = File(System.getProperty("java.io.tmpdir"), "oc-privacy-update-${System.nanoTime()}")
        val controller = UpdateController(
            dir = dir,
            currentVersion = "0.0.0",
            http = http,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            sameSigner = { true },
        )
        controller.check()
        val deadline = System.currentTimeMillis() + 5_000
        while (urls.isEmpty() && System.currentTimeMillis() < deadline) delay(20)
        assertTrue(urls.isNotEmpty(), "the updater did not make a request")
        for (url in urls) {
            val host = URI(url).host
            assertTrue(host in PrivacyRegister.activeHosts, "the updater called an undeclared host: $host")
        }
        dir.deleteRecursively()
    }
}
