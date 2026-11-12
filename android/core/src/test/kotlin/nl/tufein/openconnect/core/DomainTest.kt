package nl.tufein.openconnect.core

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DomainTest {
    @Test
    fun haversineKnownDistance() {
        // Amsterdam Centraal to Utrecht Centraal is roughly 34.7 km as the crow flies.
        val d = haversineM(52.3791, 4.9003, 52.0894, 5.1101)
        assertTrue(d in 33_000.0..36_000.0, "was $d")
    }

    @Test
    fun elevationGainIgnoresNoiseBelowThreshold() {
        assertEquals(0.0, elevationGain(listOf(10.0, 10.4, 9.8, 10.3, 9.9, 10.2)))
        // the 0.5 m dip is below the threshold, so 10 -> 12 -> 14 counts as 4 m
        assertEquals(4.0, elevationGain(listOf(10.0, 12.0, 11.5, 14.0)))
        assertEquals(0.0, elevationGain(emptyList()))
    }

    @Test
    fun paceAndSpeed() {
        val act = TestData.activity(distanceM = 5000.0, durationS = 1500.0)
        assertEquals(5000.0 / 1500.0, act.avgSpeedMps)
        assertEquals(300, Math.round(act.paceSPerKm!!).toInt())
        assertNull(TestData.activity(distanceM = 0.0, durationS = 100.0).paceSPerKm)
    }

    @Test
    fun finalizeDerivesMissingMetricsFromTrack() {
        val start = Instant.parse("2026-05-02T07:00:00Z")
        val track = (0 until 6).map { i ->
            TrackPoint(
                time = start.plusSeconds(i * 10L),
                lat = 52.0 + i * 0.001,
                lon = 5.0,
                ele = 10.0 + i * 3,
                hr = 130 + i,
            )
        }
        val act = finalizeActivity(Activity(id = "x", startTime = start, track = track))
        assertEquals(50.0, act.durationS)
        assertEquals(15.0, act.elevationGainM)
        assertTrue(act.distanceM in 540.0..570.0, "was ${act.distanceM}")
        assertEquals(132, act.avgHr) // mean 132.5 rounds half to even (132), like the server
        assertEquals(135, act.maxHr)
    }

    @Test
    fun finalizeKeepsValuesThatAreAlreadySet() {
        val act = TestData.activity()
        assertEquals(act, finalizeActivity(act))
    }

    @Test
    fun finalizeUsesCumulativeDistanceWhenPresent() {
        val track = listOf(
            TrackPoint(time = TestData.START, distanceM = 0.0),
            TrackPoint(time = TestData.START.plusSeconds(60), distanceM = 250.0),
        )
        val act = finalizeActivity(Activity(id = "x", startTime = TestData.START, track = track))
        assertEquals(250.0, act.distanceM)
        assertNotNull(act.durationS)
    }

    @Test
    fun sportFromKeyFallsBackToOther() {
        assertEquals(Sport.CYCLING, Sport.fromKey("cycling"))
        assertEquals(Sport.OTHER, Sport.fromKey("curling"))
        assertEquals(Sport.OTHER, Sport.fromKey(null))
    }

    @Test
    fun intensityMinutesCountVigorousDouble() {
        assertEquals(30, TestData.daily(java.time.LocalDate.parse("2026-05-02")).intensityMinutes)
    }
}
