package nl.tufein.openconnect.core.watch.ble

import nl.tufein.openconnect.core.watch.GarminFamily
import nl.tufein.openconnect.core.watch.GarminRecognised
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** What the watch says about itself after the handshake, and which Garmin that is, whichever family it belongs to. */
class GarminDeviceInfoTest {
    private fun info(product: Int, deviceName: String = "", model: String = "", bluetoothName: String = ""): GarminDeviceInfo =
        GarminDeviceInfo.parse(
            GfdiMessage.build(GfdiType.DEVICE_INFORMATION) {
                short(150); short(product); int(123456789); short(2119); short(4096)
                string(bluetoothName); string(deviceName); string(model)
                byte(0); byte(0)
            },
        )

    @Test
    fun theProductNumberNamesTheWatchWhenItGivesNoName() {
        val watch = info(product = 4315)
        assertEquals("Forerunner 965", watch.recognised?.displayName)
        assertEquals(GarminFamily.FORERUNNER, watch.recognised?.family)
        assertEquals(GarminRecognised.PRODUCT_NUMBER, watch.recognised?.recognisedBy)
        assertEquals("Forerunner 965", watch.nameOr("the one we scanned"))
    }

    @Test
    fun theNameTheWatchGivesItselfIsKept() {
        assertEquals("Forerunner 965", info(product = 4315, deviceName = "Forerunner 965").nameOr("x"))
        // a watch that someone renamed keeps its own name on screen, and is still recognised
        val renamed = info(product = 4536, deviceName = "Stefan's fenix")
        assertEquals("Stefan's fenix", renamed.nameOr("x"))
        assertEquals("fenix 8", renamed.recognised?.displayName)
    }

    @Test
    fun theNamesNameTheWatchWhenTheProductNumberIsNotKnown() {
        assertEquals("Venu 3", info(product = 60_000, bluetoothName = "Venu 3").recognised?.displayName)
        assertEquals("Edge 1040", info(product = 0, model = "Edge 1040").recognised?.displayName)
    }

    @Test
    fun anUnknownWatchFallsBackToWhatWasScanned() {
        val unknown = info(product = 60_000)
        assertNull(unknown.recognised)
        assertEquals("what the scan said", unknown.nameOr("what the scan said"))
    }
}
