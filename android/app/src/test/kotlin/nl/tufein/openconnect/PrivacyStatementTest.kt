package nl.tufein.openconnect

import nl.tufein.openconnect.core.PrivacyStatement
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * v0.59.0 (M6, trust): the privacy statement says everywhere the app can send data, and the code cannot grow another address
 * without the statement changing. This reads the source for every address and checks it against [PrivacyStatement], then
 * checks that the app text (English and Dutch) and docs/PRIVACY.md name each address the app calls.
 */
class PrivacyStatementTest {
    private val sources = listOf("src/main/kotlin", "../core/src/main/kotlin", "src/main/res/values/strings.xml", "src/main/AndroidManifest.xml")

    private fun hostsInTheCode(): Set<String> {
        val pattern = Regex("""https?://([A-Za-z0-9][A-Za-z0-9.-]*[A-Za-z0-9])""")
        val found = LinkedHashSet<String>()
        for (path in sources) {
            val root = File(path)
            val files = if (root.isDirectory) root.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "xml") }.toList() else listOf(root)
            assertTrue(files.isNotEmpty(), "no source at $path")
            for (file in files) pattern.findAll(file.readText()).forEach { found += it.groupValues[1].lowercase() }
        }
        return found
    }

    @Test
    fun everyAddressInTheCodeIsListedInThePrivacyStatement() {
        val unknown = hostsInTheCode() - PrivacyStatement.allKnown
        assertEquals(emptySet(), unknown, "these addresses are in the code but not in PrivacyStatement (core/Privacy.kt): add each as called, link only or namespace")
    }

    @Test
    fun everyAddressTheAppCallsIsNamedInTheAppTextInBothLanguagesAndInThePrivacyDocument() {
        val english = File("src/main/res/values/strings.xml").readText()
        val dutch = File("src/main/res/values-nl/strings.xml").readText()
        val document = File("../../docs/PRIVACY.md").readText()
        fun text(xml: String, key: String): String {
            val match = Regex("""<string name="privacy_host_$key"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL).find(xml)
            assertTrue(match != null, "missing privacy_host_$key")
            return match.groupValues[1]
        }
        for ((key, hosts) in PrivacyStatement.called) {
            for (host in hosts) {
                assertTrue(host in text(english, key), "the English text privacy_host_$key does not name $host")
                assertTrue(host in text(dutch, key), "the Dutch text privacy_host_$key does not name $host")
                assertTrue(host in document, "docs/PRIVACY.md does not name $host")
            }
        }
    }

    @Test
    fun theStatementNamesTheHostsTheCodeReallyCalls() {
        // The other direction: an address that is listed as called but is nowhere in the code is a stale entry (github.com is
        // reached through the download links in the release list, so the code only has it as the repository link).
        val inCode = hostsInTheCode()
        val stale = PrivacyStatement.called.values.flatten().filter { it !in inCode }
        assertEquals(emptyList(), stale)
    }
}
