package nl.tufein.openconnect

import org.junit.Test
import java.io.File
import kotlin.test.assertTrue

/**
 * The privacy statement says Android's cloud backup and device transfer are off. That is a manifest promise, not a
 * preference a user can change, so it is checked here rather than only in prose.
 */
class PrivacyBackupTest {
    @Test
    fun androidBackupAndDeviceTransferAreOff() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(
            Regex("""android:allowBackup\s*=\s*"false"""").containsMatchIn(manifest),
            "the manifest must keep android:allowBackup=false",
        )
        assertTrue(
            Regex("""android:dataExtractionRules\s*=\s*"@xml/data_extraction_rules"""").containsMatchIn(manifest),
            "the manifest must point at the data extraction rules",
        )
        val rules = File("src/main/res/xml/data_extraction_rules.xml").readText()
        for (tag in listOf("cloud-backup", "device-transfer")) {
            assertTrue("<$tag>" in rules, "the extraction rules must cover $tag")
        }
    }
}
