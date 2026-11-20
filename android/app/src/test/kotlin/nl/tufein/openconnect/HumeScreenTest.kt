package nl.tufein.openconnect

import android.content.Context
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import nl.tufein.openconnect.core.health.HealthAccess
import nl.tufein.openconnect.core.hume.HumeExercise
import nl.tufein.openconnect.core.hume.HumeGateway
import nl.tufein.openconnect.core.hume.HumeReadings
import nl.tufein.openconnect.core.hume.HumeSample
import nl.tufein.openconnect.core.hume.HumeSleep
import nl.tufein.openconnect.core.hume.HumeStage
import nl.tufein.openconnect.core.hume.HumeStageKind
import nl.tufein.openconnect.data.SettingsStore
import nl.tufein.openconnect.data.SqliteRepository
import nl.tufein.openconnect.watch.FakeBle
import nl.tufein.openconnect.watch.FakeUsb
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A Health Connect that answers from memory: what each app wrote, and what the person allowed. */
class FakeHumeHealth(
    var access: HealthAccess = HealthAccess.READY,
    val byOrigin: MutableMap<String, HumeReadings> = HashMap(),
) : HumeGateway {
    /** The first and the last day of every read, so a test can see how far back a sync looked. */
    val reads = ArrayList<Pair<LocalDate, LocalDate>>()
    var failWith: Exception? = null

    /** Makes the next [access] fail the way a stopped background job does: with the coroutine's cancellation. */
    var cancelOnAccess = false

    override suspend fun access(): HealthAccess {
        if (cancelOnAccess) throw kotlinx.coroutines.CancellationException("the job was stopped")
        return access
    }

    override suspend fun permissionsToAsk(): Set<String> = if (access == HealthAccess.UNAVAILABLE) emptySet() else setOf("android.permission.health.READ_STEPS")

    override suspend fun origins(from: LocalDate, to: LocalDate): Set<String> {
        failWith?.let { throw it }
        return byOrigin.keys.toSet()
    }

    override suspend fun read(origins: Set<String>, from: LocalDate, to: LocalDate): HumeReadings {
        failWith?.let { throw it }
        reads += from to to
        val all = origins.mapNotNull(byOrigin::get)
        return HumeReadings(
            steps = all.flatMap { it.steps.entries }.associate { it.key to it.value }.filterKeys { it in from..to },
            activeKcal = all.flatMap { it.activeKcal.entries }.associate { it.key to it.value }.filterKeys { it in from..to },
            heartRate = all.flatMap { it.heartRate },
            restingHr = all.flatMap { it.restingHr.entries }.associate { it.key to it.value },
            hrv = all.flatMap { it.hrv },
            spo2 = all.flatMap { it.spo2 },
            sleeps = all.flatMap { it.sleeps },
            exercises = all.flatMap { it.exercises },
        )
    }
}

