// OpenConnect, SPDX-License-Identifier: AGPL-3.0-or-later
package nl.tufein.openconnect.core.watch

import java.text.Normalizer

/** What a Garmin device is for. Only some kinds are offered when OpenConnect looks for something to sync. */
enum class GarminKind {
    WATCH,
    BIKE_COMPUTER,

    /** Golf GPS devices that are not watches (Approach G, X and Z). */
    GOLF,

    /** Outdoor handhelds (eTrex, Oregon, GPSMAP, ...): they write FIT files but are not synced over Bluetooth. */
    HANDHELD,
    CAMERA,

    /** Heart rate straps, Varia lights and radars, pedals, trainers, scales: not something to sync a day from. */
    ACCESSORY,
    ;

    /** True for the kinds that talk Garmin's file-sync protocol, so the ones the Bluetooth sync list shows. */
    val syncsOverBluetooth: Boolean get() = this == WATCH || this == BIKE_COMPUTER || this == GOLF
}

/**
 * The families Garmin sells its devices in. [prefix] families are recognised from the first word of a name even for a
 * model that is not in [GarminModelTable] yet; [digitsOk] says whether a number may follow the word directly
 * ("vivoactive3"), which is not allowed for the short words that would otherwise match other things ("D215").
 */
enum class GarminFamily(
    val label: String,
    val kind: GarminKind,
    internal val prefix: Boolean = true,
    internal val digitsOk: Boolean = true,
) {
    FENIX("fenix", GarminKind.WATCH),
    EPIX("epix", GarminKind.WATCH),
    ENDURO("Enduro", GarminKind.WATCH),
    TACTIX("tactix", GarminKind.WATCH),
    QUATIX("quatix", GarminKind.WATCH),
    MARQ("MARQ", GarminKind.WATCH),
    FORERUNNER("Forerunner", GarminKind.WATCH),
    VENU("Venu", GarminKind.WATCH),
    VIVOACTIVE("vívoactive", GarminKind.WATCH),
    VIVOMOVE("vívomove", GarminKind.WATCH),
    VIVOSMART("vívosmart", GarminKind.WATCH),
    VIVOFIT("vívofit", GarminKind.WATCH),
    VIVOSPORT("vívosport", GarminKind.WATCH),
    VIVOKI("vívoki", GarminKind.WATCH),
    INSTINCT("Instinct", GarminKind.WATCH),
    DESCENT("Descent", GarminKind.WATCH),
    LILY("Lily", GarminKind.WATCH, digitsOk = false),
    SWIM("Swim", GarminKind.WATCH, digitsOk = false),
    BOUNCE("Bounce", GarminKind.WATCH, digitsOk = false),
    D2("D2", GarminKind.WATCH, digitsOk = false),
    LEGACY("Legacy", GarminKind.WATCH, digitsOk = false),
    CIRQA("Cirqa", GarminKind.WATCH, digitsOk = false),

    /** Approach: the S models are golf watches, the G, X and Z ones are handheld golf devices (a model says which). */
    APPROACH("Approach", GarminKind.WATCH),
    EDGE("Edge", GarminKind.BIKE_COMPUTER),
    ETREX("eTrex", GarminKind.HANDHELD),
    OREGON("Oregon", GarminKind.HANDHELD),
    RINO("Rino", GarminKind.HANDHELD, digitsOk = false),
    GPSMAP("GPSMAP", GarminKind.HANDHELD),
    FORETREX("Foretrex", GarminKind.HANDHELD),
    VIRB("VIRB", GarminKind.CAMERA, digitsOk = false),
    HRM("HRM", GarminKind.ACCESSORY, digitsOk = false),
    VARIA("Varia", GarminKind.ACCESSORY, digitsOk = false),
    VECTOR("Vector", GarminKind.ACCESSORY, digitsOk = false),
    RALLY("Rally", GarminKind.ACCESSORY, digitsOk = false),
    TACX("Tacx", GarminKind.ACCESSORY, digitsOk = false),
    TEMPE("tempe", GarminKind.ACCESSORY, digitsOk = false),

    /** Garmin's scale and sleep monitor: only the names in the table, because "Index" alone is a common word. */
    INDEX("Index", GarminKind.ACCESSORY, prefix = false),
}

/** One Garmin model. [productNumbers] are the numbers Garmin gave it (one per size or region), empty for a variant. */
data class GarminModel(
    val family: GarminFamily,
    val name: String,
    val kind: GarminKind,
    val productNumbers: List<Int>,
    /** Gadgetbridge has a device for exactly this name. */
    val inGadgetbridge: Boolean,
    /** Gadgetbridge turns its newer sync protocol on for this model, which OpenConnect does not have. */
    val newSyncProtocol: Boolean,
    internal val aliases: List<String> = emptyList(),
)

