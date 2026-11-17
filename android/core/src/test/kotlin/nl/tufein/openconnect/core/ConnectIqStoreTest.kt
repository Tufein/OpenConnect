package nl.tufein.openconnect.core

import nl.tufein.openconnect.core.watch.ConnectIqStore
import nl.tufein.openconnect.core.watch.StoreStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConnectIqStoreTest {
    private val id = "4debed0e-4266-495e-8215-a3a8755ee87a"
    private val address = "https://apps.garmin.com/apps/$id"

    @Test
    fun aStoreIdBecomesTheStoreAddressWithoutALanguagePart() {
        assertEquals(address, ConnectIqStore.url(id))
        assertEquals(address, ConnectIqStore.url("  ${id.uppercase()}\n"))
    }

    @Test
    fun aPastedStoreAddressWorksAndLosesItsLanguageAndDashboardParts() {
        assertEquals(address, ConnectIqStore.url("https://apps.garmin.com/he-IL/apps/$id"))
        assertEquals(address, ConnectIqStore.url("https://apps.garmin.com/en-US/apps/$id?tab=reviews"))
        assertEquals(address, ConnectIqStore.url("https://apps-developer.garmin.com/en-US/developer/apps/$id"))
    }

    @Test
    fun anEmptyOrWrongValueGivesNoLink() {
        for (bad in listOf(
            "",
            "   ",
            "TODO",
            "4debed0e-4266-495e-8215-a3a8755ee87",
            "4debed0e42664958215a3a8755ee87a0",
            "javascript:alert(1)",
            "http://apps.garmin.com/apps/$id",
            "https://evil.example/apps/$id",
            "https://apps.garmin.com.evil.example/apps/$id",
            "https://apps.garmin.com/apps/$id/../x",
        )) assertNull(ConnectIqStore.url(bad), bad)
    }

    @Test
    fun withTheConnectIqAppOnThePhoneTheButtonStartsThatAppAndNotABrowser() {
        // v0.43.0: no listing of our own, so the app itself opens; the web store is only the way out when that fails.
        assertEquals(
            listOf(StoreStep.LaunchApp(ConnectIqStore.PACKAGE), StoreStep.OpenAddress(ConnectIqStore.HOME)),
            ConnectIqStore.steps(listing = "", installed = true),
        )
    }

    @Test
    fun withoutTheConnectIqAppThePlayStorePageOfThatAppOpens() {
        val steps = ConnectIqStore.steps(listing = "", installed = false)
        assertEquals(
            listOf(StoreStep.OpenAddress("market://details?id=com.garmin.connectiq"), StoreStep.OpenAddress("https://play.google.com/store/apps/details?id=com.garmin.connectiq")),
            steps,
        )
    }

    @Test
    fun aListingOfOurOwnOpensInTheConnectIqAppFirstAndOtherwiseInTheBrowser() {
        assertEquals(
            listOf(StoreStep.OpenAddress(address, ConnectIqStore.PACKAGE), StoreStep.OpenAddress(address)),
            ConnectIqStore.steps(listing = id, installed = true),
        )
        // A pasted address works like the bare id, and without Garmin's app the page opens in the browser.
        assertEquals(listOf(StoreStep.OpenAddress(address)), ConnectIqStore.steps(listing = "https://apps.garmin.com/en-US/apps/$id", installed = false))
    }

    @Test
    fun somethingThatIsNotAStoreIdNeverBecomesAnAddressToOpen() {
        assertEquals(ConnectIqStore.steps(listing = "", installed = true), ConnectIqStore.steps(listing = "https://evil.example/apps/$id", installed = true))
    }

    @Test
    fun everyAddressTheButtonCanOpenIsGarminsOrThePlayStore() {
        for (installed in listOf(true, false)) for (listing in listOf("", id)) {
            for (step in ConnectIqStore.steps(listing, installed)) {
                if (step is StoreStep.OpenAddress) {
                    assertTrue(
                        step.url.startsWith("https://apps.garmin.com/") || step.url.startsWith("market://details?id=com.garmin.") ||
                            step.url.startsWith("https://play.google.com/store/apps/details?id=com.garmin."),
                        step.url,
                    )
                }
            }
        }
    }

    @Test
    fun theBuiltInListingIsEitherEmptyOrAValidStoreId() {
        // Fails the build if someone pastes something that is not a store id into the constant.
        val value = ConnectIqStore.LISTING_ID
        assertTrue(value.isEmpty() || ConnectIqStore.url(value) != null, "not a store id: $value")
        assertEquals(value.isNotEmpty(), ConnectIqStore.hasListing)
    }
}