/**
 * v0.50.0 on the JVM: the Hume Band is read from a fake Health Connect, only from the app that wrote the band's data. The tab
 * shows the night, steps and heart rate apart from the watch and the other bands; a workout only the band recorded is listed among
 * the activities without being stored as one. Health Connect itself and the real Hume app are not part of this.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi", application = OpenConnectApp::class)
class HumeScreenTest : AppTestBase() {
    private val hume = "com.elink.fittrackhealth.pro"
    private val zone = ZoneId.systemDefault()
    private val yesterday: LocalDate = LocalDate.now(zone).minusDays(1)
    private val health = FakeHumeHealth()

    private fun at(day: LocalDate, hour: Int, minute: Int = 0): Instant = day.atTime(hour, minute).atZone(zone).toInstant()

    /** Yesterday's morning: a night from 23:00 to 07:00 (7 h 30 asleep of 8 h in bed), steps, a lunch walk and a few readings. */
    private fun bandReadings() = HumeReadings(
        steps = mapOf(yesterday to 9_500),
        activeKcal = mapOf(yesterday to 610),
        heartRate = listOf(HumeSample(at(yesterday, 3), 47.0), HumeSample(at(yesterday, 13), 95.0), HumeSample(at(yesterday, 18), 121.0)),
        restingHr = mapOf(yesterday to 52),
        hrv = listOf(HumeSample(at(yesterday, 4), 64.0)),
        spo2 = listOf(HumeSample(at(yesterday, 4), 96.0)),
        sleeps = listOf(
            HumeSleep(
                at(yesterday.minusDays(1), 23), at(yesterday, 7),
                listOf(
                    HumeStage(HumeStageKind.AWAKE, at(yesterday.minusDays(1), 23), at(yesterday.minusDays(1), 23, 30)),
                    HumeStage(HumeStageKind.LIGHT, at(yesterday.minusDays(1), 23, 30), at(yesterday, 2)),
                    HumeStage(HumeStageKind.DEEP, at(yesterday, 2), at(yesterday, 3, 30)),
                    HumeStage(HumeStageKind.REM, at(yesterday, 3, 30), at(yesterday, 5)),
                    HumeStage(HumeStageKind.LIGHT, at(yesterday, 5), at(yesterday, 7)),
                ),
            ),
        ),
        exercises = listOf(HumeExercise("e-1", "pilates", "Lunch pilates", at(yesterday, 12, 15), at(yesterday, 12, 55), calories = 150)),
    )

    private fun launchWithHume(band: Boolean = true, seed: (SqliteRepository) -> Unit = {}) = launchFresh(hume = band) { app ->
        val container = AppContainer(
            SqliteRepository(app),
            SettingsStore(app, secure = { app.getSharedPreferences("secure-test", Context.MODE_PRIVATE) }),
            FakeUsb { throw IllegalStateException() },
            FakeBle(),
            humeGateway = health,
        )
        app.containerOverride = container
        seed(container.repository)
    }

    private fun container() = ApplicationProvider.getApplicationContext<OpenConnectApp>().container

    @Test
    fun theBandIsReadFromHealthConnectAndTheTabShowsItsNightStepsAndHeartRate() {
        health.byOrigin[hume] = bandReadings()
        launchWithHume()
        waitForText("Load demo data")
        tab("Hume").performClick()
        waitForText("7:30 asleep", substring = true) // the first sync ran when the app opened
        scrollToText("Latest day:", substring = true)
        assertTrue(hasText("9,500", substring = false), "steps in their tile")
        assertTrue(hasText("52", substring = false), "resting heart rate in its tile")
        shot("hume-tab")
        scrollToText("Reading data from: $hume", substring = true)
        scrollToText("Last sync:", substring = true)
        assertEquals(yesterday, container().repository.listHumeDays(yesterday, yesterday).single().date)
    }

    @Test
    fun withoutPermissionTheTabAsksAndNothingIsReadMeanwhile() {
        health.access = HealthAccess.NEEDS_PERMISSION
        health.byOrigin[hume] = bandReadings()
        launchWithHume()
        waitForText("Load demo data")
        tab("Hume").performClick()
        waitForText("Allow reading")
        scrollToText("Allow in Health Connect")
        assertTrue(hasText("Hume has no connection of its own", substring = true))
        shot("hume-allow")
        assertTrue(health.reads.isEmpty())
        assertTrue(container().repository.listHumeDays(yesterday.minusDays(40), yesterday).isEmpty())
    }

    @Test
    fun withoutHealthConnectTheTabSaysSo() {
        health.access = HealthAccess.UNAVAILABLE
        launchWithHume()
        waitForText("Load demo data")
        tab("Hume").performClick()
        waitForText("Health Connect is not on this phone", substring = true)
        assertFalse(hasText("Allow in Health Connect", substring = false))
    }

    @Test
    fun whenOnlyAnotherAppHasDataTheTabOffersItAndChoosingItReadsIt() {
        health.byOrigin["com.example.otherband"] = bandReadings()
        launchWithHume()
        waitForText("Load demo data")
        tab("Hume").performClick()
        waitForText("Health Connect holds nothing from the Hume app", substring = true)
        scrollToText("Which app wrote the band's data?")
        assertTrue(container().repository.listHumeDays(yesterday.minusDays(40), yesterday).isEmpty(), "another app's data is not taken for the band's")
        rule.onNode(androidx.compose.ui.test.hasText("com.example.otherband", substring = true) and androidx.compose.ui.test.hasClickAction())
            .performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        waitForText("7:30 asleep", substring = true)
        assertEquals("com.example.otherband", container().settings.hume.origin)
        scrollToText("Reading data from: com.example.otherband", substring = true)
    }

    @Test
    fun aWorkoutOnlyTheBandRecordedIsListedAmongTheActivitiesButNeverStored() {
        health.byOrigin[hume] = bandReadings()
        var repository: SqliteRepository? = null
        launchWithHume { repo -> repository = repo }
        waitForText("Load demo data")
        tab("Hume").performClick()
        waitForText("7:30 asleep", substring = true)
        tab("Activities").performClick()
        waitForText("Lunch pilates")
        assertTrue(hasText("1 activities", substring = false))
        assertTrue(hasText("Hume Band", substring = true))
        clickText("Lunch pilates")
        waitForText("Recorded by the Hume Band only", substring = true)
        assertTrue(repository!!.listActivities(limit = 100).isEmpty(), "a band workout is made on the fly, never stored as an activity")
    }

    @Test
    fun theDayTabCanShowTheBandsDayAsASourceOfItsOwn() {
        health.byOrigin[hume] = bandReadings()
        launchWithHume()
        waitForText("Load demo data")
        tab("Hume").performClick()
        waitForText("7:30 asleep", substring = true)
        tab("Insights").performClick()
        waitForText("Week of", substring = true)
        tab("Day").performClick()
        // The watch is the first source and the band the second; both are in use, so the dropdown is there.
        waitForText("Source")
        rule.onNode(hasText("Garmin") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("Hume Band") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(hasText("Hume Band") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Tap a measure to see it as a chart.")
        assertTrue(hasText("Steps", substring = false))
        assertTrue(hasText("9,500", substring = false), "the day's steps")
        assertFalse(hasText("Readiness", substring = false), "a band without scores shows none")
        shot("hume-day")
    }

    @Test
    fun withoutTheBandThereIsNoHumeTabAndNothingIsReadFromHealthConnect() {
        health.byOrigin[hume] = bandReadings()
        launchWithHume(band = false)
        waitForText("Load demo data")
        assertFalse(hasText("Hume", substring = true), "no tab, no button")
        assertTrue(health.reads.isEmpty())
        assertTrue(container().repository.listHumeDays(yesterday.minusDays(40), yesterday).isEmpty())
    }

    @Test
    @Config(qualifiers = "nl-w411dp-h891dp-night-xxhdpi")
    fun theTabIsInDutchToo() {
        health.byOrigin[hume] = bandReadings()
        launchWithHume()
        waitForText("Demodata laden")
        tab("Hume").performClick()
        waitForText("geslapen", substring = true)
        scrollToText("Afgelopen nacht")
        scrollToText("Welke app schreef de gegevens van de band?")
    }
}
