package nl.tufein.openconnect.core.watch

import nl.tufein.openconnect.core.Activity
import nl.tufein.openconnect.core.Sport
import nl.tufein.openconnect.core.TrackPoint
import nl.tufein.openconnect.core.finalizeActivity
import java.time.Instant
import kotlin.math.sqrt

enum class SensorContact { UNSUPPORTED, NOT_DETECTED, DETECTED }

/**
 * One notification of the Bluetooth Heart Rate Measurement characteristic (0x2A37) as a Garmin watch in
 * "broadcast heart rate" mode, a chest strap or any other standard sensor sends it.
 */
data class HeartRateMeasurement(
    val bpm: Int,
    val contact: SensorContact,
    /** Time between heartbeats in milliseconds, oldest first. */
    val rrIntervalsMs: List<Double>,
    /** Energy expended since the sensor was reset, in kilojoules, when it reports it. */
    val energyKj: Int?,
) {
    companion object {
        private const val UINT16_FLAG = 0x01
        private const val CONTACT_SHIFT = 1
        private const val ENERGY_FLAG = 0x08
        private const val RR_FLAG = 0x10

        /** Intervals are sent in units of 1/1024 s. */
        private const val RR_UNITS_PER_SECOND = 1024.0

        /** Null when the packet is too short for the fields its flags announce. */
        fun parse(data: ByteArray): HeartRateMeasurement? {
            if (data.size < 2) return null
            val flags = data[0].toInt() and 0xFF
            var at = 1

            fun u8(): Int = data[at++].toInt() and 0xFF

            fun u16(): Int {
                val value = (data[at].toInt() and 0xFF) or ((data[at + 1].toInt() and 0xFF) shl 8)
                at += 2
                return value
            }

            val bpm = if (flags and UINT16_FLAG != 0) {
                if (data.size < at + 2) return null
                u16()
            } else {
                u8()
            }
            val energy = if (flags and ENERGY_FLAG != 0) {
                if (data.size < at + 2) return null
                u16()
            } else {
                null
            }
            val rr = ArrayList<Double>()
            if (flags and RR_FLAG != 0) {
                while (data.size >= at + 2) rr.add(u16() * 1000.0 / RR_UNITS_PER_SECOND)
            }
            val contact = when ((flags shr CONTACT_SHIFT) and 0x03) {
                2 -> SensorContact.NOT_DETECTED
                3 -> SensorContact.DETECTED
                else -> SensorContact.UNSUPPORTED
            }
            return HeartRateMeasurement(bpm, contact, rr, energy)
        }
    }
}

/** A live recording from a heart-rate sensor: what is shown while it runs, and what gets saved afterwards. */
class LiveHeartRate {
    private class Reading(val time: Instant, val bpm: Int)

    private val readings = ArrayList<Reading>()
    private val rr = ArrayList<Double>()

    val count: Int get() = readings.size
    val current: Int? get() = readings.lastOrNull()?.bpm
    val min: Int? get() = readings.minOfOrNull { it.bpm }
    val max: Int? get() = readings.maxOfOrNull { it.bpm }
    val avg: Int?
        get() = if (readings.isEmpty()) null else Math.rint(readings.sumOf { it.bpm }.toDouble() / readings.size).toInt()

    /** A strap that has lost contact reports 0; that is "no reading", not a heart rate. */
    fun add(time: Instant, measurement: HeartRateMeasurement) {
        if (measurement.bpm in 1..250) readings.add(Reading(time, measurement.bpm))
        rr.addAll(measurement.rrIntervalsMs)
    }

    /**
     * Short-term heart-rate variability (RMSSD) in milliseconds from the beat-to-beat intervals. Intervals
     * outside 300 to 2000 ms are artefacts (a missed or doubled beat) and break the chain instead of
     * counting as a difference.
     */
    fun rmssdMs(): Double? {
        val squares = ArrayList<Double>()
        for (i in 1 until rr.size) {
            val a = rr[i - 1]
            val b = rr[i]
            if (a in RR_RANGE && b in RR_RANGE) squares.add((b - a) * (b - a))
        }
        return if (squares.isEmpty()) null else sqrt(squares.average())
    }

    /** The recording as an activity with only heart rate in its track; null when nothing was recorded. */
    fun toActivity(name: String, sport: Sport): Activity? {
        if (readings.isEmpty()) return null
        val start = readings.first().time
        val activity = Activity(
            id = "ble:${start.epochSecond}",
            source = "ble",
            name = name,
            sport = sport,
            startTime = start,
            track = readings.map { TrackPoint(time = it.time, hr = it.bpm) },
        )
        return finalizeActivity(activity)
    }

    private companion object {
        val RR_RANGE = 300.0..2000.0
    }
}
