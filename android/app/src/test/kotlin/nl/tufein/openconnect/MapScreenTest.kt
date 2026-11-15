package nl.tufein.openconnect

import android.content.Context
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import nl.tufein.openconnect.core.Activity
import nl.tufein.openconnect.core.Sport
import nl.tufein.openconnect.core.TrackPoint
import nl.tufein.openconnect.data.SettingsStore
import nl.tufein.openconnect.data.SqliteRepository
import nl.tufein.openconnect.watch.FakeBle
import nl.tufein.openconnect.watch.FakeUsb
import nl.tufein.openconnect.watch.OpenedWatch
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.temporal.ChronoUnit

/** v0.38.0 on the JVM: the heatmap of all routes and the "same route" card on an activity. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi", application = OpenConnectApp::class)
class MapScreenTest : AppTestBase() {
    private val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)

    /** A run along a line going east for 5 km from ([lat], 5.0), [daysAgo] days ago, taking [minutes]. */
    private fun run(id: String, name: String, daysAgo: Long, minutes: Int, lat: Double = 52.0): Activity {
        val start = now.minus(daysAgo, ChronoUnit.DAYS)
        val track = (0..50).map { TrackPoint(time = start.plusSeconds(it.toLong()), lat = lat, lon = 5.0 + 5000.0 / 68_000.0 * it / 50) }
        return Activity(id = id, name = name, sport = Sport.RUNNING, startTime = start, durationS = minutes * 60.0, distanceM = 5000.0, track = track)
    }

    private fun launchWithRuns() = launchFresh { app ->
        val container = AppContainer(
            SqliteRepository(app),
            SettingsStore(app, secure = { app.getSharedPreferences("secure-test", Context.MODE_PRIVATE) }),
            FakeUsb { OpenedWatch(nl.tufein.openconnect.core.watch.InMemoryWatchSource("W", emptyMap())) { } },
            FakeBle(),
        )
        app.containerOverride = container
        container.repository.upsertActivity(run("a", "Park loop A", 9, 31))
        container.repository.upsertActivity(run("b", "Park loop B", 5, 26))
        container.repository.upsertActivity(run("c", "Park loop C", 1, 28))
        container.repository.upsertActivity(run("d", "Harbour run", 3, 30, lat = 53.0))
    }

    @Test
    fun anActivityShowsHowItRanksAmongTheRunsOnTheSameRoute() {
        launchWithRuns()
        tab("Activities").performClick()
        waitForText("Park loop C")
        clickText("Park loop C")
        waitForText("Same route")
        waitForText("3 times on this route", substring = true)
        waitForText("Number 2 of 3 by time")
        scrollToText("faster than your average on this route", substring = true)
        shot("75-same-route")
    }

    @Test
    fun aRunOnItsOwnRouteHasNoSameRouteCard() {
        launchWithRuns()
        tab("Activities").performClick()
        waitForText("Harbour run")
        clickText("Harbour run")
        waitForText("Route")
        org.junit.Assert.assertTrue(!hasText("Same route", substring = false))
    }

    @Test
    fun theMapTabDrawsAllRoutes() {
        launchWithRuns()
        tab("Insights").performClick()
        waitForText("Week of", substring = true)
        rule.onNodeWithText("Map").performClick()
        waitForText("Where you go")
        waitForText("4 routes with a position")
        scrollToText("no map provider", substring = true)
        shot("76-map")
    }
}
