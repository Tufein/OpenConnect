package nl.tufein.openconnect

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import nl.tufein.openconnect.core.DemoData
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * v1.13.12 (M15 "Terugblik") on the JVM: this year against last, the grid of every month of every year, each year on its own
 * and the records that were beaten, on three years of demo history and on an empty install.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi", application = OpenConnectApp::class)
class LookBackScreenTest : AppTestBase() {
    private fun seedThreeYears(app: OpenConnectApp) {
        val data = DemoData.generate(LocalDate.now(ZoneOffset.UTC), days = 3 * 365)
        val repo = app.container.repository
        data.activities.forEach(repo::upsertActivity)
        data.dailies.forEach(repo::upsertDaily)
        data.sleeps.forEach(repo::upsertSleep)
    }

    private fun openLookBack() {
        tab("Insights").performClick()
        waitForText("Week of", substring = true)
        clickChip("Look back")
    }

    @Test
    fun lookBackShowsTheComparisonTheGridTheYearsAndTheRecords() {
        launchFresh(prepare = ::seedThreeYears)
        waitForText("Readiness", substring = true)
        openLookBack()

        val year = LocalDate.now(ZoneOffset.UTC).year
        waitForText("$year so far, against ${year - 1}")
        scrollToText("Distance, running total (km)")
        scrollToText("Time, running total (hours)")
        scrollToText("Every month, every year")
        scrollToText("Heaviest month:", substring = true)
        scrollToText("The years")
        scrollToText("Busiest week")
        scrollToText("Weeks without an activity")
        scrollToText("Records broken")
        scrollToText("Counted per calendar day", substring = true)
        shot("80-look-back")
    }

    @Test
    fun aSportChipLimitsTheComparisonAndAllSportsBringsTheRestBack() {
        launchFresh(prepare = ::seedThreeYears)
        waitForText("Readiness", substring = true)
        openLookBack()
        val year = LocalDate.now(ZoneOffset.UTC).year
        waitForText("$year so far, against ${year - 1}")

        // Running is the default sport; the chips for all sports and for the others are there to pick.
        waitForText("All sports")
        fun picked(tag: String) = rule.onNode(hasTestTag(tag)).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected) == true
        rule.waitUntil(15_000) { picked("lookback-sport-running") }
        clickTag("lookback-sport-all")
        rule.waitUntil(15_000) { picked("lookback-sport-all") && !picked("lookback-sport-running") }
        clickTag("lookback-sport-cycling")
        rule.waitUntil(15_000) { picked("lookback-sport-cycling") && !picked("lookback-sport-all") }
        waitForText("$year so far, against ${year - 1}")
        scrollToText("Every month, every year")
    }

    @Test
    fun theGridCanBeSwitchedToTimeAndToActivities() {
        launchFresh(prepare = ::seedThreeYears)
        waitForText("Readiness", substring = true)
        openLookBack()
        waitForText("Every month, every year", substring = false)
        scrollToText("Every month, every year")
        clickTag("lookback-metric-time")
        rule.waitUntil(15_000) { hasText("Heaviest month:", substring = true) }
        clickTag("lookback-metric-count")
        rule.waitUntil(15_000) { hasText("Heaviest month:", substring = true) }
        clickTag("lookback-metric-distance")
        scrollToText("Heaviest month:", substring = true)
    }

    @Test
    fun anEmptyInstallExplainsItselfAndCanLoadThreeYearsOfDemoData() {
        launchFresh()
        openLookBack()
        waitForText("Look back needs activities", substring = true)
        clickText("Load three years of demo data")
        val year = LocalDate.now(ZoneOffset.UTC).year
        waitForText("$year so far, against ${year - 1}")
        scrollToText("Records broken")
    }

    @Test
    fun theTabAndItsTextsAreInDutch() {
        launchFresh(appLanguage = "nl", prepare = ::seedThreeYears)
        waitForText("Gereedheid", substring = true)
        tab("Inzichten").performClick()
        waitForText("Week van", substring = true)
        clickChip("Terugblik")
        val year = LocalDate.now(ZoneOffset.UTC).year
        waitForText("$year tot nu toe, tegenover ${year - 1}")
        scrollToText("Elke maand, elk jaar")
        scrollToText("Gebroken records")
    }
}
