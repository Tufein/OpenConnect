package nl.tufein.openconnect

import android.net.Uri
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import nl.tufein.openconnect.core.Activity
import nl.tufein.openconnect.core.Sport
import nl.tufein.openconnect.core.TrackPoint
import nl.tufein.openconnect.core.formats.Formats
import nl.tufein.openconnect.ui.AppViewModel
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertTrue

/**
 * v1.3.0 (M13, "Verhuisdoos"): the import button takes a whole archive, shows what it did on the Settings screen
 * and writes a line in the sync log. The archive logic itself is tested in `core`; this checks it is wired to the
 * app, with a real database and a file the phone could have handed over.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi", application = OpenConnectApp::class)
class ArchiveImportScreenTest : AppTestBase() {
    private val base = Instant.parse("2026-02-01T06:00:00Z")

    private fun run(day: Int): Activity {
        val start = base.plusSeconds(day * 86_400L)
        return Activity(
            id = "x$day", name = "Run", sport = Sport.RUNNING, startTime = start, durationS = 299.0, distanceM = 900.0,
            track = (0 until 300).map { i ->
                TrackPoint(start.plusSeconds(i.toLong()), lat = 52.0 + i * 0.00004, lon = 4.9 + i * 0.00002, ele = 5.0, hr = 140 + i % 10)
            },
        )
    }

    private fun gz(bytes: ByteArray): ByteArray = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(bytes) } }.toByteArray()

    private fun archive(): File {
        val file = File.createTempFile("export", ".zip")
        ZipOutputStream(file.outputStream()).use { z ->
            fun put(name: String, bytes: ByteArray) {
                z.putNextEntry(ZipEntry(name))
                z.write(bytes)
                z.closeEntry()
            }
            put("activities/1.fit", Formats.export(run(1), "fit").bytes)
            put("activities/2.fit.gz", gz(Formats.export(run(2), "fit").bytes))
            put("activities/3.gpx", Formats.export(run(3), "gpx").bytes)
            put("activities.csv", "id,name\n1,Run\n".toByteArray())
        }
        return file
    }

    private fun archiveWithNames(): File {
        val file = File.createTempFile("strava", ".zip")
        ZipOutputStream(file.outputStream()).use { z ->
            fun put(name: String, bytes: ByteArray) {
                z.putNextEntry(ZipEntry(name))
                z.write(bytes)
                z.closeEntry()
            }
            put("activities.csv", "Activity Name,Filename\r\nLunch run,activities/1.fit\r\n".toByteArray())
            put("activities/1.fit", Formats.export(run(1), "fit").bytes)
            put("DI_CONNECT/DI-Connect-Aggregator/UDSFile_2020-01-01_2020-04-10.json", """[{"calendarDate": "2020-01-01", "totalSteps": 8123}]""".toByteArray())
        }
        return file
    }

    private fun importFile(file: File) {
        scenario.onActivity { activity ->
            ViewModelProvider(activity)[AppViewModel::class.java].importUris(listOf(Uri.fromFile(file)))
        }
    }

    @Test
    fun `a whole archive is imported and the result is shown in Settings and the sync log`() {
        launchFresh()
        waitForText("Load demo data")
        tab("Settings").performClick()
        rule.waitForIdle()
        scrollToText("Import files or an archive")

        importFile(archive())
        // "files skipped" only appears in the finished report; the progress line ("Importing... 3 new") also says "new".
        waitForText("files skipped", substring = true)
        assertTrue(hasText("3 new, 0 already there, 1 files skipped", substring = true), "the CSV is skipped, not a failure")
        assertTrue(hasText("0 failed", substring = true))
        shot("43-settings-archive-import")

        // The same archive again changes nothing: all three are already there.
        importFile(archive())
        waitForText("0 new, 3 already there", substring = true)
    }

    @Test
    fun `a file that is not an archive or an activity is reported and nothing is added`() {
        launchFresh()
        waitForText("Load demo data")
        tab("Settings").performClick()
        rule.waitForIdle()
        scrollToText("Import files or an archive")

        val junk = File.createTempFile("notes", ".txt").also { it.writeText("hello") }
        importFile(junk)
        waitForText("0 new, 0 already there, 1 files skipped", substring = true)
    }

    @Test
    fun `names from the archive and the shape of unread JSON files show up and the report can be copied`() {
        launchFresh()
        waitForText("Load demo data")
        tab("Settings").performClick()
        rule.waitForIdle()
        scrollToText("Import files or an archive")

        importFile(archiveWithNames())
        waitForText("files skipped", substring = true)
        assertTrue(hasText("1 activities got the name they have in the archive", substring = true))
        assertTrue(hasText("1 kind(s) of JSON file were not read", substring = true))
        scrollToText("Copy report")
        clickText("Copy report")
        waitForText("Report copied")
    }
}
