package nl.tufein.openconnect

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import nl.tufein.openconnect.core.Backup
import nl.tufein.openconnect.core.DemoData
import nl.tufein.openconnect.core.ExportAll
import nl.tufein.openconnect.core.Repository
import nl.tufein.openconnect.core.UserData
import nl.tufein.openconnect.core.hume.HumeDemo
import nl.tufein.openconnect.core.oura.OuraDemo
import nl.tufein.openconnect.core.watch.Metric
import nl.tufein.openconnect.core.watch.MetricSample
import nl.tufein.openconnect.core.whoop.WhoopDemo
import nl.tufein.openconnect.data.SqliteRepository
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.util.zip.ZipInputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * v0.56.0 (milestone M3): a backup and the ZIP export are tried on the real SQLite database (not the in-memory one the core
 * tests use), with data from every source, and a guard makes a new table impossible to forget: it has to be in the backup
 * or be listed here as left out on purpose, with the reason.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupOnRealDatabaseTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val today = LocalDate.parse("2026-11-18")
    private val from = LocalDate.of(2000, 1, 1)
    private val to = LocalDate.of(2100, 1, 1)
    private val opened = ArrayList<SqliteRepository>()

    private fun database(name: String): SqliteRepository {
        context.deleteDatabase(name)
        return SqliteRepository(context, name = name).also { opened += it }
    }

    @After
    fun close() {
        opened.forEach { it.close() }
        context.deleteDatabase("backup-source.db")
        context.deleteDatabase("backup-target.db")
    }

    private fun filled(): SqliteRepository {
        val repo = database("backup-source.db")
        repo.inTransaction { DemoData.store(repo, DemoData.generate(today, days = 20)) }
        OuraDemo.days(today, 10).forEach(repo::upsertOuraDay)
        WhoopDemo.days(today, 10).forEach(repo::upsertWhoopDay)
        HumeDemo.days(today, 10).forEach(repo::upsertHumeDay)
        repo.upsertSamples(listOf(MetricSample(Metric.HEART_RATE, Instant.parse("2026-11-18T08:00:00Z"), today, 61.0)))
        return repo
    }

    private fun snapshot(repo: Repository) = listOf(
        repo.listActivities(limit = 10_000).map { repo.getActivity(it.id) },
        repo.listDaily(from, to),
        repo.listSleep(from, to),
        repo.listSamples(from, to),
        repo.listOuraDays(from, to),
        repo.listWhoopDays(from, to),
        repo.listHumeDays(from, to),
    )

    @Test
    fun `every source comes back from a backup of the real database`() {
        val source = filled()
        val bytes = Backup.write(source, Instant.parse("2026-11-18T12:00:00Z"), UserData(maxHr = 190))
        val target = database("backup-target.db")
        val report = Backup.restore(bytes, target)
        assertTrue(report.activities > 0 && report.days > 0 && report.nights > 0, "the demo made something to restore")
        assertEquals(snapshot(source), snapshot(target))
        assertEquals(10, target.listOuraDays(from, to).size)
        assertEquals(10, target.listWhoopDays(from, to).size)
        assertEquals(10, target.listHumeDays(from, to).size)
        assertEquals(190, report.userData.maxHr)
    }

    @Test
    fun `restoring over data that is already there changes nothing and loses nothing`() {
        val repo = filled()
        val before = snapshot(repo)
        Backup.restore(Backup.write(repo), repo)
        assertEquals(before, snapshot(repo))
    }

    @Test
    fun `the zip export of the real database holds every activity as a FIT file and restores`() {
        val source = filled()
        val out = ByteArrayOutputStream()
        ExportAll.write(source, UserData(), Instant.parse("2026-11-18T12:00:00Z"), out)
        val names = ArrayList<String>()
        var backup: ByteArray? = null
        ZipInputStream(out.toByteArray().inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                names += entry.name
                if (entry.name == "openconnect-backup.ocbak") backup = zip.readBytes()
            }
        }
        assertEquals(source.listActivities(limit = 10_000).size, names.count { it.endsWith(".fit") })
        val target = database("backup-target.db")
        Backup.restore(backup!!, target)
        assertEquals(snapshot(source), snapshot(target))
    }

    /**
     * A table that holds something a person would miss must be in the backup. These two are left out on purpose: reports
     * can be written again, and the record of which watch files were read only avoids reading them twice.
     */
    private val leftOut = mapOf(
        "reports" to "AI reports can be written again from the data",
        "watch_files" to "only remembers which watch files were already read; the next sync reads them again",
    )
    private val inBackup = mapOf(
        "activities" to "activities", "daily" to "daily", "sleep" to "sleep", "samples" to "samples",
        "oura_days" to "oura", "whoop_days" to "whoop", "hume_days" to "hume",
    )

    @Test
    fun `every table is in the backup or left out on purpose`() {
        val repo = database("backup-source.db")
        val tables = repo.readableDatabase.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%'", null).use { c ->
            val out = ArrayList<String>()
            while (c.moveToNext()) out += c.getString(0)
            out
        }
        val unknown = tables - inBackup.keys - leftOut.keys
        assertEquals(emptyList(), unknown, "a new table needs a decision: add it to Backup (and here), or list it as left out with the reason")
        val written = java.util.zip.GZIPInputStream(Backup.write(repo).inputStream()).readBytes().toString(Charsets.UTF_8)
        for ((table, key) in inBackup) assertTrue("\"$key\":" in written, "the backup has a part for $table (\"$key\")")
    }
}
