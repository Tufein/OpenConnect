package nl.tufein.openconnect.core

import nl.tufein.openconnect.core.formats.Formats
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * v1.4.0 (milestone M14, "Namen"): the files in an export have no names of their own; Strava keeps them in
 * `activities.csv` and Garmin in `summarizedActivities.json`. The import gives each activity its real name, and it
 * writes down the shape (file name pattern and key names, never a value) of the JSON files it did not read, so a real
 * export can show what the daily files look like. Archives here are made to the layout the sources describe; none is real.
 */
class ArchiveNamesTest {
    private val base = Instant.parse("2026-03-01T06:00:00Z")

    private fun at(day: Int) = base.plusSeconds(day * 86_400L)

    private fun run(day: Int, name: String = "Morning Run") = TestData.activity(
        id = "t$day", start = at(day), n = 180, distanceM = 900.0, durationS = 179.0, track = TestData.track(180, at(day)), name = name,
    )

    private fun fit(day: Int) = Formats.export(run(day), "fit").bytes

    private fun gz(bytes: ByteArray): ByteArray = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(bytes) } }.toByteArray()

    private fun zip(vararg files: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            for ((name, bytes) in files) {
                z.putNextEntry(ZipEntry(name))
                z.write(bytes)
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun import(bytes: ByteArray, repo: Repository, name: String = "export.zip") =
        ArchiveImport.import(ByteArrayInputStream(bytes), name, repo)

    private fun all(repo: Repository) = repo.listActivities(limit = Int.MAX_VALUE).sortedBy { it.startTime }

    private val stravaCsv = "Activity ID,Activity Date,Activity Name,Activity Type,Activity Description,Elapsed Time,Distance,Filename,Elapsed Time\r\n" +
        "111,\"Mar 2, 2026, 6:00:00 AM\",Lunch run,Run,,300,5,activities/111.fit.gz,300\r\n" +
        "222,\"Mar 3, 2026, 6:00:00 AM\",\"Hills, then \"\"coffee\"\"\",Run,\"A long\nnote\",300,5,activities/222.fit,300\r\n"

    // --- Strava ---------------------------------------------------------------------------------------------------------

    @Test
    fun stravaNamesComeFromTheCsvWhetherItIsFirstOrLastInTheArchive() {
        val files = arrayOf("activities/111.fit.gz" to gz(fit(1)), "activities/222.fit" to fit(2))
        for (csvFirst in listOf(true, false)) {
            val repo = InMemoryRepository()
            val entries = if (csvFirst) arrayOf("activities.csv" to stravaCsv.toByteArray()) + files else files + arrayOf("activities.csv" to stravaCsv.toByteArray())
            val report = import(zip(*entries), repo)
            assertEquals(2, report.imported)
            assertEquals(2, report.named)
            assertEquals(listOf("Lunch run", "Hills, then \"coffee\""), all(repo).map { it.name }, "csv first: $csvFirst")
            assertEquals(0, report.skipped, "the CSV was read, so it is not a skipped file")
        }
    }

    @Test
    fun theCsvMatchesByFileNameEvenWhenTheArchiveHasAFolderInFront() {
        val repo = InMemoryRepository()
        val report = import(zip("strava_export_99/activities.csv" to stravaCsv.toByteArray(), "strava_export_99/activities/111.fit.gz" to gz(fit(1))), repo)
        assertEquals(1, report.named)
        assertEquals("Lunch run", all(repo).single().name)
    }

    @Test
    fun aCsvWithoutTheColumnsItNeedsChangesNothingAndBreaksNothing() {
        val repo = InMemoryRepository()
        val report = import(zip("activities.csv" to "id,title\n1,Run\n".toByteArray(), "activities/111.fit.gz" to gz(fit(1))), repo)
        assertEquals(1, report.imported)
        assertEquals(0, report.named)
        assertEquals(0, report.failureCount)
        assertEquals("Running 2026-03-02", all(repo).single().name)
    }

    @Test
    fun theCsvParserHandlesQuotesNewlinesBomAndRepeatedHeaders() {
        val rows = SimpleCsv.parse("﻿a,b,a\r\n1,\"x,y\",\"say \"\"hi\"\"\"\r\n\"line1\nline2\",,3\r\n")
        assertEquals(listOf("a", "b", "a"), rows[0])
        assertEquals(listOf("1", "x,y", "say \"hi\""), rows[1])
        assertEquals(listOf("line1\nline2", "", "3"), rows[2])
        assertEquals(3, rows.size)
    }

    @Test
    fun anActivityImportedBeforeGetsItsNameWhenTheArchiveComesAgainButAnEditedNameStays() {
        val repo = InMemoryRepository()
        val plain = zip("activities/111.fit.gz" to gz(fit(1)), "activities/222.fit" to fit(2))
        import(plain, repo) // v1.3.0 style: no CSV, default names
        val second = all(repo)[1]
        repo.upsertActivity(repo.getActivity(second.id)!!.copy(name = "My own title"))

        val report = import(zip("activities.csv" to stravaCsv.toByteArray(), "activities/111.fit.gz" to gz(fit(1)), "activities/222.fit" to fit(2)), repo)
        assertEquals(0, report.imported)
        assertEquals(2, report.alreadyThere)
        assertEquals(1, report.named)
        assertEquals(listOf("Lunch run", "My own title"), all(repo).map { it.name })
    }

    @Test
    fun anActivityThatCameFromAnotherSourceIsNeverRenamed() {
        val repo = InMemoryRepository()
        repo.upsertActivity(TestData.activity(id = "garmin:5", start = at(1).plusSeconds(20), name = "Running 2026-03-02"))
        val report = import(zip("activities.csv" to stravaCsv.toByteArray(), "activities/111.fit.gz" to gz(fit(1))), repo)
        assertEquals(1, report.alreadyThere)
        assertEquals(0, report.named)
        assertEquals("Running 2026-03-02", all(repo).single().name)
    }

    @Test
    fun namesAreCleanedBlankOnesIgnoredAndLongOnesCut() {
        val long = "x".repeat(300)
        val csv = "Activity Name,Filename\r\n" +
            "\"Bell\u0007 and\ttab\",activities/1.fit\r\n" +
            ",activities/2.fit\r\n" +
            "$long,activities/3.fit\r\n"
        val repo = InMemoryRepository()
        val report = import(zip("activities.csv" to csv.toByteArray(), "activities/1.fit" to fit(1), "activities/2.fit" to fit(2), "activities/3.fit" to fit(3)), repo)
        assertEquals(2, report.named)
        val names = all(repo).map { it.name }
        assertEquals("Bell and tab", names[0])
        assertEquals("Running 2026-03-03", names[1])
        assertEquals(120, names[2].length)
    }

    // --- Garmin ---------------------------------------------------------------------------------------------------------

    private fun garminJson(vararg entries: Pair<Long, String>, wrapped: Boolean): String {
        val items = entries.joinToString(",") { (ms, name) -> """{"activityId": ${ms / 1000}, "name": "$name", "activityType": "running", "startTimeGmt": $ms, "distance": 900.0}""" }
        return if (wrapped) """[{"summarizedActivitiesExport": [$items]}]""" else "[$items]"
    }

    @Test
    fun garminNamesComeFromSummarizedActivitiesAndMatchOnStartTimeInBothKnownShapes() {
        for (wrapped in listOf(true, false)) {
            val repo = InMemoryRepository()
            val json = garminJson(at(1).toEpochMilli() + 30_000 to "Utrecht Hardlopen", at(2).toEpochMilli() to "Heuvels", at(9).toEpochMilli() to "Another day", wrapped = wrapped)
            val inner = zip("111_ACTIVITY.fit" to fit(1), "222_ACTIVITY.fit" to fit(2))
            val report = import(
                zip(
                    "DI_CONNECT/DI-Connect-Fitness/user@example.com_0_summarizedActivities.json" to json.toByteArray(),
                    "DI_CONNECT/DI-Connect-Uploaded-Files/UploadedFiles_0-_Part1.zip" to inner,
                ),
                repo,
            )
            assertEquals(2, report.imported)
            assertEquals(2, report.named, "wrapped: $wrapped")
            assertEquals(listOf("Utrecht Hardlopen", "Heuvels"), all(repo).map { it.name })
            assertEquals(0, report.skipped)
        }
    }

    @Test
    fun aGarminNameOnlyAppliesWithinNinetySecondsOfTheStart() {
        val repo = InMemoryRepository()
        val json = garminJson(at(1).toEpochMilli() + 200_000 to "Too far off", wrapped = false)
        val report = import(zip("summarizedActivities.json" to json.toByteArray(), "1.fit" to fit(1)), repo)
        assertEquals(0, report.named)
        assertEquals("Running 2026-03-02", all(repo).single().name)
    }

    @Test
    fun aSummarizedActivitiesFileThatIsNotJsonIsReportedNotFatal() {
        val repo = InMemoryRepository()
        val report = import(zip("summarizedActivities.json" to "not json at all".toByteArray(), "1.fit" to fit(1)), repo)
        assertEquals(1, report.imported)
        assertEquals(0, report.named)
        assertEquals(1, report.failureCount)
        assertEquals("summarizedActivities.json", report.failures.single().name)
    }

    // --- The JSON files that were not read ------------------------------------------------------------------------------

    @Test
    fun theShapeOfUnreadJsonIsWrittenDownWithKeyNamesOnly() {
        val uds = """[{"calendarDate": "2020-01-01", "totalSteps": 8123, "restingHeartRate": 51, "userSecretNote": "hunter2 is my password", "2020-01-01": 1}]"""
        val sleep = """{"sleepData": [{"sleepStartTimestampGMT": "2020-01-01T22:00:00.0", "deepSleepSeconds": 5400}]}"""
        val repo = InMemoryRepository()
        val report = import(
            zip(
                "DI_CONNECT/DI-Connect-Aggregator/UDSFile_2020-01-01_2020-04-10.json" to uds.toByteArray(),
                "DI_CONNECT/DI-Connect-Aggregator/UDSFile_2020-04-11_2020-07-20.json" to uds.toByteArray(),
                "DI_CONNECT/DI-Connect-Wellness/user@example.com_0_sleepData.json" to sleep.toByteArray(),
            ),
            repo,
        )
        assertEquals(3, report.skipped)
        assertEquals(2, report.shapes.size)
        val udsShape = report.shapes.single { it.pattern.startsWith("UDSFile") }
        assertEquals(2, udsShape.files)
        assertEquals("UDSFile_#-#-#_#-#-#.json", udsShape.pattern)
        assertTrue("totalSteps" in udsShape.keys && "calendarDate" in udsShape.keys && "restingHeartRate" in udsShape.keys)
        assertFalse("2020-01-01" in udsShape.keys, "a key that is a date is data, not a key name")
        val sleepShape = report.shapes.single { it.pattern.contains("sleepData") }
        assertFalse(sleepShape.pattern.contains("example.com"), "an e-mail address in a file name is never kept")
        assertTrue("deepSleepSeconds" in sleepShape.keys)
    }

    @Test
    fun theDiagnosticsTextHasCountsShapesAndNamesButNoValuesAndNoAddresses() {
        val uds = """[{"calendarDate": "2020-01-01", "totalSteps": 8123, "secretValue": "hunter2"}]"""
        val report = import(
            zip(
                "DI_CONNECT/DI-Connect-Aggregator/UDSFile_2020-01-01_2020-04-10.json" to uds.toByteArray(),
                "me@example.com_bad.fit" to fit(1).copyOf(40),
                "1.fit" to fit(2),
            ),
            InMemoryRepository(),
        )
        val text = report.diagnostics()
        assertTrue("totalSteps" in text)
        assertTrue("new: 1" in text && "failed: 1" in text, text)
        assertFalse("hunter2" in text)
        assertFalse("8123" in text)
        assertFalse("example.com" in text, "an address inside a file name is blanked")
    }

    @Test
    fun noMoreThanTwentyFivePatternsAreKept() {
        val files = (1..40).map { "x/file_${"a".repeat(it)}.json" to """{"k$it": 1}""".toByteArray() }.toTypedArray()
        val report = import(zip(*files), InMemoryRepository())
        assertEquals(25, report.shapes.size)
        assertEquals(40, report.skipped)
    }

    @Test
    fun reportsOfSeveralFilesMergeTheirNamesAndShapes() {
        val json = """[{"totalSteps": 1}]""".toByteArray()
        val a = import(zip("UDSFile_1.json" to json, "activities.csv" to stravaCsv.toByteArray(), "activities/111.fit.gz" to gz(fit(1))), InMemoryRepository())
        val b = import(zip("UDSFile_2.json" to json), InMemoryRepository())
        val both = a + b
        assertEquals(1, both.named)
        assertEquals(1, both.shapes.size)
        assertEquals(2, both.shapes.single().files)
        assertNull(both.stopped)
    }
}
