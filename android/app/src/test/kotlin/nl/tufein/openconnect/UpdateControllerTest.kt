package nl.tufein.openconnect

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import nl.tufein.openconnect.core.updates.UpdateHttp
import nl.tufein.openconnect.updates.UpdateController
import nl.tufein.openconnect.updates.UpdateError
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = OpenConnectApp::class)
class UpdateControllerTest {
    private val apk = "a tiny fake apk".toByteArray()
    private val hash = MessageDigest.getInstance("SHA-256").digest(apk).joinToString("") { "%02x".format(it) }
    private val release = """
        {"tag_name":"v0.48.0","body":"notes","assets":[
          {"name":"openconnect-v0.48.0.apk","size":${apk.size},
           "browser_download_url":"https://github.com/Tufein/OpenConnect/releases/download/v0.48.0/openconnect-v0.48.0.apk"},
          {"name":"openconnect-v0.48.0.apk.sha256",
           "browser_download_url":"https://github.com/Tufein/OpenConnect/releases/download/v0.48.0/openconnect-v0.48.0.apk.sha256"}
        ]}
    """.trimIndent()

    private fun http(checksum: String = hash) = UpdateHttp { url, _ ->
        when {
            url.contains("releases/latest") -> release.toByteArray()
            url.endsWith(".apk") -> apk
            url.endsWith(".sha256") -> "$checksum  openconnect-v0.48.0.apk".toByteArray()
            else -> throw IOException("unexpected URL $url")
        }
    }

    private fun controller(dir: File, net: UpdateHttp = http()) = UpdateController(
        dir = dir,
        currentVersion = "0.47.0",
        http = net,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    )

    private suspend fun waitFor(block: () -> Boolean) {
        val end = System.currentTimeMillis() + 5_000
        while (!block()) {
            if (System.currentTimeMillis() > end) throw AssertionError("timed out waiting for state")
            delay(20)
        }
    }

    private fun tempDir(): File =
        File(System.getProperty("java.io.tmpdir"), "oc-update-test-${System.nanoTime()}")

    @Test
    fun finds_a_new_release_verifies_and_keeps_the_apk(): Unit = runBlocking<Unit> {
        val dir = tempDir()
        val updates = controller(dir)
        updates.check()
        waitFor { updates.state.value.available?.version == "0.48.0" }
        updates.download()
        waitFor { updates.state.value.ready }
        val file = updates.apkFile()
        assertNotNull(file)
        assertTrue(file.exists())
        assertTrue(file.readBytes().contentEquals(apk))
        assertEquals(100, updates.state.value.progress)
        dir.deleteRecursively()
    }

    @Test
    fun a_wrong_checksum_never_leaves_a_ready_file(): Unit = runBlocking<Unit> {
        val dir = tempDir()
        val updates = controller(dir, http("0".repeat(64)))
        updates.check()
        waitFor { updates.state.value.available != null }
        updates.download()
        waitFor { updates.state.value.error == UpdateError.CHECKSUM }
        assertTrue(updates.apkFile() == null)
        dir.deleteRecursively()
    }

    @Test
    fun a_current_version_needs_no_download(): Unit = runBlocking<Unit> {
        val dir = tempDir()
        val updates = UpdateController(dir, "0.48.0", http(), CoroutineScope(SupervisorJob() + Dispatchers.Default))
        updates.check()
        waitFor { updates.state.value.upToDate }
        assertTrue(updates.state.value.available == null)
        dir.deleteRecursively()
    }
}