/** What is known about syncing this device over Bluetooth with OpenConnect's port of Gadgetbridge's protocol. */
enum class BleSync {
    /** Gadgetbridge supports this exact model with the classic sync OpenConnect has. Not tried here on any watch. */
    LISTED,

    /** Gadgetbridge needs the newer sync protocol for it; OpenConnect has the classic one, so files may be missing. */
    NEEDS_NEWER_PROTOCOL,

    /** Not in Gadgetbridge's list, or the model is not known: it may work, nobody has said so. */
    UNKNOWN,

    /** An accessory, camera or handheld: there is nothing to sync a day from. */
    NOT_APPLICABLE,
}

/** How a device was recognised, from the most certain to the least. */
enum class GarminRecognised(val label: String) {
    PRODUCT_NUMBER("product number"),
    NAME("name"),
    FAMILY_NAME("family name"),
    GARMIN_ONLY("Garmin id only"),
}

data class GarminDevice(
    /** The exact model, or null when only the family (or only "Garmin") is known. */
    val model: GarminModel?,
    val family: GarminFamily?,
    /** What to show: the model's name, or the name the device gave when the model is not in the table. */
    val displayName: String,
    val kind: GarminKind?,
    val recognisedBy: GarminRecognised,
) {
    /** False for accessories, cameras and handhelds. An unknown Garmin device is offered: it may well be a watch. */
    val offerForSync: Boolean get() = kind?.syncsOverBluetooth ?: true

    val bleSync: BleSync
        get() = when {
            kind != null && !kind.syncsOverBluetooth -> BleSync.NOT_APPLICABLE
            model?.newSyncProtocol == true -> BleSync.NEEDS_NEWER_PROTOCOL
            model?.inGadgetbridge == true -> BleSync.LISTED
            else -> BleSync.UNKNOWN
        }
}

/**
 * Recognises Garmin devices, all of them, from whatever is at hand: what a device advertises over Bluetooth, what the
 * watch says about itself after the handshake, or what a FIT file says about the device that wrote it.
 *
 * Recognising is not the same as syncing: that a Forerunner or a Venu is *recognised* says nothing about whether the
 * Bluetooth sync works with it. [BleSync] keeps those apart; the honest answer for every model is "not tried on a
 * real watch".
 *
 * The way a product number rides in the advertisement (Garmin's company id, 0x0087, followed by the number in two
 * bytes, big end first) is how Gadgetbridge's Garmin support recognises a watch before it has a name.
 */
object GarminDevices {
    /** Garmin International, Inc. in Bluetooth's list of company ids. */
    const val COMPANY_ID = 0x0087

    /** Garmin's FIT manufacturer number for Garmin itself. */
    const val FIT_MANUFACTURER_GARMIN = 1

    // the patterns come first: the tables below are built with normalise(), and an object starts in the order it is written
    private val UNIT_NUMBER = Regex("^uat (\\d{3,5})$")
    private val PART_NUMBER = Regex("^\\d{3}-B(\\d{3,5})-\\d{2}$")
    private val SIZE = Regex("\\b\\d{2,3}\\s?mm\\b")
    private val SEPARATORS = Regex("[-_\\u2013]")
    private val SPACES = Regex("\\s+")
    private val DIACRITICS = Regex("\\p{M}+")
    private val LEADING_GARMIN = Regex("^garmin\\s+", RegexOption.IGNORE_CASE)

    val models: List<GarminModel> get() = GarminModelTable.models

    private val byProduct: Map<Int, GarminModel> = HashMap<Int, GarminModel>().also { map ->
        for (model in GarminModelTable.models) {
            for (number in model.productNumbers) {
                val clash = map.put(number, model)
                check(clash == null) { "product number $number is listed for both ${clash?.name} and ${model.name}" }
            }
        }
    }

    private val byName: Map<String, GarminModel> = HashMap<String, GarminModel>().also { map ->
        for (model in GarminModelTable.models) {
            for (key in listOf(model.name) + model.aliases) {
                val clash = map.put(normalise(key), model)
                check(clash == null) { "the name '$key' is listed for both ${clash?.name} and ${model.name}" }
            }
        }
    }

    /** Names whose capitals matter: the first epix advertises "EPIX", the second generation "epix - 47mm". */
    private val byExactName: Map<String, String> = mapOf("EPIX" to "epix (Gen 1)")

