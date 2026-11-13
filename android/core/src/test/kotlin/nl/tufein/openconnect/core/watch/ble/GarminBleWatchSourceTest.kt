// OpenConnect, SPDX-License-Identifier: AGPL-3.0-or-later
package nl.tufein.openconnect.core.watch.ble

import nl.tufein.openconnect.core.InMemoryRepository
import nl.tufein.openconnect.core.watch.WatchFolder
import nl.tufein.openconnect.core.watch.WatchSyncService
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GarminBleWatchSourceTest {
    private val amsterdam = ZoneId.of("Europe/Amsterdam")
    private val scheduler = ExecutorScheduler()
    private val watches = ArrayList<FakeGarminWatch>()

    @AfterTest
    fun stop() {
        watches.forEach { it.shutdown() }
        scheduler.shutdown()
    }

    private fun bytes(path: String): ByteArray =
        checkNotNull(GarminBleWatchSourceTest::class.java.getResourceAsStream(path)) { "missing $path" }.readBytes()

    private val watchFiles = mapOf(
        10 to (GarminFileType.MONITOR to bytes("/watch/monitoring.fit")),
        11 to (GarminFileType.SLEEP to bytes("/watch/sleep.fit")),
        12 to (GarminFileType.HSA to bytes("/watch/hsa.fit")),
        13 to (GarminFileType.ACTIVITY to bytes("/fixtures/python-run.fit")),
        14 to (GarminFileType.METRICS to byteArrayOf(1, 2, 3)), // not something we read
    )

    /** A fresh connection to the same watch, as each sync makes. */
    private fun connect(downloads: MutableList<Int>, archive: Boolean = false): GarminBleWatchSource {
        val watch = FakeGarminWatch(watchFiles, chunkSize = 400)
        watches += watch
        val session = GarminBleSession(watch.pipe, PhoneIdentity("p", "m", "d"), scheduler)
        watch.phone = session
        session.start()
        val info = session.awaitReady(5_000)
        val access = session.asFileAccess()
        return GarminBleWatchSource(
            object : GarminFileAccess {
                override fun listDirectory() = access.listDirectory()
                override fun download(index: Int): ByteArray = access.download(index).also { downloads += index }
                override fun archive(index: Int) = access.archive(index)
            },
            info.deviceName,
            archiveAfterRead = archive,
        )
    }

    @Test
    fun `with archiving on, the files that were stored are marked as synced on the watch`() {
        val report = WatchSyncService(amsterdam).sync(connect(ArrayList(), archive = true), InMemoryRepository())
        assertEquals(4, report.archived)
        assertEquals(setOf(10, 11, 12, 13), watches.last().archived.toSet())
    }

    @Test
    fun `with archiving off, nothing is marked on the watch`() {
        val report = WatchSyncService(amsterdam).sync(connect(ArrayList()), InMemoryRepository())
        assertEquals(0, report.archived)
        assertTrue(watches.last().archived.isEmpty())
    }

    @Test
    fun `files get stable names in the folders the sync service knows`() {
        val entry = GarminDirectoryEntry(1234, 128, 32, 1, 0, 0, 10, Instant.parse("2026-11-13T06:00:00Z"))
        assertEquals("MONITOR_2026-11-13_06-00-00_1234.fit", GarminBleWatchSource.fileName(entry))
        assertEquals(WatchFolder.MONITOR, GarminBleWatchSource.folderOf(entry.fileType))
        assertEquals("FILE_128_99_7.fit", GarminBleWatchSource.fileName(entry.copy(index = 7, subType = 99, date = null)))
    }

    @Test
    fun `a bluetooth sync fills the days, the night and the activity, and the next one downloads nothing old`() {
        val repo = InMemoryRepository()
        val first = ArrayList<Int>()
        val report = WatchSyncService(amsterdam).sync(connect(first), repo)

        assertTrue(report.errors.isEmpty(), report.errors.toString())
        assertEquals(4, report.filesFound) // the metrics file is not one we read
        assertEquals(4, report.filesRead)
        assertEquals(1, report.activitiesAdded)
        assertEquals(1, report.nightsUpdated)
        assertEquals(setOf(10, 11, 12, 13), first.toSet())
        val day = repo.listDaily(LocalDate.parse("2026-06-10"), LocalDate.parse("2026-06-10")).single()
        assertEquals(10_200, day.steps)
        assertEquals("fenix 8", repo.listActivities().single().deviceName)

        val second = ArrayList<Int>()
        val again = WatchSyncService(amsterdam).sync(connect(second), repo)
        assertEquals(4, again.filesSkipped)
        assertTrue(second.isEmpty(), "nothing is downloaded twice: $second")
    }
}
