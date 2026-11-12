package nl.tufein.openconnect.core

import nl.tufein.openconnect.core.formats.Fit
import nl.tufein.openconnect.core.formats.FormatError
import nl.tufein.openconnect.core.formats.Formats
import java.time.Instant
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FormatsTest {
    private fun assertTracksClose(a: List<TrackPoint>, b: List<TrackPoint>, eleTol: Double = 0.25) {
        assertEquals(a.size, b.size)
        for ((p, q) in a.zip(b)) {
            assertEquals(p.time, q.time)
            assertTrue(abs(p.lat!! - q.lat!!) < 1e-6, "lat ${p.lat} vs ${q.lat}")
            assertTrue(abs(p.lon!! - q.lon!!) < 1e-6, "lon ${p.lon} vs ${q.lon}")
            assertTrue(abs(p.ele!! - q.ele!!) <= eleTol, "ele ${p.ele} vs ${q.ele}")
            assertEquals(p.hr, q.hr)
        }
    }

    private fun text(bytes: ByteArray) = String(bytes, Charsets.UTF_8)

    // ------------------------------------------------------------------------------- GPX

    @Test
    fun gpxExportIsWellFormedWithTrackPointExtensions() {
        val act = TestData.activity()
        val xml = text(Formats.export(act, "gpx").bytes)
        assertContains(xml, "version=\"1.1\"")
        assertEquals(act.track.size, Regex("<trkpt ").findAll(xml).count())
        assertContains(xml, "<gpxtpx:hr>")
    }

    @Test
    fun gpxRoundTripPreservesTrackAndMetadata() {
        val act = TestData.activity(name = "Park & Ride <fast>", sport = Sport.CYCLING)
        val parsed = Formats.import(Formats.export(act, "gpx").bytes)
        assertEquals("Park & Ride <fast>", parsed.name)
        assertEquals(Sport.CYCLING, parsed.sport)
        assertEquals(act.startTime, parsed.startTime)
        assertTracksClose(act.track, parsed.track, eleTol = 0.01)
        assertEquals(act.durationS, parsed.durationS)
        assertTrue(parsed.distanceM > 0) // derived from the track
        assertEquals("import", parsed.source)
    }

    @Test
    fun gpxMinimalFileWithoutExtensionsOrElevation() {
        val xml = """<?xml version="1.0"?>
            <gpx version="1.1" creator="x" xmlns="http://www.topografix.com/GPX/1/1">
              <trk><name>Walk</name><trkseg>
                <trkpt lat="52.0" lon="5.0"><time>2026-03-01T10:00:00Z</time></trkpt>
                <trkpt lat="52.001" lon="5.0"><time>2026-03-01T10:01:00Z</time></trkpt>
              </trkseg></trk></gpx>""".trimIndent().toByteArray()
        val act = Formats.import(xml, "walk.gpx")
        assertEquals("Walk", act.name)
        assertEquals(60.0, act.durationS)
        assertTrue(act.distanceM in 100.0..120.0, "was ${act.distanceM}")
        assertNull(act.track[0].ele)
        assertNull(act.track[0].hr)
    }

    @Test
    fun gpxHonoursTimezoneOffsets() {
        val xml = """<gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1"><trk><trkseg>
            <trkpt lat="52.0" lon="5.0"><time>2026-03-01T11:00:00+01:00</time></trkpt>
            </trkseg></trk></gpx>""".toByteArray()
        assertEquals(Instant.parse("2026-03-01T10:00:00Z"), Formats.import(xml, "a.gpx").startTime)
    }

    @Test
    fun gpxRejectsGarbageAndEmptyTracks() {
        assertFailsWith<FormatError> { Formats.import("not xml at all".toByteArray(), "x.gpx") }
        assertFailsWith<FormatError> {
            Formats.import("""<gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1"/>""".toByteArray())
        }
    }

    @Test
    fun gpxBlocksEntityExpansionAttacks() {
        val bomb = """<?xml version="1.0"?>
            <!DOCTYPE gpx [<!ENTITY a "aaaaaaaaaa"><!ENTITY b "&a;&a;&a;&a;&a;&a;&a;&a;">]>
            <gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1"><metadata><name>&b;</name></metadata></gpx>"""
        assertFailsWith<FormatError> { Formats.import(bomb.toByteArray(), "x.gpx") }
        // the same document re-encoded as UTF-16 must not slip past the check
        assertFailsWith<FormatError> { Formats.import(bomb.toByteArray(Charsets.UTF_16LE), "x.gpx") }
    }

    @Test
    fun gpxBlocksExternalEntities() {
        val xxe = """<?xml version="1.0"?>
            <!DOCTYPE gpx [<!ENTITY x SYSTEM "file:///etc/passwd">]>
            <gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1"><metadata><name>&x;</name></metadata></gpx>"""
        assertFailsWith<FormatError> { Formats.import(xxe.toByteArray(), "x.gpx") }
    }

    @Test
    fun gpxExportNeedsPositions() {
        val indoor = TestData.activity(track = TestData.track().map { it.copy(lat = null, lon = null) })
        assertFailsWith<FormatError> { Formats.export(indoor, "gpx") }
    }

    // ------------------------------------------------------------------------------- TCX

    @Test
    fun tcxExportHasLapTotalsAndSport() {
        val xml = text(Formats.export(TestData.activity(sport = Sport.RUNNING), "tcx").bytes)
        assertContains(xml, "Sport=\"Running\"")
        assertContains(xml, "<DistanceMeters>5000.0</DistanceMeters>")
        assertContains(xml, "<TotalTimeSeconds>59.0</TotalTimeSeconds>")
        assertEquals(60, Regex("<Trackpoint>").findAll(xml).count())
    }

    @Test
    fun tcxRoundTrip() {
        val act = TestData.activity(sport = Sport.CYCLING)
        val parsed = Formats.import(Formats.export(act, "tcx").bytes)
        assertEquals(Sport.CYCLING, parsed.sport)
        assertEquals(act.startTime, parsed.startTime)
        assertEquals(5000.0, parsed.distanceM)
        assertEquals(150, parsed.avgHr)
        assertEquals(159, parsed.maxHr)
        assertEquals(420, parsed.calories)
        assertTracksClose(act.track, parsed.track, eleTol = 0.01)
    }

    @Test
    fun tcxIndoorActivityWithoutPositions() {
        val act = TestData.activity(
            sport = Sport.OTHER,
            track = TestData.track().map { it.copy(lat = null, lon = null) },
        )
        val parsed = Formats.import(Formats.export(act, "tcx").bytes)
        assertFalse(parsed.hasGps)
        assertEquals(act.track.size, parsed.track.size)
        assertEquals(act.track[3].hr, parsed.track[3].hr)
    }

    @Test
    fun tcxRejectsGarbage() {
        assertFailsWith<FormatError> { Formats.import("<nope/>".toByteArray(), "x.tcx") }
    }

    // -------------------------------------------------------------------------------- FIT

    @Test
    fun fitHeaderAndCrcAreValid() {
        val data = Formats.export(TestData.activity(), "fit").bytes
        assertEquals(14, data[0].toInt())
        assertEquals(".FIT", String(data, 8, 4, Charsets.US_ASCII))
        val dataSize = (0 until 4).sumOf { (data[4 + it].toLong() and 0xFF) shl (8 * it) }.toInt()
        assertEquals(14 + dataSize + 2, data.size)
        val stored = (data[data.size - 2].toInt() and 0xFF) or ((data[data.size - 1].toInt() and 0xFF) shl 8)
        assertEquals(stored, Fit.crc16(data, 0, data.size - 2))
    }

    @Test
    fun fitRoundTrip() {
        val act = TestData.activity(sport = Sport.RUNNING)
        val parsed = Formats.import(Formats.export(act, "fit").bytes)
        assertEquals(Sport.RUNNING, parsed.sport)
        assertEquals(act.startTime, parsed.startTime)
        assertEquals(5000.0, parsed.distanceM)
        assertEquals(59.0, parsed.durationS)
        assertEquals(12.0, parsed.elevationGainM)
        assertEquals(150, parsed.avgHr)
        assertEquals(159, parsed.maxHr)
        assertEquals(420, parsed.calories)
        assertTracksClose(act.track, parsed.track)
        assertEquals(84, parsed.track[5].cadence)
    }

    @Test
    fun fitStrengthAndYogaKeepTheirSport() {
        for (sport in listOf(Sport.STRENGTH, Sport.YOGA, Sport.SWIMMING, Sport.HIKING, Sport.WALKING)) {
            val parsed = Formats.import(Formats.export(TestData.activity(sport = sport), "fit").bytes)
            assertEquals(sport, parsed.sport)
        }
    }

    @Test
    fun fitDecodesAFileWrittenByTheServer() {
        val parsed = Formats.import(TestData.resource("python-run.fit"), "python-run.fit")
        assertEquals(Sport.RUNNING, parsed.sport)
        assertEquals(Instant.parse("2026-05-02T07:00:00Z"), parsed.startTime)
        assertEquals(60, parsed.track.size)
        assertEquals(5000.0, parsed.distanceM)
        assertEquals(150, parsed.avgHr)
        assertEquals(420, parsed.calories)
        assertTracksClose(TestData.track(), parsed.track)
    }

    @Test
    fun fitDetectsCorruption() {
        val data = Formats.export(TestData.activity(), "fit").bytes
        val flipped = data.copyOf().also { it[40] = (it[40].toInt() xor 0x55).toByte() }
        assertFailsWith<FormatError> { Formats.import(flipped, "a.fit") }
        assertFailsWith<FormatError> { Formats.import(data.copyOf(data.size - 10), "a.fit") }
        assertFailsWith<FormatError> { Formats.import(ByteArray(40) { 1 }, "a.fit") }
    }

    @Test
    fun fitWithoutActivityDataIsRejected() {
        // header and CRC only, no messages
        val header = ByteArray(14)
        header[0] = 14
        header[1] = 0x20
        ".FIT".toByteArray().copyInto(header, 8)
        val crcH = Fit.crc16(header, 0, 12)
        header[12] = (crcH and 0xFF).toByte()
        header[13] = (crcH shr 8).toByte()
        val crc = Fit.crc16(header)
        val file = header + byteArrayOf((crc and 0xFF).toByte(), (crc shr 8).toByte())
        assertFailsWith<FormatError> { Formats.import(file, "empty.fit") }
    }

    // ----------------------------------------------------------------------- facade / ids

    @Test
    fun detectsFormatByContentBeforeExtension() {
        val fit = Formats.export(TestData.activity(), "fit").bytes
        assertEquals("fit", Formats.detect(fit, "misnamed.gpx"))
        assertEquals("gpx", Formats.detect(Formats.export(TestData.activity(), "gpx").bytes, "x.txt"))
        assertEquals("tcx", Formats.detect(Formats.export(TestData.activity(), "tcx").bytes, ""))
        assertFailsWith<FormatError> { Formats.detect("hello".toByteArray(), "notes.txt") }
    }

    @Test
    fun exportRejectsUnknownFormatAndNamesTheFile() {
        assertFailsWith<FormatError> { Formats.export(TestData.activity(), "pdf") }
        val exported = Formats.export(TestData.activity(), ".GPX")
        assertEquals("running-20260502-070000.gpx", exported.filename)
        assertEquals("application/gpx+xml", exported.contentType)
    }

    @Test
    fun importIdIsStableSoReimportingUpdatesInsteadOfCloning() {
        val bytes = Formats.export(TestData.activity(), "gpx").bytes
        val first = Formats.import(bytes)
        val second = Formats.import(bytes)
        assertEquals(first.id, second.id)
        assertTrue(first.id.startsWith("import:"))
        val other = Formats.import(Formats.export(TestData.activity(start = TestData.START.plusSeconds(86_400)), "gpx").bytes)
        assertNotEquals(first.id, other.id)
    }

    @Test
    fun importIdMatchesTheServerForTheSameActivity() {
        // Computed by the Python server for the same fixture: sha1("2026-05-02T07:00:00+00:00|59")[:16].
        val parsed = Formats.import(TestData.resource("python-run.gpx"))
        assertEquals("import:f66f7975a1ac0bdc", parsed.id)
        assertContentEquals(parsed.track.map { it.hr }, TestData.track().map { it.hr })
    }

    /** Lets the server's importers check what the app writes: set OPENCONNECT_WRITE_VALIDATION_FILES=dir. */
    @Test
    fun writesExportsForExternalValidationWhenAsked() {
        val dir = System.getenv("OPENCONNECT_WRITE_VALIDATION_FILES") ?: return
        java.io.File(dir).mkdirs()
        for (fmt in Formats.SUPPORTED) {
            java.io.File(dir, "kotlin-run.$fmt").writeBytes(Formats.export(TestData.activity(sport = Sport.RUNNING), fmt).bytes)
        }
    }
}
