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

    private fun controller(dir: File, net: UpdateHttp = http(), signer: (File) -> Boolean = { true }) = UpdateController(
        dir = dir,
        currentVersion = "0.47.0",
        http = net,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        sameSigner = signer,
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
    fun a_release_without_a_checksum_file_is_refused_not_trusted(): Unit = runBlocking<Unit> {
        val dir = tempDir()
        val noChecksum = release.replace(Regex(""",\s*\{"name":"openconnect-v0.48.0.apk.sha256".*?\}(?=\s*\])""", RegexOption.DOT_MATCHES_ALL), "")
        assertTrue(!noChecksum.contains("sha256"), "the fake release has no checksum asset")
        val net = UpdateHttp { url, _ ->
            when {
                url.contains("releases/latest") -> noChecksum.toByteArray()
                url.endsWith(".apk") -> apk
                else -> throw IOException("unexpected URL $url")
            }
        }
        val updates = controller(dir, net)
        updates.check()
        waitFor { updates.state.value.available != null }
        updates.download()
        waitFor { updates.state.value.error == UpdateError.NO_CHECKSUM }
        assertTrue(updates.apkFile() == null)
        assertTrue(File(dir, "openconnect-v0.48.0.apk").exists().not(), "an unverified file is not kept")
        dir.deleteRecursively()
    }

    @Test
    fun an_apk_signed_by_someone_else_is_refused_and_removed(): Unit = runBlocking<Unit> {
        val dir = tempDir()
        val updates = controller(dir, signer = { false })
        updates.check()
        waitFor { updates.state.value.available != null }
        updates.download()
        waitFor { updates.state.value.error == UpdateError.SIGNATURE }
        assertTrue(updates.apkFile() == null)
        assertTrue(File(dir, "openconnect-v0.48.0.apk").exists().not(), "a file with the wrong signer is deleted")
        dir.deleteRecursively()
    }

    @Test
    fun an_apk_with_the_right_checksum_and_signer_is_ready(): Unit = runBlocking<Unit> {
        val dir = tempDir()
        val checked = ArrayList<String>()
        val updates = controller(dir, signer = { checked += it.name; true })
        updates.check()
        waitFor { updates.state.value.available != null }
        updates.download()
        waitFor { updates.state.value.ready }
        assertEquals(listOf("openconnect-v0.48.0.apk"), checked, "the signer is checked on the file that will be installed")
        assertNotNull(updates.apkFile())
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
