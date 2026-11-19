package nl.tufein.openconnect.core.watch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Which Garmin device is this? OpenConnect used to know one answer (the fenix 8); these tests pin how it recognises every
 * Garmin family: by the name a device advertises over Bluetooth, by the product number in the advertisement, in the
 * watch's own device information and in the FIT files it writes. The product numbers asserted here are the ones in
 * Garmin's FIT profile (garmin_product); whether a real watch advertises them that way is checked in the Bluetooth
 * docs, not here.
 */
class GarminDevicesTest {
    private fun named(name: String?) = GarminDevices.fromAdvertisement(name, emptyMap())

    private fun withProduct(product: Int, name: String? = null, company: Int = GarminDevices.COMPANY_ID) =
        GarminDevices.fromAdvertisement(
            name,
            mapOf(company to byteArrayOf((product shr 8).toByte(), product.toByte(), 0x01, 0x02)),
        )

    // ------------------------------------------------------------------------- by name

    @Test
    fun theFenix8IsStillRecognisedWithTheSizeLeftOut() {
        val device = assertNotNull(named("fenix 8 - 47mm"))
        assertEquals("fenix 8", device.model?.name)
        assertEquals(GarminFamily.FENIX, device.family)
        assertEquals(GarminKind.WATCH, device.kind)
        assertEquals("fenix 8", device.displayName)
        assertEquals(GarminRecognised.NAME, device.recognisedBy)
    }

    @Test
    fun everyFamilyIsRecognisedFromTheNameItAdvertises() {
        val expected = listOf(
            "fenix 7X Pro" to GarminFamily.FENIX,
            "EPIX PRO - 47mm" to GarminFamily.EPIX,
            "Enduro 3" to GarminFamily.ENDURO,
            "tactix 8 - 47mm" to GarminFamily.TACTIX,
            "quatix 7 - 47mm" to GarminFamily.QUATIX,
            "MARQ Aviator" to GarminFamily.MARQ,
            "Forerunner 965" to GarminFamily.FORERUNNER,
            "Venu 3S" to GarminFamily.VENU,
            "vívoactive 5" to GarminFamily.VIVOACTIVE,
            "vívomove Trend" to GarminFamily.VIVOMOVE,
            "vívosmart 5" to GarminFamily.VIVOSMART,
            "vívofit 4" to GarminFamily.VIVOFIT,
            "vívosport" to GarminFamily.VIVOSPORT,
            "Instinct 2X Solar" to GarminFamily.INSTINCT,
            "Descent Mk3i 51mm" to GarminFamily.DESCENT,
            "Lily 2" to GarminFamily.LILY,
            "Swim 2" to GarminFamily.SWIM,
            "Bounce 2" to GarminFamily.BOUNCE,
            "D2 Mach 1 Pro" to GarminFamily.D2,
            "Approach S70" to GarminFamily.APPROACH,
            "CIRQA Smart Band" to GarminFamily.CIRQA,
            "Edge 1040" to GarminFamily.EDGE,
        )
        for ((name, family) in expected) {
            assertEquals(family, named(name)?.family, "$name should be $family")
        }
    }

    @Test
    fun accentsCaseAndTheGarminPrefixDoNotMatter() {
        val a = named("vívoactive 5")
        assertEquals(a?.model, named("vivoactive 5")?.model)
        assertEquals(a?.model, named("VIVOACTIVE 5")?.model)
        assertEquals(a?.model, named("Garmin vívoactive 5")?.model)
        assertEquals("vívoactive 5", a?.displayName)
        assertEquals("Forerunner 265S", named("Garmin Forerunner 265s")?.displayName)
    }

    @Test
    fun sizesAndMarksAreIgnoredButVariantsStayApart() {
        assertEquals("Venu 4", named("Venu 4 41mm")?.displayName)
        assertEquals("Instinct 3", named("Instinct 3 - 45mm")?.displayName)
        assertEquals("Instinct 3 Solar", named("Instinct 3 Solar - 50mm")?.displayName)
        assertEquals("fenix 8 Pro", named("fenix 8 Pro - 51mm")?.displayName)
        assertEquals("fenix 8 Solar", named("fenix 8 Solar - 47mm")?.displayName)
        assertEquals("Forerunner 965", named("Forerunner® 965")?.displayName)
        assertNotEquals(named("Forerunner 255")?.model, named("Forerunner 255S")?.model)
        assertNotEquals(named("Forerunner 255")?.model, named("Forerunner 255 Music")?.model)
    }

