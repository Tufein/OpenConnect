// OpenConnect, SPDX-License-Identifier: AGPL-3.0-or-later
package nl.tufein.openconnect.core

import kotlinx.serialization.json.Json
import nl.tufein.openconnect.core.formats.Fit
import nl.tufein.openconnect.core.formats.FormatError
import nl.tufein.openconnect.core.formats.Formats
import nl.tufein.openconnect.core.garmin.DayLeaves
import nl.tufein.openconnect.core.garmin.SleepMapping
import nl.tufein.openconnect.core.watch.WatchFit
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Files and answers from outside the app are never trusted: a watch file, a GPX from a stranger, a Garmin answer in a
 * layout nobody documented. Whatever arrives, the parser must either give a result or say so with the error it
 * declares ([FormatError]); any other exception (an index out of range, a number that cannot be rounded, an overflow)
 * is a crash waiting for the one real file that triggers it. This test feeds every parser the real fixtures damaged in
 * a few thousand ways (cut short, bytes changed, numbers replaced by the awkward ones) from a fixed seed, so a failure
 * can be repeated, and lists every kind of unexpected exception once with where it came from.
 */
class ParserFuzzTest {
    private class Finding(val label: String, val error: Throwable) {
        val key: String get() = "$label | ${error::class.simpleName} | ${error.stackTrace.firstOrNull { it.className.startsWith("nl.tufein") }}"
    }

    private val findings = LinkedHashMap<String, Finding>()
    private var tried = 0

    private fun fixture(path: String): ByteArray =
        checkNotNull(ParserFuzzTest::class.java.getResourceAsStream(path)) { "missing $path" }.readBytes()

    private inline fun probe(label: String, allowed: (Throwable) -> Boolean, block: () -> Unit) {
        tried++
        try {
            block()
        } catch (e: Throwable) {
            if (!allowed(e)) Finding(label, e).let { findings.putIfAbsent(it.key, it) }
        }
    }

    /** A parsed activity must hold only real numbers and places on Earth: NaN breaks every sum, chart and JSON export. */
    private fun sane(label: String, a: Activity) {
        val bad = buildList {
            if (!a.durationS.isFinite()) add("duration ${a.durationS}")
            if (!a.distanceM.isFinite()) add("distance ${a.distanceM}")
            if (!a.elevationGainM.isFinite()) add("elevation gain ${a.elevationGainM}")
            for (p in a.track) {
                listOfNotNull(p.lat, p.lon, p.ele, p.distanceM).firstOrNull { !it.isFinite() }?.let { add("track value $it") }
                if (p.lat != null && p.lat !in -90.0..90.0) add("latitude ${p.lat}")
                if (p.lon != null && p.lon !in -180.0..180.0) add("longitude ${p.lon}")
            }
        }
        if (bad.isNotEmpty()) Finding(label, IllegalStateException("parsed with ${bad.first()}")).let { findings.putIfAbsent("$label | insane | ${bad.first().substringBefore(' ')}", it) }
    }

    private fun assertClean() {
        val report = findings.values.joinToString("\n") { f ->
            val where = f.error.stackTrace.take(4).joinToString(" <- ") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
            "  ${f.label}: ${f.error::class.qualifiedName}: ${f.error.message} at $where"
        }
        assertTrue(findings.isEmpty(), "${findings.size} kinds of unexpected exceptions in $tried tries:\n$report")
    }

    // ---------------------------------------------------------------------------------------------- byte mutations

    private val nasty = byteArrayOf(0x00, 0x01, 0x7F, 0x80.toByte(), 0xFE.toByte(), 0xFF.toByte())

    private fun mutants(seed: ByteArray, rnd: Random, count: Int, from: Int = 0, to: Int = seed.size): Sequence<ByteArray> = sequence {
        for (i in 0 until count) {
            val copy = seed.copyOf()
            val edits = 1 + rnd.nextInt(4)
            repeat(edits) {
                if (to > from) copy[from + rnd.nextInt(to - from)] = if (rnd.nextBoolean()) nasty[rnd.nextInt(nasty.size)] else rnd.nextInt(256).toByte()
            }
            yield(copy)
        }
    }

    /** A FIT file whose body was damaged, with the header size and both checksums made right again so the body is read. */
    private fun refit(header: Int, body: ByteArray): ByteArray {
        val head = ByteArray(header)
        head[0] = header.toByte()
        head[1] = 0x20
        head[2] = 0x00
        head[3] = 0x00
        java.nio.ByteBuffer.wrap(head).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(4, body.size)
        ".FIT".toByteArray(Charsets.ISO_8859_1).copyInto(head, 8)
        val all = head + body
        val crc = Fit.crc16(all, 0, all.size)
        return all + byteArrayOf((crc and 0xFF).toByte(), (crc shr 8).toByte())
    }

    private fun fitBody(file: ByteArray): Pair<Int, ByteArray> {
        val header = file[0].toInt() and 0xFF
        val size = java.nio.ByteBuffer.wrap(file).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt(4)
        return header to file.copyOfRange(header, header + size)
    }

