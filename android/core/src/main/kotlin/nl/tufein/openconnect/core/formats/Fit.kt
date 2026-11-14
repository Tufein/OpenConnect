package nl.tufein.openconnect.core.formats

import nl.tufein.openconnect.core.Activity
import nl.tufein.openconnect.core.Sport
import nl.tufein.openconnect.core.TrackPoint
import nl.tufein.openconnect.core.FitStep
import nl.tufein.openconnect.core.clampInt
import nl.tufein.openconnect.core.finalizeActivity
import java.io.ByteArrayOutputStream
import java.time.Instant

/**
 * Minimal FIT activity encoder and decoder, no dependencies.
 *
 * Encoding writes the messages Garmin Connect and most platforms need for an activity upload:
 * file_id, record*, lap, session, activity. Decoding reads the same plus real-world device files
 * (big or little endian, compressed timestamps, developer fields, which it skips).
 */
internal object Fit {
    /** Seconds between the Unix epoch and the FIT epoch (1989-12-31T00:00:00Z). */
    private const val FIT_EPOCH_OFFSET = 631_065_600L
    private const val PROTOCOL_VERSION = 0x20
    private const val PROFILE_VERSION = 2132

    private val CRC_TABLE = intArrayOf(
        0x0000, 0xCC01, 0xD801, 0x1400, 0xF001, 0x3C00, 0x2800, 0xE401,
        0xA001, 0x6C00, 0x7800, 0xB401, 0x5000, 0x9C01, 0x8801, 0x4400,
    )

    fun crc16(data: ByteArray, from: Int = 0, to: Int = data.size, start: Int = 0): Int {
        var crc = start
        for (i in from until to) {
            val byte = data[i].toInt() and 0xFF
            var tmp = CRC_TABLE[crc and 0xF]
            crc = (crc shr 4) and 0x0FFF
            crc = crc xor tmp xor CRC_TABLE[byte and 0xF]
            tmp = CRC_TABLE[crc and 0xF]
            crc = (crc shr 4) and 0x0FFF
            crc = crc xor tmp xor CRC_TABLE[(byte shr 4) and 0xF]
        }
        return crc
    }

    // ----------------------------------------------------------------------------- encoding

    private class BaseType(val code: Int, val size: Int, val invalid: Long)

    private val ENUM = BaseType(0x00, 1, 0xFF)
    private val UINT8 = BaseType(0x02, 1, 0xFF)
    private val UINT16 = BaseType(0x84, 2, 0xFFFF)
    private val SINT32 = BaseType(0x85, 4, 0x7FFFFFFF)
    private val UINT32 = BaseType(0x86, 4, 0xFFFFFFFFL)
    private val UINT32Z = BaseType(0x8C, 4, 0)
    private val STRING = BaseType(0x07, 1, 0)

    /** [size] is the type's own size, except for a string, whose length is fixed per field. */
    private class FieldDef(val num: Int, val type: BaseType, val size: Int = type.size)

    private val FILE_ID = listOf(
        FieldDef(0, ENUM), FieldDef(1, UINT16), FieldDef(2, UINT16), FieldDef(3, UINT32Z), FieldDef(4, UINT32),
    )
    private val RECORD = listOf(
        FieldDef(253, UINT32), FieldDef(0, SINT32), FieldDef(1, SINT32), FieldDef(2, UINT16),
        FieldDef(3, UINT8), FieldDef(4, UINT8), FieldDef(5, UINT32), FieldDef(7, UINT16),
    )
    private val LAP = listOf(
        FieldDef(254, UINT16), FieldDef(253, UINT32), FieldDef(2, UINT32), FieldDef(7, UINT32),
        FieldDef(8, UINT32), FieldDef(9, UINT32), FieldDef(0, ENUM), FieldDef(1, ENUM),
    )
    private val SESSION = listOf(
        FieldDef(254, UINT16), FieldDef(253, UINT32), FieldDef(2, UINT32), FieldDef(7, UINT32),
        FieldDef(8, UINT32), FieldDef(9, UINT32), FieldDef(5, ENUM), FieldDef(6, ENUM),
        FieldDef(11, UINT16), FieldDef(16, UINT8), FieldDef(17, UINT8), FieldDef(22, UINT16),
        FieldDef(25, UINT16), FieldDef(26, UINT16), FieldDef(0, ENUM), FieldDef(1, ENUM),
    )
    private val ACTIVITY = listOf(
        FieldDef(253, UINT32), FieldDef(0, UINT32), FieldDef(1, UINT16), FieldDef(2, ENUM),
        FieldDef(3, ENUM), FieldDef(4, ENUM),
    )

