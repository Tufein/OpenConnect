package nl.tufein.openconnect.core

import nl.tufein.openconnect.core.formats.Formats
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * v1.3.0 (milestone M13, "Verhuisdoos"): one import takes a whole archive. The archives here are made the way Strava
 * and Garmin describe their exports: a ZIP with an `activities` folder of `.fit.gz`, `.gpx` and `.tcx.gz` files (Strava), and a ZIP with
 * the watch's own FIT files in a ZIP of their own inside the export (Garmin). Nothing here has met a real export.
 */
class ArchiveImportTest {
    private val base = Instant.parse("2026-03-01T06:00:00Z")

    /** A run that starts [day] days after [base], at its own hour, so activities never collide by accident. */
    private fun run(day: Int, minutes: Int = 30, id: String = "t$day") = TestData.activity(
        id = id,
        start = base.plusSeconds(day * 86_400L),
        n = minutes * 6,
        distanceM = minutes * 180.0,
        durationS = minutes * 6 - 1.0,
        track = TestData.track(minutes * 6, base.plusSeconds(day * 86_400L)),
    )

    private fun fit(day: Int) = Formats.export(run(day), "fit").bytes
    private fun gpx(day: Int) = Formats.export(run(day), "gpx").bytes
    private fun tcx(day: Int) = Formats.export(run(day), "tcx").bytes