    @Test
    fun theFirstEpixAndTheSecondGenerationAreToldApartByTheCapitals() {
        // Gadgetbridge's own patterns do the same: the first generation advertises "EPIX", the second "epix - 47mm"
        assertEquals("epix (Gen 1)", named("EPIX")?.displayName)
        assertEquals("epix (Gen 2)", named("epix - 47mm")?.displayName)
        assertEquals("epix (Gen 2)", named("epix")?.displayName)
    }

    @Test
    fun aPreReleaseWatchThatAdvertisesItsProductNumberIsStillFound() {
        // some watches advertise "UAT-<product number>"; Gadgetbridge accepts these two, so the pattern is real
        assertEquals("Forerunner 965", named("UAT-4315")?.displayName)
        assertEquals("Venu 3S", named("UAT-4261")?.displayName)
        assertNull(named("UAT-1"))
        assertNull(named("UAT-99999"))
    }

    @Test
    fun aModelThatIsNotInTheTableYetIsStillRecognisedAsItsFamily() {
        val device = assertNotNull(named("Forerunner 999 Future"))
        assertNull(device.model)
        assertEquals(GarminFamily.FORERUNNER, device.family)
        assertEquals(GarminKind.WATCH, device.kind)
        assertEquals("Forerunner 999 Future", device.displayName)
        assertEquals(GarminRecognised.FAMILY_NAME, device.recognisedBy)
        assertTrue(device.offerForSync)
        assertEquals(BleSync.UNKNOWN, device.bleSync)
    }

    @Test
    fun aNumberStuckToTheFamilyNameStillCounts() {
        assertEquals(GarminFamily.VIVOACTIVE, named("vivoactive3")?.family)
        assertEquals("vívoactive 3", named("vívoactive3")?.displayName)
    }

    @Test
    fun namesOfOtherMakersAndLookAlikesAreNotGarmin() {
        for (name in listOf(
            "Fitbit Charge 6", "Polar H10", "Galaxy Watch6 (ABCD)", "Wahoo KICKR", "Mi Smart Band 8", "WHOOP 4.0",
            "Bike Lock", "Index Card", "Swimming Pool", "D215", "Edgewater", "fenixx", "Venus", "Instincts", "Lilypad",
            "Approachable", "", "   ",
        )) {
            assertNull(named(name), "'$name' is not a Garmin device")
        }
        assertNull(named(null))
    }

    // ------------------------------------------------------------------------- what to offer

    @Test
    fun accessoriesAreRecognisedButNeverOfferedAsWatches() {
        for (name in listOf("HRM-Pro", "HRM-Dual", "HRM-Fit", "HRM 600", "Varia RTL515", "Index S2", "Tacx Neo 2T", "Rally RS200", "Vector 3", "tempe")) {
            val device = assertNotNull(named(name), name)
            assertEquals(GarminKind.ACCESSORY, device.kind, name)
            assertFalse(device.offerForSync, name)
            assertEquals(BleSync.NOT_APPLICABLE, device.bleSync, name)
        }
        val remote = assertNotNull(named("Edge Remote"))
        assertEquals(GarminKind.ACCESSORY, remote.kind)
        assertEquals(GarminKind.CAMERA, named("VIRB Ultra 30")?.kind)
        assertFalse(named("VIRB Ultra 30")!!.offerForSync)
    }

    @Test
    fun watchesBikeComputersAndGolfDevicesAreOffered() {
        assertEquals(GarminKind.WATCH, named("Approach S70")?.kind)
        assertEquals(GarminKind.GOLF, named("Approach G12")?.kind)
        assertEquals(GarminKind.BIKE_COMPUTER, named("Edge 1040")?.kind)
        assertEquals(GarminKind.BIKE_COMPUTER, named("Edge Explore 2")?.kind)
        assertEquals(GarminKind.HANDHELD, named("GPSMAP 66i")?.kind)
        for (name in listOf("Approach S70", "Approach G12", "Edge 1040", "Forerunner 965", "fenix 8", "Lily 2")) {
            assertTrue(named(name)!!.offerForSync, name)
        }
        assertFalse(named("GPSMAP 66i")!!.offerForSync)
    }