    private val SPORT_TO_FIT = mapOf(
        Sport.RUNNING to (1 to 0),
        Sport.CYCLING to (2 to 0),
        Sport.SWIMMING to (5 to 0),
        Sport.WALKING to (11 to 0),
        Sport.HIKING to (17 to 0),
        Sport.STRENGTH to (10 to 20),
        Sport.YOGA to (10 to 43),
        Sport.OTHER to (0 to 0),
    )

    private class Writer {
        val buf = ByteArrayOutputStream()

        private fun le(value: Long, size: Int) {
            for (i in 0 until size) buf.write(((value shr (8 * i)) and 0xFF).toInt())
        }

        fun define(local: Int, global: Int, fields: List<FieldDef>) {
            buf.write(0x40 or local)
            buf.write(0) // reserved
            buf.write(0) // little endian
            le(global.toLong(), 2)
            buf.write(fields.size)
            for (f in fields) {
                buf.write(f.num)
                buf.write(f.size)
                buf.write(f.type.code)
            }
        }

        /** A value is a number (null is the type's "invalid") or, for a string field, its bytes, cut to fit and padded with zeros. */
        fun data(local: Int, fields: List<FieldDef>, values: List<Any?>) {
            check(fields.size == values.size)
            buf.write(local)
            for ((f, v) in fields.zip(values)) {
                if (v is ByteArray) {
                    val n = minOf(v.size, f.size - 1)
                    buf.write(v, 0, n)
                    repeat(f.size - n) { buf.write(0) }
                } else {
                    le((v as Number?)?.toLong() ?: f.type.invalid, f.type.size)
                }
            }
        }
    }

    private fun fitTs(instant: Instant): Long = instant.epochSecond - FIT_EPOCH_OFFSET

    private fun semicircles(degrees: Double): Long = Math.rint(degrees * (1L shl 31).toDouble() / 180.0).toLong()

    private fun clamp(value: Number?, lo: Int, hi: Int): Long? = value?.let { clampInt(it.toDouble(), lo, hi).toLong() }

    fun export(activity: Activity): ByteArray {
        val w = Writer()
        val startTs = fitTs(activity.startTime)
        val endTs = if (activity.track.isNotEmpty()) fitTs(activity.track.last().time) else startTs + Math.rint(activity.durationS).toLong()
        val elapsedMs = Math.rint(activity.durationS * 1000).toLong()
        val distanceCm = Math.rint(activity.distanceM * 100).toLong()
        val (sport, subSport) = SPORT_TO_FIT.getValue(activity.sport)

        w.define(0, 0, FILE_ID)
        w.data(0, FILE_ID, listOf(4, 255, 0, 1, startTs)) // type=activity, manufacturer=development

        w.define(1, 20, RECORD)
        for (p in activity.track) {
            w.data(
                1,
                RECORD,
                listOf(
                    fitTs(p.time),
                    p.lat?.let { semicircles(it) },
                    p.lon?.let { semicircles(it) },
                    p.ele?.let { clamp((it + 500) * 5, 0, 0xFFFE) },
                    clamp(p.hr, 0, 254),
                    clamp(p.cadence, 0, 254),
                    p.distanceM?.let { Math.rint(it * 100).toLong() },
                    clamp(p.power, 0, 0xFFFE),
                ),
            )
        }

        w.define(2, 19, LAP)
        w.data(2, LAP, listOf(0, endTs, startTs, elapsedMs, elapsedMs, distanceCm, 9, 1))

        w.define(3, 18, SESSION)
        w.data(
            3,
            SESSION,
            listOf(
                0, endTs, startTs, elapsedMs, elapsedMs, distanceCm,
                sport.toLong(), subSport.toLong(),
                clamp(activity.calories, 0, 0xFFFE),
                clamp(activity.avgHr, 0, 254),
                clamp(activity.maxHr, 0, 254),
                clamp(activity.elevationGainM, 0, 0xFFFE),
                0, 1, 8, 1,
            ),
        )

        w.define(4, 34, ACTIVITY)
        w.data(4, ACTIVITY, listOf(endTs, elapsedMs, 1, 0, 26, 1))

        return finish(w)
    }

    private val WORKOUT = listOf(FieldDef(4, ENUM), FieldDef(6, UINT16), FieldDef(8, STRING, 32))
    private val WORKOUT_STEP = listOf(
        FieldDef(254, UINT16), FieldDef(0, STRING, 16), FieldDef(1, ENUM), FieldDef(2, UINT32),
        FieldDef(3, ENUM), FieldDef(4, UINT32), FieldDef(5, UINT32), FieldDef(6, UINT32), FieldDef(7, ENUM),
    )

    private fun text(s: String): ByteArray = s.toByteArray(Charsets.UTF_8)