    private fun gz(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(bytes) }
        return out.toByteArray()
    }

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

    private fun import(
        bytes: ByteArray,
        repo: Repository,
        name: String = "export.zip",
        limits: ArchiveImport.Limits = ArchiveImport.Limits(),
        shouldStop: () -> Boolean = { false },
        onProgress: (ArchiveImport.Progress) -> Unit = {},
    ) = ArchiveImport.import(ByteArrayInputStream(bytes), name, repo, limits, shouldStop = shouldStop, onProgress = onProgress)

    private fun all(repo: Repository) = repo.listActivities(limit = Int.MAX_VALUE)

    @Test
    fun aZipWithAllThreeFormatsBringsInEveryActivity() {
        val repo = InMemoryRepository()
        val report = import(zip("activities/1.fit" to fit(1), "activities/2.gpx" to gpx(2), "activities/3.tcx" to tcx(3)), repo)
        assertEquals(3, report.imported)
        assertEquals(0, report.alreadyThere)
        assertEquals(0, report.failureCount)
        assertEquals(3, all(repo).size)
    }

    @Test
    fun gzippedMembersAreReadLikeStravaWritesThem() {
        val repo = InMemoryRepository()
        val report = import(zip("activities/10.fit.gz" to gz(fit(1)), "activities/11.gpx.gz" to gz(gpx(2)), "activities/12.tcx.gz" to gz(tcx(3))), repo)
        assertEquals(3, report.imported)
        assertEquals(3, all(repo).size)
    }

    @Test
    fun aZipInsideAZipIsWalkedLikeGarminsExport() {
        val inner = zip("111_ACTIVITY.fit" to fit(1), "222_ACTIVITY.fit" to fit(2))
        val outer = zip(
            "DI_CONNECT/DI-Connect-Uploaded-Files/UploadedFiles_0-_Part1.zip" to inner,
            "DI_CONNECT/DI-Connect-Fitness/summarizedActivities.json" to "[]".toByteArray(),
            "DI_CONNECT/DI-Connect-User/user_profile.json" to "{}".toByteArray(),
        )
        val repo = InMemoryRepository()
        val report = import(outer, repo)
        assertEquals(2, report.imported)
        assertEquals(2, report.skipped, "the two JSON files are not activities")
        assertEquals(0, report.failureCount)
    }

    @Test
    fun theSameArchiveAgainAddsNothing() {
        val archive = zip("a.fit" to fit(1), "b.gpx" to gpx(2), "c.fit.gz" to gz(fit(3)))
        val repo = InMemoryRepository()
        assertEquals(3, import(archive, repo).imported)
        val second = import(archive, repo)
        assertEquals(0, second.imported)
        assertEquals(3, second.alreadyThere)
        assertEquals(3, all(repo).size)
    }

    @Test
    fun anActivityThatStartsWithinNinetySecondsOfAnExistingOneIsNotAddedAndNotChanged() {
        val repo = InMemoryRepository()
        // The same run is already there from the Garmin account, 30 seconds off and with a name the person may have edited.
        val existing = TestData.activity(id = "garmin:77", start = base.plusSeconds(86_400 + 30), name = "Parkrun with Sam")
        repo.upsertActivity(existing)
        val report = import(zip("run.fit" to fit(1)), repo)
        assertEquals(0, report.imported)
        assertEquals(1, report.alreadyThere)
        val kept = all(repo).single()
        assertEquals("Parkrun with Sam", kept.name)
        assertEquals("garmin:77", kept.id)
    }

    @Test
    fun anActivityThatStartsTwoMinutesApartIsAnotherActivity() {
        val repo = InMemoryRepository()
        repo.upsertActivity(TestData.activity(id = "garmin:77", start = base.plusSeconds(86_400 + 120)))
        val report = import(zip("run.fit" to fit(1)), repo)
        assertEquals(1, report.imported)
        assertEquals(2, all(repo).size)
    }

    @Test
    fun theSameActivityInTwoPartsOfOneArchiveIsAddedOnce() {
        val part1 = zip("1.fit" to fit(1), "2.fit" to fit(2))
        val part2 = zip("2-again.fit" to fit(2), "3.fit" to fit(3))
        val repo = InMemoryRepository()
        val report = import(zip("Part1.zip" to part1, "Part2.zip" to part2), repo)
        assertEquals(3, report.imported)
        assertEquals(1, report.alreadyThere)
        assertEquals(3, all(repo).size)
    }

    @Test
    fun filesThatAreNotActivitiesAreSkippedAndCountedNotReportedAsFailures() {
        val route = """<?xml version="1.0"?><gpx version="1.1" creator="x"><rte><rtept lat="52.0" lon="5.0"/><rtept lat="52.1" lon="5.1"/></rte></gpx>"""
        val archive = zip(
            "activities.csv" to "id,name\n1,Run".toByteArray(),
            "media/photo.jpg" to byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 0x10),
            "routes/city.gpx" to route.toByteArray(),
            "README.txt" to "hello".toByteArray(),
            "activities/1.fit" to fit(1),
        )
        val repo = InMemoryRepository()
        val report = import(archive, repo)
        assertEquals(1, report.imported)
        assertEquals(4, report.skipped)
        assertEquals(0, report.failureCount)
    }

    @Test
    fun oneBrokenFileIsReportedByNameAndTheRestStillComesIn() {
        val broken = fit(2).copyOf(40) // cut off in the middle
        val repo = InMemoryRepository()
        val report = import(zip("good1.fit" to fit(1), "bad.fit" to broken, "good2.gpx" to gpx(3)), repo)
        assertEquals(2, report.imported)
        assertEquals(1, report.failureCount)
        assertEquals("bad.fit", report.failures.single().name)
        assertTrue(report.failures.single().reason.isNotBlank())
    }

    @Test
    fun aFileOverTheLimitFailsAndTheRestStillComesIn() {
        val repo = InMemoryRepository()
        val tight = ArchiveImport.Limits(maxFileBytes = fit(1).size + 10)
        val report = import(zip("small.fit" to fit(1), "huge.gpx" to gpx(2) + ByteArray(200_000) { ' '.code.toByte() }), repo, limits = tight)
        assertEquals(1, report.imported)
        assertEquals(1, report.failureCount)
        assertEquals("huge.gpx", report.failures.single().name)
    }

    @Test
    fun theTotalLimitStopsTheImportButKeepsWhatCameInBefore() {
        val repo = InMemoryRepository()
        val one = fit(1).size
        val report = import(
            zip("1.fit" to fit(1), "2.fit" to fit(2), "3.fit" to fit(3), "4.fit" to fit(4)),
            repo,
            limits = ArchiveImport.Limits(maxTotalBytes = one * 2L + 100),
        )
        assertEquals(ArchiveImport.Stop.TOO_LARGE, report.stopped)
        assertTrue(report.imported in 1..3, "some came in: ${report.imported}")
        assertEquals(report.imported, all(repo).size)
    }

    @Test
    fun theFileCountLimitStopsTheImport() {
        val repo = InMemoryRepository()
        val report = import(zip("1.fit" to fit(1), "2.fit" to fit(2), "3.fit" to fit(3)), repo, limits = ArchiveImport.Limits(maxEntries = 2))
        assertEquals(ArchiveImport.Stop.TOO_MANY_FILES, report.stopped)
        assertEquals(2, report.imported)
    }

    @Test
    fun zipsNestedDeeperThanTheLimitAreNotOpenedAndSaySo() {
        var bytes = zip("deep.fit" to fit(1))
        repeat(4) { bytes = zip("level.zip" to bytes) }
        val repo = InMemoryRepository()
        val report = import(bytes, repo, limits = ArchiveImport.Limits(maxDepth = 3))
        assertEquals(0, report.imported)
        assertEquals(1, report.failureCount)
        assertTrue(report.failures.single().reason.contains("nested", ignoreCase = true))
    }

    @Test
    fun aLooseFileOrAGzippedFileWorksToo() {
        val repo = InMemoryRepository()
        assertEquals(1, import(fit(1), repo, name = "run.fit").imported)
        assertEquals(1, import(gz(gpx(2)), repo, name = "run.gpx.gz").imported)
        assertEquals(2, all(repo).size)
    }

    @Test
    fun theKindComesFromTheContentWhenTheNameSaysNothing() {
        // A file picked from the phone's storage often has a name like "msf:1234" without an extension.
        val repo = InMemoryRepository()
        assertEquals(2, import(zip("a.fit" to fit(1), "b.gpx" to gpx(2)), repo, name = "msf:1234").imported)
        assertEquals(1, import(gz(fit(3)), repo, name = "document:99").imported)
        assertEquals(1, import(tcx(4), repo, name = "primary:Download/run").imported)
        assertEquals(4, all(repo).size)
    }

    @Test
    fun junkFilesFromAMacAreNotEvenCounted() {
        val repo = InMemoryRepository()
        val report = import(zip("__MACOSX/._1.fit" to byteArrayOf(0, 5, 22, 7), ".DS_Store" to byteArrayOf(1), "1.fit" to fit(1)), repo)
        assertEquals(1, report.imported)
        assertEquals(0, report.skipped)
        assertEquals(0, report.failureCount)
        assertEquals(1, report.entries)
    }

    @Test
    fun theReportNamesTheFirstAndLastDayItBroughtIn() {
        val report = import(zip("1.fit" to fit(5), "2.fit" to fit(1), "3.fit" to fit(9)), InMemoryRepository())
        assertEquals(base.plusSeconds(86_400), report.first)
        assertEquals(base.plusSeconds(9 * 86_400L), report.last)
    }

    @Test
    fun anExportOfOpenConnectItselfComesBackAndTellsThereIsABackupInside() {
        val source = InMemoryRepository()
        source.upsertActivity(run(1))
        source.upsertActivity(run(3, minutes = 45))
        source.upsertDaily(TestData.daily(LocalDate.parse("2026-03-02")))
        val out = ByteArrayOutputStream()
        ExportAll.write(source, UserData(), Instant.parse("2026-11-22T10:00:00Z"), out)

        val repo = InMemoryRepository()
        val report = import(out.toByteArray(), repo)
        // Each activity is inside twice (FIT and GPX): the second copy is "already there", not a duplicate.
        assertEquals(2, report.imported)
        assertEquals(2, report.alreadyThere)
        assertTrue(report.backupInside)
        assertEquals(2, all(repo).size)
        assertEquals(setOf(base.plusSeconds(86_400), base.plusSeconds(3 * 86_400L)), all(repo).map { it.startTime }.toSet())
    }

    @Test
    fun progressGrowsWhileItRunsAndStopCancelsAfterWhatCameIn() {
        val repo = InMemoryRepository()
        val seen = ArrayList<ArchiveImport.Progress>()
        var asked = 0
        val report = import(
            zip("1.fit" to fit(1), "2.fit" to fit(2), "3.fit" to fit(3)),
            repo,
            shouldStop = { ++asked > 1 },
            onProgress = { seen += it },
        )
        assertEquals(ArchiveImport.Stop.CANCELLED, report.stopped)
        assertEquals(1, report.imported)
        assertEquals(1, all(repo).size)
        assertTrue(seen.isNotEmpty())
        assertEquals(seen.map { it.entries }, seen.map { it.entries }.sorted())
    }

    @Test
    fun aTruncatedZipKeepsWhatWasReadBeforeTheDamage() {
        val whole = zip("1.fit" to fit(1), "2.fit" to fit(2), "3.fit" to fit(3))
        val cut = whole.copyOf(whole.size - 400) // inside the data of the third file
        val repo = InMemoryRepository()
        val report = import(cut, repo)
        assertEquals(2, report.imported, "the first two files were whole")
        assertTrue(report.failureCount >= 1)
        assertNotNull(report.failures.firstOrNull())
    }

    @Test
    fun writesAreBatchedInsideTransactionsAndFlushedAtTheEnd() {
        val repo = InMemoryRepository()
        var transactions = 0
        val report = ArchiveImport.import(
            ByteArrayInputStream(zip("1.fit" to fit(1), "2.fit" to fit(2), "3.fit" to fit(3))),
            "x.zip",
            repo,
            ArchiveImport.Limits(),
            transaction = { block -> transactions++; block() },
            batch = 2,
        )
        assertEquals(3, report.imported)
        assertEquals(2, transactions, "one transaction for the first two, one at the end for the last")
        assertEquals(3, all(repo).size)
    }

    @Test
    fun reportsOfSeveralPickedFilesAddUpIntoOne() {
        val a = import(zip("1.fit" to fit(2), "x.csv" to "a".toByteArray()), InMemoryRepository())
        val b = import(zip("2.fit" to fit(7), "bad.fit" to fit(3).copyOf(40)), InMemoryRepository())
        val both = a + b
        assertEquals(2, both.imported)
        assertEquals(1, both.skipped)
        assertEquals(1, both.failureCount)
        assertEquals(base.plusSeconds(2 * 86_400L), both.first)
        assertEquals(base.plusSeconds(7 * 86_400L), both.last)
        assertNull(both.stopped)
    }

    @Test
    fun anEmptyArchiveIsNotAnError() {
        val report = import(zip(), InMemoryRepository())
        assertEquals(0, report.imported)
        assertEquals(0, report.failureCount)
        assertNull(report.stopped)
        assertFalse(report.backupInside)
    }
}