    private fun fitFuzz(label: String, file: ByteArray, rnd: Random, allowed: (Throwable) -> Boolean, parse: (ByteArray) -> Unit) {
        val (header, body) = fitBody(file)
        probe("$label (untouched)", allowed) { parse(file) }
        // cut short at many places
        val cuts = (0..minOf(body.size, 80)) + (0 until 300).map { rnd.nextInt(body.size + 1) }
        for (cut in cuts) probe("$label (cut at body byte)", allowed) { parse(refit(header, body.copyOf(cut))) }
        // bytes changed in the body
        for (m in mutants(body, rnd, 1500)) probe("$label (body bytes changed)", allowed) { parse(refit(header, m)) }
        // bytes changed without repairing the checksums (the usual way a file is damaged in transit)
        for (m in mutants(file, rnd, 300)) probe("$label (raw bytes changed)", allowed) { parse(m) }
        // a file that claims more data than it has
        probe("$label (size says more)", allowed) { parse(file.copyOf().also { it[4] = 0xFF.toByte(); it[5] = 0xFF.toByte(); it[6] = 0xFF.toByte(); it[7] = 0x7F }) }
    }

    @Test
    fun `an activity FIT file damaged in any way is read or refused with a FormatError`() {
        fitFuzz("Fit.parse", fixture("/fixtures/python-run.fit"), Random(1), { it is FormatError }) { sane("Fit.parse", Fit.parse(it)) }
        assertClean()
    }

    @Test
    fun `a watch FIT file damaged in any way is read or refused with a FormatError`() {
        for (name in listOf("monitoring.fit", "sleep.fit", "hsa.fit", "wrap.fit")) {
            fitFuzz("WatchFit.parse $name", fixture("/watch/$name"), Random(name.hashCode().toLong()), { it is FormatError }) { WatchFit.parse(it) }
        }
        assertClean()
    }

    // --------------------------------------------------------------------------------------------- text mutations

    private val awkward = listOf(
        "NaN", "Infinity", "-Infinity", "1e999", "-1", "0", "99999999999999999999", "2147483648", "-2147483649", "",
        "abc", "0x10", "1,5", "٣", "9223372036854775807", "-0.0", "1e-999",
    )

    private val number = Regex("""-?\d+(\.\d+)?([eE][-+]?\d+)?""")

    private fun textMutants(seed: String, rnd: Random, count: Int): Sequence<String> = sequence {
        val numbers = number.findAll(seed).toList()
        for (i in 0 until count) {
            when (rnd.nextInt(4)) {
                0 -> yield(seed.substring(0, rnd.nextInt(seed.length + 1))) // cut short
                1 -> if (numbers.isNotEmpty()) { // one number replaced by an awkward one
                    val hit = numbers[rnd.nextInt(numbers.size)]
                    yield(seed.replaceRange(hit.range, awkward[rnd.nextInt(awkward.size)]))
                }
                2 -> if (numbers.isNotEmpty()) { // several at once
                    var text = seed
                    repeat(1 + rnd.nextInt(5)) {
                        val hits = number.findAll(text).toList()
                        if (hits.isNotEmpty()) text = text.replaceRange(hits[rnd.nextInt(hits.size)].range, awkward[rnd.nextInt(awkward.size)])
                    }
                    yield(text)
                }
                else -> { // a piece taken out or repeated
                    val a = rnd.nextInt(seed.length)
                    val b = minOf(seed.length, a + 1 + rnd.nextInt(200))
                    yield(if (rnd.nextBoolean()) seed.removeRange(a, b) else seed.substring(0, b) + seed.substring(a))
                }
            }
        }
    }

    @Test
    fun `a GPX file damaged in any way is read or refused with a FormatError`() {
        val seed = String(fixture("/fixtures/python-run.gpx"), Charsets.UTF_8)
        val allowed = { e: Throwable -> e is FormatError }
        for (m in textMutants(seed, Random(2), 2500)) probe("Gpx", allowed) { sane("Gpx", Formats.import(m.toByteArray(Charsets.UTF_8), "run.gpx")) }
        assertClean()
    }

    @Test
    fun `a TCX file damaged in any way is read or refused with a FormatError`() {
        val seed = String(fixture("/fixtures/python-run.tcx"), Charsets.UTF_8)
        val allowed = { e: Throwable -> e is FormatError }
        for (m in textMutants(seed, Random(3), 2500)) probe("Tcx", allowed) { sane("Tcx", Formats.import(m.toByteArray(Charsets.UTF_8), "run.tcx")) }
        assertClean()
    }

    // ------------------------------------------------------------------------------------------- Garmin's own JSON

    @Test
    fun `a Garmin answer in any layout gives fewer values, never an exception`() {
        val rnd = Random(4)
        val none = { _: Throwable -> false }
        for (name in listOf("dailysummaries", "sleep", "steps", "stress", "hrv", "bodybattery")) {
            val seed = String(fixture("/garmin/$name.json"), Charsets.UTF_8)
            for (m in textMutants(seed, rnd, 500)) {
                val root = try {
                    Json.parseToJsonElement(m)
                } catch (_: Exception) {
                    continue // not JSON any more: the HTTP layer reports that, not the mapping
                }
                probe("DayLeaves $name", none) {
                    val days = DayLeaves.collect(root)
                    DayLeaves.bodyBattery(root)
                    for ((date, facts) in days) {
                        SleepMapping.night(date, facts)
                        facts.int("steps.value", "totalSteps")
                        facts.reading("restingHeartRate", "heartRate.restingValue")
                    }
                }
            }
        }
        assertClean()
    }
}