    private val modelByName: Map<String, GarminModel> by lazy { GarminModelTable.models.associateBy { it.name } }


    /**
     * A name as comparing wants it: lower case, without accents or the (R) mark, without a size such as "- 47mm",
     * hyphens as spaces, and without a leading "Garmin". "Forerunner(R) 965", "forerunner 965" and "Garmin Forerunner 965 - 47mm"
     * all come out the same.
     */
    fun normalise(name: String): String {
        var s = Normalizer.normalize(name, Normalizer.Form.NFD).replace(DIACRITICS, "")
        s = s.replace("®", "").replace("™", "").lowercase()
        s = SIZE.replace(s, " ")
        s = SEPARATORS.replace(s, " ")
        s = SPACES.replace(s, " ").trim()
        return if (s.startsWith("garmin ")) s.removePrefix("garmin ").trim() else s
    }

    fun byProductNumber(product: Int): GarminModel? = byProduct[product]

    /** The device a name stands for, or null when the name is not a Garmin's. */
    fun fromName(name: String?): GarminDevice? {
        val raw = name?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        byExactName[raw]?.let { exact -> modelByName[exact]?.let { return of(it, GarminRecognised.NAME) } }
        val n = normalise(raw)
        if (n.isEmpty()) return null
        byName[n]?.let { return of(it, GarminRecognised.NAME) }
        UNIT_NUMBER.matchEntire(n)?.let { m ->
            return byProduct[m.groupValues[1].toInt()]?.let { of(it, GarminRecognised.NAME) }
        }
        val family = familyOf(n)
        if (family != null) {
            val shown = raw.replace(LEADING_GARMIN, "").replace("\u00AE", "").replace("\u2122", "")
            return GarminDevice(null, family, shown, family.kind, GarminRecognised.FAMILY_NAME)
        }
        val lower = raw.lowercase()
        if (lower == "garmin" || lower.startsWith("garmin ") || lower.startsWith("garmin-")) {
            return GarminDevice(null, null, raw, null, GarminRecognised.GARMIN_ONLY)
        }
        return null
    }

    private val familyWords: List<Pair<GarminFamily, String>> =
        GarminFamily.entries.filter { it.prefix }.map { it to normalise(it.label) }

    private fun familyOf(normalised: String): GarminFamily? =
        familyWords.firstOrNull { (family, word) ->
            normalised == word || (
                normalised.startsWith(word) && normalised[word.length].let { next ->
                    next == ' ' || (family.digitsOk && next.isDigit())
                }
                )
        }?.first

    /**
     * What a scan result tells: Garmin's manufacturer data first (the product number names the model whatever the
     * watch was called), then the advertised name. [manufacturerData] maps a company id to its bytes.
     */
    fun fromAdvertisement(name: String?, manufacturerData: Map<Int, ByteArray>): GarminDevice? {
        val byName = fromName(name)
        val data = manufacturerData[COMPANY_ID]?.takeIf { it.size >= 2 } ?: return byName
        val product = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
        byProduct[product]?.let { return of(it, GarminRecognised.PRODUCT_NUMBER) }
        return byName ?: GarminDevice(null, null, name?.trim()?.takeIf { it.isNotEmpty() } ?: "Garmin", null, GarminRecognised.GARMIN_ONLY)
    }

    /**
     * What the watch says about itself in its device information: the product number first, then the part number
     * (Garmin's are written `006-B<product number>-00`), then the names it reports.
     */
    fun fromDeviceInfo(productNumber: Int, deviceName: String, deviceModel: String, bluetoothName: String): GarminDevice? {
        byProduct[productNumber]?.let { return of(it, GarminRecognised.PRODUCT_NUMBER) }
        PART_NUMBER.matchEntire(deviceModel.trim())?.let { match ->
            byProduct[match.groupValues[1].toInt()]?.let { return of(it, GarminRecognised.PRODUCT_NUMBER) }
        }
        return fromName(deviceName) ?: fromName(bluetoothName) ?: fromName(deviceModel)
    }

    /** The device that wrote a FIT file, from its `file_id` message; null for any other maker or an unknown product. */
    fun fromFit(manufacturer: Int?, product: Int?): GarminDevice? {
        if (manufacturer != FIT_MANUFACTURER_GARMIN || product == null) return null
        return byProduct[product]?.let { of(it, GarminRecognised.PRODUCT_NUMBER) }
    }

    private fun of(model: GarminModel, how: GarminRecognised) = GarminDevice(model, model.family, model.name, model.kind, how)
}
