package nl.tufein.openconnect.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ManualActivityTest {
    private val zone = ZoneId.of("Europe/Amsterdam")
    private val day = LocalDate.of(2026, 11, 20)

    private fun walk(minutes: Int = 45, km: Double = 4.2, hr: Int? = 118) =
        ManualActivity(sport = Sport.WALKING, date = day, minutes = minutes, distanceKm = km, avgHr = hr, name = "  Rondje park  ")

    @Test
    fun aTypedWorkoutBecomesAnActivityWithItsOwnSource() {
        val activity = walk().toActivity(zone, "manual-1")

        assertEquals("manual-1", activity.id)
        assertEquals(ManualActivity.SOURCE, activity.source)
        assertEquals(Sport.WALKING, activity.sport)
        assertEquals("Rondje park", activity.name, "the name is trimmed")
        assertEquals(45 * 60.0, activity.durationS)
        assertEquals(4200.0, activity.distanceM)
        assertEquals(118, activity.avgHr)
        assertTrue(activity.track.isEmpty(), "nothing it did not record is invented")
    }

    @Test
    fun theDateIsKeptInEveryTimeZoneEvenWhenTheClocksChange() {
        // 2026-10-25 is the night the Netherlands moves to winter time.
        val switch = LocalDate.of(2026, 10, 25)
        for (zone in listOf(ZoneId.of("Europe/Amsterdam"), ZoneId.of("Pacific/Auckland"), ZoneId.of("UTC"))) {
            val activity = walk().copy(date = switch).toActivity(zone, "manual-2")
            assertEquals(switch, activity.startTime.atZone(zone).toLocalDate(), "the day stays put in $zone")
        }
    }

    @Test
    fun numbersThatCannotBeTrueAreRefused() {
        assertEquals(ManualActivity.Problem.MINUTES, walk(minutes = 0).problem)
        assertEquals(ManualActivity.Problem.MINUTES, walk(minutes = 1441).problem)
        assertEquals(ManualActivity.Problem.DISTANCE, walk(km = -1.0).problem)
        assertEquals(ManualActivity.Problem.DISTANCE, walk(km = 1001.0).problem)
        assertEquals(ManualActivity.Problem.HEART_RATE, walk(hr = 29).problem)
        assertEquals(ManualActivity.Problem.HEART_RATE, walk(hr = 251).problem)
    }

    @Test
    fun distanceAndHeartRateMayBeLeftOut() {
        assertNull(walk(km = 0.0, hr = null).problem)
        assertNull(walk(minutes = 1440).problem, "a whole day is allowed")
        assertNull(walk(hr = 30).problem)
        assertNull(walk(hr = 250).problem)
    }

    @Test
    fun twoWorkoutsOnTheSameDayNeverShareAnId() {
        val first = ManualActivity.id(Instant.ofEpochMilli(1_700_000_000_000))
        val second = ManualActivity.id(Instant.ofEpochMilli(1_700_000_001_000))
        assertTrue(first.startsWith("manual-"))
        assertTrue(first != second)
    }
}