    @Test
    fun theSyncAdviceSaysWhatIsKnownAndWhatIsNot() {
        assertEquals(BleSync.LISTED, named("fenix 8 - 47mm")?.bleSync)
        assertEquals(BleSync.LISTED, named("Forerunner 965")?.bleSync)
        // Gadgetbridge turns on the newer sync protocol for these; OpenConnect only has the classic one
        assertEquals(BleSync.NEEDS_NEWER_PROTOCOL, named("Venu X1")?.bleSync)
        assertEquals(BleSync.NEEDS_NEWER_PROTOCOL, named("Venu 4 45mm")?.bleSync)
        assertEquals(BleSync.NEEDS_NEWER_PROTOCOL, named("CIRQA Smart Band")?.bleSync)
        // no support in Gadgetbridge's list: it may work, nobody has said so
        assertEquals(BleSync.UNKNOWN, named("Edge 1040")?.bleSync)
        assertEquals(BleSync.UNKNOWN, named("MARQ Aviator")?.bleSync)
    }

    // ------------------------------------------------------------------------- by advertisement

    @Test
    fun theProductNumberInGarminsAdvertisementNamesTheWatchWithoutAName() {
        val device = assertNotNull(withProduct(4536))
        assertEquals("fenix 8", device.displayName)
        assertEquals(GarminRecognised.PRODUCT_NUMBER, device.recognisedBy)
        assertEquals("Forerunner 965", withProduct(4315)?.displayName)
        assertEquals("Venu 3S", withProduct(4261)?.displayName)
        assertEquals("Edge 1040", withProduct(3843)?.displayName)
    }

    @Test
    fun theProductNumberBeatsAnOddNameTheWatchWasGiven() {
        assertEquals("Forerunner 965", withProduct(4315, name = "Stefan's watch")?.displayName)
        assertEquals("Forerunner 965", withProduct(4315, name = "fenix 8")?.displayName)
    }

    @Test
    fun aGarminAdvertisementWithAnUnknownProductNumberIsStillGarmin() {
        val device = assertNotNull(withProduct(60_000))
        assertNull(device.model)
        assertNull(device.family)
        assertNull(device.kind)
        assertEquals("Garmin", device.displayName)
        assertEquals(GarminRecognised.GARMIN_ONLY, device.recognisedBy)
        assertTrue(device.offerForSync)
        // and a name next to it says more
        assertEquals("Forerunner 999", withProduct(60_000, name = "Forerunner 999")?.displayName)
    }

    @Test
    fun anAccessoryNameWinsOverAnUnknownProductNumber() {
        val strap = assertNotNull(withProduct(60_000, name = "HRM-Pro"))
        assertEquals(GarminKind.ACCESSORY, strap.kind)
        assertFalse(strap.offerForSync)
    }

    @Test
    fun anotherMakersDataIsIgnoredAndShortGarminDataFallsBackToTheName() {
        assertNull(withProduct(4536, company = 0x004C)) // Apple's id: not Garmin without a Garmin name
        assertEquals("fenix 8", withProduct(4536, name = "fenix 8", company = 0x004C)?.displayName)
        val short = GarminDevices.fromAdvertisement("Venu 3", mapOf(GarminDevices.COMPANY_ID to byteArrayOf(0x10)))
        assertEquals("Venu 3", short?.displayName)
        assertNull(GarminDevices.fromAdvertisement(null, mapOf(GarminDevices.COMPANY_ID to ByteArray(0))))
    }

    // ------------------------------------------------------------------------- from the watch itself

    @Test
    fun theWatchesOwnDeviceInformationNamesIt() {
        assertEquals("fenix 8", GarminDevices.fromDeviceInfo(4536, "", "", "")?.displayName)
        assertEquals("Forerunner 965", GarminDevices.fromDeviceInfo(0, "Forerunner 965", "", "")?.displayName)
        // Garmin part numbers are 006-B<product number>-00: 3226 is the first Venu
        assertEquals("Venu", GarminDevices.fromDeviceInfo(0, "", "006-B3226-00", "")?.displayName)
        assertEquals("Venu 3", GarminDevices.fromDeviceInfo(0, "", "", "Venu 3")?.displayName)
        assertNull(GarminDevices.fromDeviceInfo(0, "", "", ""))
        assertNull(GarminDevices.fromDeviceInfo(0, "", "no part number", "unknown thing"))
    }

    @Test
    fun theProductNumberOutranksTheNameInDeviceInformation() {
        assertEquals("fenix 8", GarminDevices.fromDeviceInfo(4536, "my watch", "006-B4315-00", "Forerunner 965")?.displayName)
    }

    // ------------------------------------------------------------------------- from a FIT file

    @Test
    fun aFitFileNamesTheGarminThatWroteIt() {
        assertEquals("Forerunner 965", GarminDevices.fromFit(manufacturer = 1, product = 4315)?.displayName)
        assertEquals("fenix 8", GarminDevices.fromFit(manufacturer = 1, product = 4536)?.displayName)
    }

