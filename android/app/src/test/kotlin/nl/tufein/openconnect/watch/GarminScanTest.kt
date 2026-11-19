// OpenConnect, SPDX-License-Identifier: AGPL-3.0-or-later
package nl.tufein.openconnect.watch

import android.util.SparseArray
import nl.tufein.openconnect.core.watch.BleSync
import nl.tufein.openconnect.core.watch.GarminDevices
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What the Bluetooth scan shows in its list of Garmin devices. The scan result itself needs a real radio; this is the part
 * after it: a name and the manufacturer data of the advertisement go in, a watch to list (or nothing) comes out.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GarminScanTest {
    private fun garminData(product: Int) = SparseArray<ByteArray>().apply {
        put(GarminDevices.COMPANY_ID, byteArrayOf((product shr 8).toByte(), product.toByte(), 1, 2, 3))
    }

    @Test
    fun theNameTheWatchAdvertisesIsListedAsItIsWithItsSize() {
        val watch = garminCandidate("AA:BB", "fenix 8 - 47mm", null, paired = false)
        assertEquals(GarminBleWatch("AA:BB", "fenix 8 - 47mm", paired = false, bleSync = BleSync.LISTED), watch)
    }

    @Test
    fun everyKindOfGarminWatchIsListed() {
        for (name in listOf("Forerunner 965", "Venu 3S", "vívoactive 5", "Instinct 2X Solar", "Enduro 3", "Lily 2", "epix - 47mm", "Approach S70", "Edge 1040")) {
            assertEquals(name, garminCandidate("AA:BB", name, null, paired = true)?.name, name)
        }
    }

    @Test
    fun aWatchWithoutANameIsListedByItsProductNumber() {
        assertEquals("Forerunner 965", garminCandidate("AA:BB", null, garminData(4315), paired = false)?.name)
        assertEquals("fenix 8", garminCandidate("AA:BB", null, garminData(4536), paired = false)?.name)
    }

    @Test
    fun aWatchWithAnOddNameGetsTheNameOfItsModel() {
        assertEquals("Forerunner 965", garminCandidate("AA:BB", "Stefan's watch", garminData(4315), paired = false)?.name)
    }

    @Test
    fun aGarminDeviceOfAnUnknownModelIsListedAsGarmin() {
        assertEquals("Garmin", garminCandidate("AA:BB", null, garminData(60_000), paired = false)?.name)
    }

    @Test
    fun theAdviceRidesAlongSoTheListCanBeHonest() {
        assertEquals(BleSync.LISTED, garminCandidate("A", "Forerunner 965", null, false)?.bleSync)
        assertEquals(BleSync.NEEDS_NEWER_PROTOCOL, garminCandidate("A", "Venu X1", null, false)?.bleSync)
        assertEquals(BleSync.UNKNOWN, garminCandidate("A", "Edge 1040", null, false)?.bleSync)
        assertEquals(BleSync.UNKNOWN, garminCandidate("A", null, garminData(60_000), false)?.bleSync)
    }

    @Test
    fun strapsLightsScalesAndOtherMakersAreNotListed() {
        for (name in listOf("HRM-Pro", "HRM-Dual", "Varia RTL515", "Index S2", "Edge Remote", "Polar H10", "Bike Lock", "Galaxy Watch6")) {
            assertNull(garminCandidate("AA:BB", name, null, paired = false), name)
        }
        assertNull(garminCandidate("AA:BB", null, null, paired = false))
        // somebody else's manufacturer data is not Garmin's
        val apple = SparseArray<ByteArray>().apply { put(0x004C, byteArrayOf(0x11, 0xB8.toByte(), 1)) }
        assertNull(garminCandidate("AA:BB", null, apple, paired = false))
    }

    @Test
    fun aPairedWatchKeepsItsPairedMark() {
        assertEquals(true, garminCandidate("AA:BB", "Forerunner 965", null, paired = true)?.paired)
        assertEquals(false, garminCandidate("AA:BB", "Forerunner 965", null, paired = false)?.paired)
    }
}
