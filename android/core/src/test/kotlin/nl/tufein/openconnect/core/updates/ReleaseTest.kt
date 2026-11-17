package nl.tufein.openconnect.core.updates

import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReleaseTest {
    private val release = """
        {
          "tag_name": "v0.48.0",
          "body": "A new version",
          "assets": [
            {"name": "openconnect-v0.48.0.apk", "size": 11000000,
             "browser_download_url": "https://github.com/Tufein/OpenConnect/releases/download/v0.48.0/openconnect-v0.48.0.apk"},
            {"name": "openconnect-v0.48.0.apk.sha256",
             "browser_download_url": "https://github.com/Tufein/OpenConnect/releases/download/v0.48.0/openconnect-v0.48.0.apk.sha256"},
            {"name": "openconnect-0.48.0-py3-none-any.whl",
             "browser_download_url": "https://github.com/Tufein/OpenConnect/releases/download/v0.48.0/whl"}
          ]
        }
    """.trimIndent()

    @Test
    fun parses_the_apk_and_its_checksum_from_a_release() {
        val info = parseRelease(release)
        assertNotNull(info)
        assertEquals("0.48.0", info.version)
        assertTrue(info.apkUrl.endsWith("openconnect-v0.48.0.apk"))
        assertTrue(info.checksumUrl!!.endsWith(".apk.sha256"))
        assertEquals(11_000_000L, info.sizeBytes)
        assertEquals("A new version", info.notes)
    }

    @Test
    fun releases_without_an_apk_are_not_usable() {
        assertNull(parseRelease("""{"tag_name": "v0.48.0", "assets": []}"""))
        assertNull(parseRelease("not json"))
    }

    @Test
    fun version_comparison_handles_numbers_and_missing_parts() {
        assertFalse(isNewer("0.47.0", "0.47.0"))
        assertTrue(isNewer("0.47.0", "0.48.0"))
        assertTrue(isNewer("0.9.9", "0.10.0"))
        assertTrue(isNewer("0.47", "0.47.1"))
        assertFalse(isNewer("0.48.0", "v0.47.9"))
    }

    @Test
    fun checksum_must_match_the_downloaded_bytes() {
        val bytes = "an apk".toByteArray()
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertTrue(matchesSha256(bytes, "$hash  openconnect-v0.48.0.apk\n"))
        assertFalse(matchesSha256("other bytes".toByteArray(), "$hash  file.apk"))
        assertFalse(matchesSha256(bytes, "not a checksum"))
    }
}
