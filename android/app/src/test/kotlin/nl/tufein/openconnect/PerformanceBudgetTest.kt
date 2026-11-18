package nl.tufein.openconnect

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import nl.tufein.openconnect.core.DemoData
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import kotlin.test.assertTrue

/**
 * v0.58.0 (milestone M5, fast): five years of history, about 1,300 activities with a track each and 1,800 days of health
 * data, stored in the real SQLite repository, and every tab opened on it. The budget is generous (a tab 8 seconds, the first start 30; measured
 * about 0.2 and 3) because this runs on a shared CI machine under Robolectric, which is slower than a phone; it is there to catch a screen that
 * becomes slow with a lot of history (a loop inside a loop, a track read for every row), not to promise a time on a phone.
 * The measured times are written to `app/build/perf/report.txt` and the last run's numbers are in docs/PERFORMANCE.md.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi", application = OpenConnectApp::class)
class PerformanceBudgetTest : AppTestBase() {
    private val report = StringBuilder()
    private val budgetMs = 8_000L
    private val coldStartBudgetMs = 30_000L

    private fun note(line: String) {
        println("PERF $line")
        report.appendLine(line)
    }

    private fun timed(what: String, block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        val ms = (System.nanoTime() - start) / 1_000_000
        note("$what: $ms ms")
        return ms
    }

    private fun seedFiveYears(app: OpenConnectApp) {
        val data = DemoData.generate(LocalDate.now(), days = 5 * 365)
        note("generated: ${data.activities.size} activities, ${data.activities.sumOf { it.track.size }} track points, ${data.dailies.size} days")
        val repo = app.container.repository
        timed("stored five years") {
            data.activities.forEach(repo::upsertActivity)
            data.dailies.forEach(repo::upsertDaily)
            data.sleeps.forEach(repo::upsertSleep)
        }
    }

    @Test
    fun everyTabOpensWithinTheBudgetOnFiveYearsOfHistory() {
        val slow = ArrayList<String>()
        fun tabOpens(name: String, wait: String, substring: Boolean = false) {
            val ms = timed("tab $name") {
                tab(name).performClick()
                waitForText(wait, substring)
            }
            if (ms > budgetMs) slow += "$name took $ms ms"
        }

        val start = System.nanoTime()
        launchFresh(prepare = ::seedFiveYears)
        waitForText("Readiness", substring = true)
        val home = (System.nanoTime() - start) / 1_000_000
        note("start to Home with data: $home ms (includes storing the data)")
        if (home > coldStartBudgetMs) slow += "the first start took $home ms"

        tabOpens("Activities", "Easy Run")
        tabOpens("Health", "Scores")
        tabOpens("Insights", "Week of", substring = true)
        rule.onNodeWithText("Map").performClick()
        val map = timed("map of the newest 300 routes") { waitForText("routes with a position", substring = true) }
        if (map > budgetMs) slow += "the map took $map ms"
        tabOpens("Settings", "Appearance")

        File("build/perf").also { it.mkdirs() }.resolve("report.txt").writeText(report.toString())
        assertTrue(slow.isEmpty(), "over the budget (a tab $budgetMs ms, the first start $coldStartBudgetMs ms): $slow")
    }
}
