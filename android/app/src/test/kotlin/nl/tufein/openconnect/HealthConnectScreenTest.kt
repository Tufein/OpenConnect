package nl.tufein.openconnect

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import nl.tufein.openconnect.core.health.HealthAccess
import nl.tufein.openconnect.core.health.HealthConnectGateway
import nl.tufein.openconnect.core.health.HealthSession
import nl.tufein.openconnect.data.SettingsStore
import nl.tufein.openconnect.data.SqliteRepository
import nl.tufein.openconnect.watch.FakeBle
import nl.tufein.openconnect.watch.FakeUsb
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi", application = OpenConnectApp::class)
class HealthConnectScreenTest : AppTestBase() {
    private class Fake(val access: HealthAccess) : HealthConnectGateway {
        var sent = 0
        override suspend fun access() = access
        override suspend fun write(sessions: List<HealthSession>): Int = sessions.size.also { sent += it }
        override suspend fun steps(from: LocalDate, to: LocalDate) = emptyMap<LocalDate, Int>()
        override suspend fun sleep(from: LocalDate, to: LocalDate) = emptyMap<LocalDate, Int>()
    }

    private fun launch(access: HealthAccess) = launchFresh { app ->
        app.containerOverride = AppContainer(
            SqliteRepository(app), SettingsStore(app), FakeUsb { error("no usb") }, FakeBle(), healthConnect = Fake(access),
        )
    }

    private fun openSettingsWithDemo() {
        waitForText("Load demo data")
        rule.onNodeWithText("Load demo data").performClick()
        rule.waitUntil(15_000) { !hasText("Load demo data", substring = false) }
        tab("Settings").performClick()
        rule.waitForIdle()
        scrollToText("Health Connect")
    }

    @Test
    fun `a phone without Health Connect says so and offers no buttons`() {
        launch(HealthAccess.UNAVAILABLE)
        openSettingsWithDemo()
        scrollToText("Health Connect is not available on this phone", substring = true)
        assertTrue(!hasText("Send activities now", substring = false))
    }

    @Test
    fun `a phone that has not allowed access shows the allow button`() {
        launch(HealthAccess.NEEDS_PERMISSION)
        openSettingsWithDemo()
        scrollToText("Allow access in Health Connect")
        shot("42-settings-health-connect")
    }

    @Test
    fun `when access is granted the activities can be sent and the result is shown`() {
        launch(HealthAccess.READY)
        openSettingsWithDemo()
        scrollToText("Send activities now")
        assertTrue(hasText("Read steps and sleep", substring = false))
        // The "Demo data loaded" message sits over the lower part of the screen and would take the click.
        rule.waitUntil(15_000) { !hasText("Demo data loaded", substring = true) }
        clickText("Send activities now")
        waitForText("Sent ", substring = true)
    }
}
