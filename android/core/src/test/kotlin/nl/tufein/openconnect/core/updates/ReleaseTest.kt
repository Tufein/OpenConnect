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
    fun a_release_candidate_is_older_than_its_final_release_and_never_offered_to_a_final_install() {
        assertTrue(isNewer("1.0.0-rc.1", "1.0.0"), "a phone on rc.1 is offered 1.0.0")
        assertFalse(isNewer("1.0.0", "1.0.0-rc.1"), "a phone on 1.0.0 is not offered rc.1")
        assertFalse(isNewer("1.0.0", "1.0.0-rc.2"))
        assertTrue(isNewer("1.0.0-rc.1", "1.0.0-rc.2"))
        assertTrue(isNewer("1.0.0-rc.2", "1.0.0-rc.10"), "rc.10 is later than rc.2, as numbers")
        assertTrue(isNewer("1.0.0-rc", "1.0.0-rc.1"))
        assertTrue(isNewer("1.0.0-beta.1", "1.0.0-rc.1"))
        assertTrue(isNewer("0.57.0", "1.0.0-rc.1"), "a candidate is newer than an older release")
        assertTrue(isNewer("1.0.0-rc.3", "1.0.1"))
        assertFalse(isNewer("1.0.0-rc.1", "v1.0.0-rc.1+build.7"), "build metadata does not count")
    }

    @Test
    fun the_updater_refuses_a_pre_release_or_a_draft_even_when_one_is_handed_over() {
        val apk = """"assets": [{"name": "openconnect-v1.0.0-rc.1.apk", "browser_download_url": "https://github.com/x/openconnect-v1.0.0-rc.1.apk"}]"""
        assertNotNull(parseRelease("""{"tag_name": "v1.0.0-rc.1", "prerelease": false, "draft": false, $apk}"""))
        assertNull(parseRelease("""{"tag_name": "v1.0.0-rc.1", "prerelease": true, $apk}"""))
        assertNull(parseRelease("""{"tag_name": "v1.0.0-rc.1", "draft": true, $apk}"""))
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
