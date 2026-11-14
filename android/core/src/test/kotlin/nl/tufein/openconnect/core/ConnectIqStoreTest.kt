package nl.tufein.openconnect.core

import nl.tufein.openconnect.core.watch.ConnectIqStore
import nl.tufein.openconnect.core.watch.StoreApp
import nl.tufein.openconnect.core.watch.StoreLink
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
    fun onlyAppsWithAValidIdGetALink() {
        assertEquals(emptyList(), ConnectIqStore.links("", ""))
        assertEquals(listOf(StoreLink(StoreApp.WATCHFACE, address)), ConnectIqStore.links(id, ""))
        val other = "1679c143-4f3d-44cb-b933-bb8f38636025"
        assertEquals(
            listOf(StoreLink(StoreApp.WATCHFACE, address), StoreLink(StoreApp.WIDGET, "https://apps.garmin.com/apps/$other")),
            ConnectIqStore.links(id, other),
        )
    }

    @Test
    fun theBuiltInConstantsAreEitherEmptyOrAValidStoreId() {
        // Fails the build if someone pastes something that is not a store id into the constants.
        for (value in listOf(ConnectIqStore.WATCHFACE_ID, ConnectIqStore.WIDGET_ID)) {
            assertTrue(value.isEmpty() || ConnectIqStore.url(value) != null, "not a store id: $value")
        }
    }
}
