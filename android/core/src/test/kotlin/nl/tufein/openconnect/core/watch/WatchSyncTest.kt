package nl.tufein.openconnect.core.watch

import nl.tufein.openconnect.core.InMemoryRepository
import nl.tufein.openconnect.core.SleepSummary
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WatchSyncTest {
    private val amsterdam = ZoneId.of("Europe/Amsterdam")
    private val d10 = LocalDate.parse("2026-06-10")
    private val d11 = LocalDate.parse("2026-06-11")

    private fun bytes(path: String): ByteArray =
        checkNotNull(WatchSyncTest::class.java.getResourceAsStream(path)) { "missing $path" }.readBytes()

    private fun watch(extra: Map<String, ByteArray> = emptyMap()) = InMemoryWatchSource(
        deviceName = "Forerunner 265",
        files = mapOf(
            "Monitor/46DB3F05.FIT" to bytes("/watch/monitoring.fit"),
            "Sleep/46DC0001.FIT" to bytes("/watch/sleep.fit"),
            "HSA/46DB0002.FIT" to bytes("/watch/hsa.fit"),
            "Activity/2026-06-10-run.fit" to bytes("/fixtures/python-run.fit"),
            // things that must be ignored
            "Metrics/46DA0003.FIT" to byteArrayOf(1, 2, 3),
            "Activity/readme.txt" to "hello".toByteArray(),
        ) + extra,
    )

    private val service get() = WatchSyncService(amsterdam)

    @Test
    fun `first sync brings in activities, days and nights`() {
        val repo = InMemoryRepository()
        val report = service.sync(watch(), repo)

        assertEquals(4, report.filesRead)
        assertEquals(0, report.filesSkipped)
        assertEquals(1, report.activitiesAdded)
        assertEquals(0, report.activitiesKnown)
        assertEquals(2, report.daysUpdated) // the 10th and the 11th
        assertEquals(1, report.nightsUpdated)
        assertTrue(report.errors.isEmpty(), report.errors.toString())

        val day = repo.listDaily(d10, d10).single()
        assertEquals(10_200, day.steps)
        assertEquals(90, day.bodyBatteryHigh)
        assertEquals(52, repo.listDaily(d11, d11).single().hrvMs)

        val sleep = repo.listSleep(d11, d11).single()
        assertEquals(82, sleep.score)
        assertEquals(430 * 60, sleep.durationS)

        val activity = repo.listActivities().single()
        assertEquals("watch", activity.source)
        assertEquals("Forerunner 265", activity.deviceName)
    }

    @Test
    fun `second sync skips files it already read and changes nothing`() {
        val repo = InMemoryRepository()
        service.sync(watch(), repo)
        val before = repo.listDaily(d10, d11)

        val report = service.sync(watch(), repo)
        assertEquals(0, report.filesRead)
        assertEquals(4, report.filesSkipped)
        assertEquals(0, report.activitiesAdded)
        assertEquals(before, repo.listDaily(d10, d11))
        assertEquals(1, repo.listActivities().size)
    }

    @Test
    fun `a file whose size changed is read again`() {
        val repo = InMemoryRepository()
        service.sync(watch(), repo)
        // the current Monitor file is still being written: same name, different content and size
        val report = service.sync(watch(extra = mapOf("Monitor/46DB3F05.FIT" to bytes("/watch/sleep.fit"))), repo)
        assertEquals(1, report.filesRead)
        assertEquals(3, report.filesSkipped)
        assertTrue(report.errors.isEmpty())
    }

    @Test
    fun `an activity that is already stored is recognised, not duplicated`() {
        val repo = InMemoryRepository()
        service.sync(watch(), repo)
        // the same run arrives a second time under another file name, as happens after Garmin Connect renamed it
        val report = service.sync(watch(extra = mapOf("Activity/other-name.fit" to bytes("/fixtures/python-run.fit"))), repo)
        assertEquals(0, report.activitiesAdded)
        assertEquals(1, report.activitiesKnown)
        assertEquals(1, repo.listActivities().size)
    }

    @Test
    fun `a run the Garmin account already brought in is not added a second time from the watch`() {
        val repo = InMemoryRepository()
        // read the run once to learn its start time, then store it as the account route would have: another id and source
        val scratch = InMemoryRepository()
        service.sync(watch(), scratch)
        val run = scratch.listActivities().single()
        val fromAccount = run.copy(id = "garmin-12345", source = "garmin", externalId = "12345", startTime = run.startTime.plusSeconds(40))
        repo.upsertActivity(fromAccount)

        val report = service.sync(watch(), repo)
        assertEquals(0, report.activitiesAdded)
        assertEquals(1, report.activitiesKnown)
        assertEquals(listOf("garmin-12345"), repo.listActivities().map { it.id })
    }

    @Test
    fun `a broken file is reported and the others still sync`() {
        val repo = InMemoryRepository()
        val broken = bytes("/watch/monitoring.fit").also { it[40] = (it[40].toInt() xor 0x55).toByte() }
        val report = service.sync(watch(extra = mapOf("Monitor/BROKEN.FIT" to broken)), repo)

        assertEquals(1, report.errors.size)
        assertTrue("Monitor/BROKEN.FIT" in report.errors.single())
        assertEquals(4, report.filesRead)
        assertEquals(10_200, repo.listDaily(d10, d10).single().steps)
    }

    @Test
    fun `a file that cannot be read is reported and the others still sync`() {
        val repo = InMemoryRepository()
        val inner = watch()
        val flaky = object : WatchSource by inner {
            override fun read(file: WatchFileInfo): ByteArray =
                if (file.name == "46DC0001.FIT") throw java.io.IOException("cable pulled") else inner.read(file)
        }
        val report = service.sync(flaky, repo)

        assertEquals(1, report.errors.size)
        assertTrue("Sleep/46DC0001.FIT" in report.errors.single() && "cable pulled" in report.errors.single())
        assertEquals(3, report.filesRead)
        assertEquals(10_200, repo.listDaily(d10, d10).single().steps)
        assertEquals(1, service.sync(inner, repo).filesRead, "only the file that failed is read the next time")
    }

    @Test
    fun `the broken file is tried again next time`() {
        val repo = InMemoryRepository()
        val broken = bytes("/watch/monitoring.fit").also { it[40] = (it[40].toInt() xor 0x55).toByte() }
        val source = watch(extra = mapOf("Monitor/BROKEN.FIT" to broken))
        service.sync(source, repo)
        assertEquals(1, service.sync(source, repo).errors.size)
    }

    @Test
    fun `a nap does not replace the night, but a night replaces a nap`() {
        val night = SleepSummary(date = d11, durationS = 10 * 3600, deepS = 3600, lightS = 30_000, remS = 3600, score = 90)
        val nap = SleepSummary(date = d11, durationS = 1800, lightS = 1800)

        val kept = InMemoryRepository().apply { upsertSleep(night) }
        service.sync(watch(), kept)
        assertEquals(night, kept.listSleep(d11, d11).single()) // 10 hours stored, the file says 7.2 hours

        val replaced = InMemoryRepository().apply { upsertSleep(nap) }
        service.sync(watch(), replaced)
        assertEquals(430 * 60, replaced.listSleep(d11, d11).single().durationS)
    }

    @Test
    fun `progress is reported file by file and the sync can be cancelled`() {
        val repo = InMemoryRepository()
        val seen = ArrayList<WatchSyncProgress>()
        service.sync(watch(), repo, onProgress = { seen.add(it) })
        assertEquals(4, seen.last().total)
        assertEquals(seen.map { it.done }, seen.map { it.done }.sorted())

        val repo2 = InMemoryRepository()
        var calls = 0
        val report = service.sync(watch(), repo2, isCancelled = { calls++ >= 1 })
        assertTrue(report.cancelled)
        assertEquals(1, report.filesRead)
    }

    @Test
    fun `deleting all data makes the next sync bring everything back`() {
        val repo = InMemoryRepository()
        service.sync(watch(), repo)
        repo.clearAll()
        val report = service.sync(watch(), repo)
        assertEquals(4, report.filesRead)
        assertEquals(10_200, repo.listDaily(d10, d10).single().steps)
    }

    /** The in-memory watch plus a record of what was marked, with the failure the test asks for. */
    private class Archiving(private val inner: InMemoryWatchSource, private val failWith: Exception? = null) : WatchSource by inner {
        val marked = ArrayList<String>()

        override fun archive(files: List<WatchFileInfo>): Int {
            failWith?.let { throw it }
            files.mapTo(marked) { it.path }
            return files.size
        }
    }

    @Test
    fun `stored files are archived after the sync, files that failed are not`() {
        val source = Archiving(watch(mapOf("Monitor/BAD.FIT" to byteArrayOf(1, 2, 3, 4))))
        val report = service.sync(source, InMemoryRepository())
        assertEquals(1, report.errors.size)
        assertEquals(4, report.archived)
        assertEquals(4, source.marked.size)
        assertTrue("Monitor/BAD.FIT" !in source.marked)
    }

    @Test
    fun `a cancelled sync archives nothing`() {
        val source = Archiving(watch())
        val report = service.sync(source, InMemoryRepository(), isCancelled = { true })
        assertTrue(report.cancelled)
        assertEquals(0, report.archived)
        assertTrue(source.marked.isEmpty())
    }

    @Test
    fun `files skipped because they were read before are archived too, and a failing archive does not fail the sync`() {
        val repo = InMemoryRepository()
        service.sync(watch(), repo)
        val again = Archiving(watch())
        assertEquals(4, service.sync(again, repo).archived)

        val failing = Archiving(watch(), IllegalStateException("link lost"))
        val report = service.sync(failing, InMemoryRepository())
        assertEquals(4, report.filesRead)
        assertEquals(0, report.archived)
        assertTrue(report.errors.single().startsWith("archive:"))
    }

    @Test
    fun `a watch without a garmin folder gives an empty report`() {
        val report = service.sync(InMemoryWatchSource("Empty", emptyMap()), InMemoryRepository())
        assertEquals(0, report.filesFound)
        assertEquals(0, report.filesRead)
    }
}