    /** A structured running workout: file_id (type workout), one workout message and one workout_step per step. */
    fun exportWorkout(name: String, steps: List<FitStep>, created: Instant): ByteArray {
        val w = Writer()
        w.define(0, 0, FILE_ID)
        w.data(0, FILE_ID, listOf(5, 255, 0, 1, fitTs(created))) // type=workout, manufacturer=development
        w.define(1, 26, WORKOUT)
        w.data(1, WORKOUT, listOf(1, steps.size, text(name))) // sport=running
        w.define(2, 27, WORKOUT_STEP)
        steps.forEachIndexed { i, s ->
            w.data(
                2,
                WORKOUT_STEP,
                listOf(i, text(s.name), s.durationType, s.durationValue, s.targetType, s.targetValue, s.low, s.high, s.intensity),
            )
        }
        return finish(w)
    }

    private fun finish(w: Writer): ByteArray {
        val body = w.buf.toByteArray()
        val header = ByteArray(14)
        header[0] = 14
        header[1] = PROTOCOL_VERSION.toByte()
        putLe(header, 2, PROFILE_VERSION.toLong(), 2)
        putLe(header, 4, body.size.toLong(), 4)
        ".FIT".toByteArray(Charsets.US_ASCII).copyInto(header, 8)
        putLe(header, 12, crc16(header, 0, 12).toLong(), 2)
        val all = header + body
        val out = ByteArray(all.size + 2)
        all.copyInto(out)
        putLe(out, all.size, crc16(all).toLong(), 2)
        return out
    }

    private fun putLe(target: ByteArray, at: Int, value: Long, size: Int) {
        for (i in 0 until size) target[at + i] = ((value shr (8 * i)) and 0xFF).toByte()
    }

    // ----------------------------------------------------------------------------- decoding

    private const val RECORD_MSG = 20
    private const val SESSION_MSG = 18
    private val RECORD_FIELDS = setOf(253, 0, 1, 2, 78, 3, 4, 5, 7)
    private val SESSION_FIELDS = setOf(2, 7, 8, 9, 5, 6, 11, 16, 17, 22)

    fun parse(data: ByteArray): Activity {
        val track = ArrayList<TrackPoint>()
        var session: Map<Int, Long>? = null

        FitReader.read(
            data,
            wanted = {
                when (it) {
                    RECORD_MSG -> RECORD_FIELDS
                    SESSION_MSG -> SESSION_FIELDS
                    else -> null
                }
            },
        ) { global, v ->
            when (global) {
                RECORD_MSG -> {
                    val ts = v[253]
                    if (ts != null) {
                        val altRaw = v[78] ?: v[2]
                        track.add(
                            TrackPoint(
                                time = Instant.ofEpochSecond(ts + FIT_EPOCH_OFFSET),
                                lat = v[0]?.let { it * 180.0 / (1L shl 31) },
                                lon = v[1]?.let { it * 180.0 / (1L shl 31) },
                                ele = altRaw?.let { it / 5.0 - 500.0 },
                                hr = v[3]?.toInt(),
                                cadence = v[4]?.toInt(),
                                power = v[7]?.toInt(),
                                distanceM = v[5]?.let { it / 100.0 },
                            ),
                        )
                    }
                }
                SESSION_MSG -> if (session == null) {
                    session = SESSION_FIELDS.mapNotNull { num -> v[num]?.let { num to it } }.toMap()
                }
            }
        }

        val s = session
        if (s == null && track.isEmpty()) throw FormatError("FIT file contains no activity data")

        val start = s?.get(2)?.let { Instant.ofEpochSecond(it + FIT_EPOCH_OFFSET) } ?: track.first().time
        var activity = Activity(id = "pending", source = "import", startTime = start, track = track)
        if (s != null) {
            val timer = s[8]?.takeIf { it != 0L } ?: s[7] ?: 0L
            activity = activity.copy(
                sport = sportFromFit(s[5]?.toInt(), s[6]?.toInt()),
                durationS = timer / 1000.0,
                distanceM = (s[9] ?: 0L) / 100.0,
                elevationGainM = (s[22] ?: 0L).toDouble(),
                avgHr = s[16]?.toInt(),
                maxHr = s[17]?.toInt(),
                calories = s[11]?.toInt(),
            )
        }
        activity = finalizeActivity(activity)
        activity = activity.copy(name = defaultName(activity))
        return activity.copy(id = importId(activity))
    }

    private fun sportFromFit(sport: Int?, subSport: Int?): Sport = when (sport) {
        1 -> Sport.RUNNING
        2 -> Sport.CYCLING
        5 -> Sport.SWIMMING
        11 -> Sport.WALKING
        17 -> Sport.HIKING
        10 -> when (subSport) {
            20 -> Sport.STRENGTH
            43 -> Sport.YOGA
            else -> Sport.OTHER
        }
        else -> Sport.OTHER
    }
}
