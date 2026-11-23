// OpenConnect, SPDX-License-Identifier: AGPL-3.0-or-later
package nl.tufein.openconnect

import android.Manifest
import android.content.Context
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import nl.tufein.openconnect.data.SettingsStore
import nl.tufein.openconnect.data.SqliteRepository
import nl.tufein.openconnect.notify.FakeForwardingKeeper
import nl.tufein.openconnect.notify.FakeNotifyConnection
import nl.tufein.openconnect.notify.InstalledApp
import nl.tufein.openconnect.notify.InstalledApps
import nl.tufein.openconnect.watch.FakeBle
import nl.tufein.openconnect.watch.FakeGarminBle
import nl.tufein.openconnect.watch.FakeUsb
import nl.tufein.openconnect.watch.GarminBleWatch
import nl.tufein.openconnect.watch.OpenedWatch
import nl.tufein.openconnect.watch.bluetoothPermissions
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The card for notifications on the watch, and the screen where the apps are chosen, with a pretend watch and a pretend
 * phone (notification access and Bluetooth permission are switches in the test). What this cannot show is Android's own
 * screens, or what a real Garmin does with what it is sent.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi", application = OpenConnectApp::class)
class WatchForwardingScreenTest : AppTestBase() {
    private val fenix = GarminBleWatch("AA:BB:CC:DD:EE:FF", "fenix 8", paired = true)
    private val connections = CopyOnWriteArrayList<FakeNotifyConnection>()
    private val keeper = FakeForwardingKeeper()
    private var access = true
    private var bluetooth = true
    private val apps = InstalledApps { listOf(InstalledApp("com.spel", "Spel"), InstalledApp("com.whatsapp", "WhatsApp"), InstalledApp("com.gmail", "Gmail")) }

    private val garmin = FakeGarminBle(openNotifications = { _, listener -> FakeNotifyConnection(listener = listener).also { connections += it } })

