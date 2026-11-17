package nl.tufein.openconnect

import android.content.Context
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.hasScrollAction
import nl.tufein.openconnect.core.Activity
import nl.tufein.openconnect.core.DailySummary
import nl.tufein.openconnect.core.SleepSummary
import nl.tufein.openconnect.core.Sport
import nl.tufein.openconnect.core.oura.OuraDay
import nl.tufein.openconnect.core.oura.OuraWorkout
import nl.tufein.openconnect.core.watch.Metric
import nl.tufein.openconnect.core.watch.MetricSample
import nl.tufein.openconnect.data.SettingsStore
import nl.tufein.openconnect.data.SqliteRepository
import nl.tufein.openconnect.watch.FakeBle
import nl.tufein.openconnect.watch.FakeUsb
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * v0.43.0 on the JVM: an activity is filled in with what the watch and the Oura Ring measured around it, and a workout
 * that only the ring recorded is listed among the activities without being stored as one. Nothing here is live data: it
 * is what was stored at the last sync, and the screen says so.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi", application = OpenConnectApp::class)
class ActivityAroundScreenTest : AppTestBase() {
    private val zone = ZoneId.systemDefault()
    private val day: LocalDate = LocalDate.now(zone).minusDays(2)
    private fun at(hour: Int, minute: Int = 0): Instant = day.atTime(hour, minute).atZone(zone).toInstant()

    private fun reading(metric: Metric, time: Instant, value: Double) = MetricSample(metric, time, time.atZone(zone).toLocalDate(), value)

    /** An evening run from 18:00 to 19:00; the watch recorded no heart rate for it, so the day readings may fill that in. */
    private val run = Activity(id = "run1", source = "garmin-ble", name = "Evening run", sport = Sport.RUNNING, startTime = at(18), durationS = 3600.0, distanceM = 9_800.0)

    private fun launch(ring: Boolean, seed: (SqliteRepository) -> Unit) = launchFresh(oura = ring) { app ->
        val container = AppContainer(
            SqliteRepository(app),
            SettingsStore(app, secure = { app.getSharedPreferences("secure-test", Context.MODE_PRIVATE) }),
            FakeUsb { error("no watch in this test") },
            FakeBle(),
            ouraHttp = FakeOura(),
        )
        app.containerOverride = container
        seed(container.repository)
    }

    private fun seedWatch(repo: SqliteRepository) {
        repo.upsertActivity(run)
        repo.upsertDaily(DailySummary(day, steps = 12_400, restingHr = 52, hrvMs = 58, avgStress = 31, bodyBatteryHigh = 85, bodyBatteryLow = 23))
        repo.upsertSleep(SleepSummary(day, durationS = 7 * 3600 + 12 * 60, score = 81))
        repo.upsertSamples(
            listOf(
                reading(Metric.BODY_BATTERY, at(17, 55), 62.0),
                reading(Metric.BODY_BATTERY, at(19, 2), 48.0),
                reading(Metric.STRESS, at(17, 20), 20.0),
                reading(Metric.STRESS, at(17, 40), 30.0),
                reading(Metric.STRESS, at(19, 20), 40.0),
                reading(Metric.STRESS, at(19, 40), 50.0),
                reading(Metric.HEART_RATE, at(18, 10), 140.0),
                reading(Metric.HEART_RATE, at(18, 40), 160.0),
            ),
        )
    }

    private fun seedRing(repo: SqliteRepository) {
        val sameRun = OuraWorkout("w-run", "running", null, at(18, 5).toEpochMilli(), at(18, 55).toEpochMilli(), calories = 610, distanceM = 9_700.0)
        val lunchWalk = OuraWorkout("w-walk", "walking", "Lunch walk", at(12).toEpochMilli(), at(12, 30).toEpochMilli(), calories = 120)
        repo.upsertOuraDay(OuraDay(day.toString(), readiness = 78, sleepScore = 80, sleepS = 25_200, lowestHr = 48, averageHrv = 58, temperatureDeviation = 0.1, workouts = listOf(sameRun, lunchWalk)))
    }

    private fun openRun() {
        tab("Activities").performClick()
        waitForText("Evening run")
        clickText("Evening run")
        // The card sits below the activity's own cards, so wait for the top of the screen and scroll down to it.
        waitForText("Distance")
        scrollToText("Around this activity")
    }