    @Test
    fun aFitFileFromSomeoneElseOrAnUnknownProductNamesNothing() {
        assertNull(GarminDevices.fromFit(manufacturer = 255, product = 4315)) // development
        assertNull(GarminDevices.fromFit(manufacturer = 1, product = 60_000))
        assertNull(GarminDevices.fromFit(manufacturer = null, product = null))
        assertNull(GarminDevices.fromFit(manufacturer = 1, product = null))
    }

    // ------------------------------------------------------------------------- the table itself

    @Test
    fun noNameAndNoProductNumberIsClaimedTwice() {
        val names = GarminDevices.models.map { GarminDevices.normalise(it.name) }
        assertEquals(names.size, names.toSet().size, "duplicate model name: ${names.groupingBy { it }.eachCount().filter { it.value > 1 }.keys}")
        val ids = GarminDevices.models.flatMap { it.productNumbers }
        assertEquals(ids.size, ids.toSet().size, "a product number is listed for two models")
        assertTrue(ids.all { it in 1..65_535 })
    }

    @Test
    fun everyFamilyHasModelsAndEveryModelBelongsToAFamilyOfItsKind() {
        for (family in GarminFamily.entries) {
            val models = GarminDevices.models.filter { it.family == family }
            if (family.kind == GarminKind.ACCESSORY || family.kind == GarminKind.CAMERA) continue // recognised by name only
            assertTrue(models.isNotEmpty(), "no model for $family")
        }
        assertEquals(265, GarminDevices.models.size, "a model was added or lost: update this count and docs/GARMIN_DEVICES.md")
    }

    @Test
    fun theThreeFenix8ModelsAreThereWithTheirProductNumbers() {
        val fenix8 = GarminDevices.models.filter { it.name.startsWith("fenix 8") }.map { it.name }.toSet()
        assertEquals(setOf("fenix 8", "fenix 8 Solar", "fenix 8 Pro"), fenix8)
        assertEquals(listOf(4534, 4536), GarminDevices.models.single { it.name == "fenix 8" }.productNumbers.sorted())
        assertEquals(listOf(4532, 4533), GarminDevices.models.single { it.name == "fenix 8 Solar" }.productNumbers.sorted())
        assertEquals(listOf(4631), GarminDevices.models.single { it.name == "fenix 8 Pro" }.productNumbers)
    }

    @Test
    fun theFamiliesReportTheirKindAndALabel() {
        assertEquals(GarminKind.BIKE_COMPUTER, GarminFamily.EDGE.kind)
        assertEquals(GarminKind.WATCH, GarminFamily.FENIX.kind)
        assertEquals("fenix", GarminFamily.FENIX.label)
        assertEquals("vívoactive", GarminFamily.VIVOACTIVE.label)
        assertTrue(GarminFamily.entries.map { it.label }.toSet().size == GarminFamily.entries.size)
    }

    @Test
    fun theWholeGarminNameListFromGadgetbridgeIsRecognised() {
        // names Gadgetbridge's coordinators accept, as a watch advertises them; each must come out as a watch, not as nothing
        val advertised = listOf(
            "fenix 8 - 47mm", "fenix 8 Pro - 51mm", "fenix 7S", "fenix 6X Pro Solar", "fenix 5X Plus", "fenix 3 HR", "fenix Chronos",
            "fenix E", "Forerunner 970", "Forerunner 570 42mm", "Forerunner 265S", "Forerunner 245 Music", "Forerunner 735XT",
            "Forerunner 55", "Venu 2 Plus", "Venu Sq 2 Music", "Venu X1", "vívoactive 3 Music", "vívoactive HR", "vívomove HR",
            "vívosmart HR+", "Instinct Crossover", "Instinct E - 45mm", "Instinct 3 SolTac", "Instinct 2 SolTac", "Enduro 4",
            "Descent G2", "tactix 7", "quatix 8 - 47mm", "Lily 2 Active", "D2 Air X15", "D2 Mach 2 Pro", "Swim 2", "EPIX",
        )
        for (name in advertised) {
            val device = assertNotNull(named(name), "$name was not recognised")
            assertEquals(GarminKind.WATCH, device.kind, name)
            assertTrue(device.offerForSync, name)
        }
    }
}

private fun <T> assertNotEquals(a: T, b: T) = assertTrue(a != b, "expected different values but both were $a")