    private fun launch(remembered: GarminBleWatch? = fenix, enabled: Boolean = false, chosen: Set<String> = emptySet()) = launchFresh { app ->
        val settings = SettingsStore(app)
        settings.garminWatch.save(remembered)
        settings.forwarding.enabled = enabled
        settings.forwarding.apps = chosen
        app.containerOverride = AppContainer(
            SqliteRepository(app), settings,
            FakeUsb { OpenedWatch(nl.tufein.openconnect.core.watch.InMemoryWatchSource("x", emptyMap())) { } }, FakeBle(),
            garminBle = garmin,
            forwardKeeper = keeper,
            forwardAccess = { access },
            forwardBluetooth = { bluetooth },
            installedApps = apps,
        )
        val context = ApplicationProvider.getApplicationContext<Context>()
        shadowOf(context as android.app.Application).grantPermissions(*bluetoothPermissions(), Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun openCard() {
        waitForText("Connect watch")
        rule.onNodeWithText("Connect watch").performClick()
        // since v1.8.0 the battery card sits above the notification cards, so this one starts below the fold
        scrollToText("Notifications on the watch")
        waitForText("Notifications on the watch")
        scrollToText("Send notifications to the watch")
    }

    private fun settings() = ApplicationProvider.getApplicationContext<OpenConnectApp>().container.settings

    @Test
    fun theCardIsQuietUntilTheSwitchIsTurnedOn() {
        launch()
        openCard()
        assertTrue(hasText("one phone at a time", substring = true))
        assertFalse(hasText("Choose apps", substring = true), "the choices only show once forwarding is on")
        assertTrue(connections.isEmpty(), "nothing connects before the person asks")
    }

    @Test
    fun withoutNotificationAccessItSaysWhatIsNeededAndWhereToGiveIt() {
        access = false
        launch()
        openCard()
        clickText("Send notifications to the watch")
        waitForText("OpenConnect needs notification access", substring = true)
        assertTrue(settings().forwarding.enabled, "the switch stays on while the person goes to Android's settings")
        assertTrue(hasText("Allow notification access", substring = false))
        scrollToText("Open the app info")
        assertTrue(hasText("Allow restricted settings", substring = true), "a sideloaded app on Android 13 and newer needs this step")
        assertTrue(connections.isEmpty())
        shot("70-forward-needs-access")
    }

    @Test
    fun withoutASyncedWatchItAsksForASyncFirst() {
        launch(remembered = null)
        openCard()
        clickText("Send notifications to the watch")
        waitForText("First sync your watch over Bluetooth", substring = true)
        assertTrue(connections.isEmpty())
    }

    @Test
    fun withEverythingInPlaceItConnectsAndATestReachesTheWatch() {
        launch()
        openCard()
        clickText("Send notifications to the watch")
        waitForText("Connected to fenix 8.")
        assertTrue(settings().forwarding.enabled)
        assertEquals(1, connections.size)
        assertTrue(hasText("Choose apps (0 chosen)", substring = false))
        shot("71-forward-connected")

        clickText("Send a test to the watch")
        waitForText("Test sent. Look at the watch.")
        val test = connections.single().posted.single()
        assertEquals("OpenConnect test", test.title)
        assertEquals("If you can read this on the watch, notifications work.", test.body)
    }

    @Test
    fun aSwitchLeftOnComesBackConnectedWhenTheScreenOpens() {
        launch(enabled = true, chosen = setOf("com.whatsapp"))
        openCard()
        waitForText("Connected to fenix 8.")
        assertTrue(hasText("Choose apps (1 chosen)", substring = false))
    }

    @Test
    fun theChosenAppsAreListedFirstAndTheChoiceIsRemembered() {
        launch(enabled = true, chosen = setOf("com.whatsapp"))
        openCard()
        waitForText("Connected to fenix 8.")
        clickText("Choose apps (1 chosen)")
        waitForText("Only the notifications of the apps you switch on", substring = true)
        waitForText("WhatsApp")
        shot("72-forward-apps")
        clickText("Gmail")
        rule.waitUntil(5_000) { settings().forwarding.apps == setOf("com.whatsapp", "com.gmail") }
        clickText("WhatsApp")
        rule.waitUntil(5_000) { settings().forwarding.apps == setOf("com.gmail") }
    }

    @Test
    fun switchingOffLetsTheConnectionGo() {
        launch(enabled = true)
        openCard()
        waitForText("Connected to fenix 8.")
        clickText("Send notifications to the watch")
        rule.waitUntil(5_000) { connections.single().closed }
        assertFalse(settings().forwarding.enabled)
        assertEquals("stop", keeper.events.last())
    }

    @Test
    fun theDetailsListTheStepsAndTheLogCanBeCopied() {
        launch(enabled = true, chosen = setOf("com.whatsapp"))
        openCard()
        waitForText("Connected to fenix 8.")
        // in the real app Android starts the listener by itself; in this test there is none, and the card says so
        assertTrue(hasText("has not started the OpenConnect listener", substring = true))

        scrollToText("What is happening?")
        clickText("What is happening?")
        waitForText("Notification access given", substring = true)
        assertTrue(hasText("[x] Notification access given", substring = true))
        assertTrue(hasText("[!] Android started the listener", substring = true))
        assertTrue(hasText("[x] Connection to the watch", substring = true))
        shot("73-forward-details")

        scrollToText("Copy the log")
        clickText("Copy the log")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val copied = context.getSystemService(android.content.ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString()
        assertTrue("connected to fenix 8" in copied, copied)
        assertTrue(copied.startsWith("OpenConnect notifications to the watch: log"), copied)
    }

    @Test
    fun aWatchThatHasNotAskedForNotificationsIsSaidSoAndTheTestIsKept() {
        launch(enabled = true, chosen = setOf("com.whatsapp"))
        openCard()
        waitForText("Connected to fenix 8.")
        connections.single().result = nl.tufein.openconnect.core.watch.ble.PostResult.WAITING
        scrollToText("Send a test to the watch")
        clickText("Send a test to the watch")
        waitForText("The watch has not asked for notifications yet", substring = true)
    }
}