    @Test
    fun anActivityShowsWhatTheWatchMeasuredJustBeforeAndAfterAndThatDay() {
        launch(ring = false, seed = ::seedWatch)
        openRun()
        scrollToText("Body Battery, start to end")
        assertTrue(hasText("62 → 48", substring = false))
        assertTrue(hasText("Stress, the hour before", substring = false))
        assertTrue(hasText("Stress, the hour after", substring = false))
        // The activity has no heart rate of its own, so the day readings give the average (150) and the highest (160).
        assertTrue(hasText("average 150 · highest 160 bpm", substring = false))
        scrollToText("Resting heart rate that day")
        assertTrue(hasText("52 bpm", substring = false))
        assertTrue(hasText("58 ms", substring = false))
        assertTrue(hasText("7:12 · score 81", substring = false))
        scrollToText("None of this is live", substring = true)
        assertFalse(hasText("The watch has no measurements", substring = true), "there are measurements, so nothing is missing")
        assertFalse(hasText("Oura", substring = true), "no ring was chosen, so it is neither shown nor missed")
        shot("78-activity-around-watch")
    }

    @Test
    fun withTheRingTheMorningScoresAndItsOwnRecordOfTheEffortJoin() {
        launch(ring = true) { repo -> seedWatch(repo); seedRing(repo) }
        openRun()
        scrollToText("Readiness that morning")
        assertTrue(hasText("78", substring = false))
        scrollToText("The ring recorded it too")
        assertTrue(hasText("Running  ·  50:00", substring = true), "the ring's own length, 50 minutes")
        scrollToText("Skin temperature against normal")
        // Said plainly: the ring's heart rate during the effort is not read.
        scrollToText("heart rate during the effort is not shown", substring = true)
        scrollToText("None of this is live", substring = true)
        shot("79-activity-around-ring")
    }

    @Test
    fun aWorkoutOnlyTheRingRecordedIsListedButNeverStoredAsAnActivity() {
        var repository: SqliteRepository? = null
        launch(ring = true) { repo -> repository = repo; seedWatch(repo); seedRing(repo) }
        tab("Activities").performClick()
        waitForText("Lunch walk")
        // The run is covered by the ring's record of it, so only the walk is an extra row.
        assertTrue(hasText("2 activities", substring = false))
        assertTrue(hasText("Oura Ring", substring = true))
        clickText("Lunch walk")
        waitForText("Recorded by the Oura Ring only", substring = true)
        scrollToText("Around this activity")
        scrollToText("None of this is live", substring = true)
        rule.onAllNodes(hasScrollAction())[0].performTouchInput { swipeUp(); swipeUp() }
        rule.waitForIdle()
        assertFalse(hasText("Delete activity", substring = false), "a ring workout cannot be deleted: it is not stored")
        assertFalse(hasText("FIT", substring = false), "and it cannot be exported")
        shot("80-ring-workout-row")
        // Only what a watch or a file recorded is in the activities table, so totals and trophies do not count the ring's.
        assertEquals(listOf("run1"), repository!!.listActivities(limit = 100).map { it.id })
    }

    @Test
    fun withoutTheRingThereIsNoRingRowAndNoRingText() {
        launch(ring = false) { repo -> seedWatch(repo); seedRing(repo) }
        tab("Activities").performClick()
        waitForText("Evening run")
        assertFalse(hasText("Lunch walk", substring = false))
        assertTrue(hasText("1 activities", substring = false))
        assertFalse(hasText("Oura Ring", substring = true))
    }

    @Test
    fun whenOnlyTotalsOfTheDayExistTheCardSaysWhatIsMissing() {
        // A file or the Garmin account gives the day's totals but not the minute-by-minute readings.
        launch(ring = false) { repo ->
            repo.upsertActivity(run)
            repo.upsertDaily(DailySummary(day, steps = 9_000, restingHr = 52))
        }
        openRun()
        scrollToText("Resting heart rate that day")
        scrollToText("No Body Battery or stress from just before and after", substring = true)
        assertFalse(hasText("Body Battery, start to end", substring = false))
    }

    @Test
    fun aDayWithNothingFromTheWatchSaysSoInsteadOfLeavingAHole() {
        launch(ring = false) { repo -> repo.upsertActivity(run) }
        openRun()
        // No daily summary, no night and no readings: the watch is the only chosen device and knows nothing of this day.
        scrollToText("The watch has no measurements for this day.")
        scrollToText("None of this is live", substring = true)
        assertFalse(hasText("Body Battery, start to end", substring = false))
    }

    @Test
    @Config(qualifiers = "nl-w411dp-h891dp-night-xxhdpi")
    fun theCardIsInDutchToo() {
        launch(ring = true) { repo -> seedWatch(repo); seedRing(repo) }
        tab("Activiteiten").performClick()
        waitForText("Evening run")
        clickText("Evening run")
        waitForText("Afstand")
        scrollToText("Rond deze activiteit")
        scrollToText("Body Battery, begin tot eind")
        assertTrue(hasText("62 → 48", substring = false))
        scrollToText("Paraatheid die ochtend")
        scrollToText("De ring zag het ook")
        scrollToText("Niets hiervan is live", substring = true)
    }
}
