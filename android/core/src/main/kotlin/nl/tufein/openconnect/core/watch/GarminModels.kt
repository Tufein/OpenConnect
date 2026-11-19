// OpenConnect, SPDX-License-Identifier: AGPL-3.0-or-later
package nl.tufein.openconnect.core.watch

import nl.tufein.openconnect.core.watch.GarminFamily.APPROACH
import nl.tufein.openconnect.core.watch.GarminFamily.BOUNCE
import nl.tufein.openconnect.core.watch.GarminFamily.CIRQA
import nl.tufein.openconnect.core.watch.GarminFamily.D2
import nl.tufein.openconnect.core.watch.GarminFamily.DESCENT
import nl.tufein.openconnect.core.watch.GarminFamily.EDGE
import nl.tufein.openconnect.core.watch.GarminFamily.ENDURO
import nl.tufein.openconnect.core.watch.GarminFamily.EPIX
import nl.tufein.openconnect.core.watch.GarminFamily.ETREX
import nl.tufein.openconnect.core.watch.GarminFamily.FENIX
import nl.tufein.openconnect.core.watch.GarminFamily.FORERUNNER
import nl.tufein.openconnect.core.watch.GarminFamily.FORETREX
import nl.tufein.openconnect.core.watch.GarminFamily.GPSMAP
import nl.tufein.openconnect.core.watch.GarminFamily.INDEX
import nl.tufein.openconnect.core.watch.GarminFamily.INSTINCT
import nl.tufein.openconnect.core.watch.GarminFamily.LEGACY
import nl.tufein.openconnect.core.watch.GarminFamily.LILY
import nl.tufein.openconnect.core.watch.GarminFamily.MARQ
import nl.tufein.openconnect.core.watch.GarminFamily.OREGON
import nl.tufein.openconnect.core.watch.GarminFamily.QUATIX
import nl.tufein.openconnect.core.watch.GarminFamily.RINO
import nl.tufein.openconnect.core.watch.GarminFamily.SWIM
import nl.tufein.openconnect.core.watch.GarminFamily.TACTIX
import nl.tufein.openconnect.core.watch.GarminFamily.VENU
import nl.tufein.openconnect.core.watch.GarminFamily.VIVOACTIVE
import nl.tufein.openconnect.core.watch.GarminFamily.VIVOFIT
import nl.tufein.openconnect.core.watch.GarminFamily.VIVOKI
import nl.tufein.openconnect.core.watch.GarminFamily.VIVOMOVE
import nl.tufein.openconnect.core.watch.GarminFamily.VIVOSMART
import nl.tufein.openconnect.core.watch.GarminFamily.VIVOSPORT

/**
 * The Garmin models OpenConnect knows by name and by product number (v1.0.0-rc.2).
 *
 * Where it comes from:
 * - **Product numbers:** the `garmin_product` list in Garmin's FIT profile (version 21.222). A number is a fact about a
 *   product, so only the numbers and the product names are used here, none of Garmin's SDK code. Regional variants
 *   (the `_asia`, `_japan`, ... entries) are folded into one model; a model's sizes share one entry.
 *   Variants that Garmin does not give a number of their own (Pro, Sapphire, Solar, Tactical, ...) are listed
 *   without numbers and are found by name only.
 * - **`gb = true`:** Gadgetbridge (codeberg.org/Freeyourgadget/Gadgetbridge, AGPL-3.0) has a device for exactly this
 *   name, which is what the Bluetooth sync of OpenConnect was ported from. It says nothing about whether it works
 *   with OpenConnect: nothing here was tried on a real watch.
 * - **`newSync = true`:** Gadgetbridge turns its "new sync protocol" on by default for this model, because it misses
 *   files without it. OpenConnect only has the classic one.
 *
 * To add a model: add a row here (name as the watch advertises it, without the size), the product numbers from the
 * FIT profile if Garmin gave it one, and a test line in `GarminDevicesTest`. Two models may not share a name or a
 * number; [GarminDevices] refuses to start when they do.
 */
internal object GarminModelTable {
    @Suppress("LongMethod")
    val models: List<GarminModel> = listOf(
        // Forerunner
        m(FORERUNNER, "Forerunner 301", 473, 474, 475, 494),
        m(FORERUNNER, "Forerunner 405", 717, 987),
        m(FORERUNNER, "Forerunner 50", 782),
        m(FORERUNNER, "Forerunner 60", 988),
        m(FORERUNNER, "Forerunner 110", 1124, 1274),
        m(FORERUNNER, "Forerunner 210", 1360),
        m(FORERUNNER, "Forerunner 310XT", 1018, 1446),
        m(FORERUNNER, "Forerunner 910XT", 1328, 1537, 1600, 1664),
        m(FORERUNNER, "Forerunner 610", 1345, 1410),
        m(FORERUNNER, "Forerunner 10", 1482, 1688),
        m(FORERUNNER, "Forerunner 15", 1903, 2061),
        m(FORERUNNER, "Forerunner 620", 1623, 1928, 1929, 2072, 2173, gb = true),
        m(FORERUNNER, "Forerunner 220", 1632, 1930, 1931, 2073, 2174, gb = true),
        m(FORERUNNER, "Forerunner 920XT", 1765, 2130, 2131, 2132, gb = true, aliases = listOf("Forerunner 920")),
        m(FORERUNNER, "Forerunner 225", 2153, 2219, gb = true),
        m(FORERUNNER, "Forerunner 25", 2148, gb = true),
        m(FORERUNNER, "Forerunner 630", 2156, 2310, 2311, gb = true),
        m(FORERUNNER, "Forerunner 230", 2157, 2313, gb = true),
        m(FORERUNNER, "Forerunner 735XT", 2158, 2533, 2534, gb = true),
        m(FORERUNNER, "Forerunner 235", 2396, 2397, 2431, 2733, 3144, gb = true),
        m(FORERUNNER, "Forerunner 35", 2503, 2650, 2667, 2668, 2727, 2814, gb = true),
        m(FORERUNNER, "Forerunner 935", 2691, 2833, gb = true),
        m(FORERUNNER, "Forerunner 45", 3282, 3469, gb = true),
        m(FORERUNNER, "Forerunner 45 Plus", gb = true),
        m(FORERUNNER, "Forerunner 30", 2891, 2977, gb = true),
        m(FORERUNNER, "Forerunner 645", 2886, 3003, gb = true),
        m(FORERUNNER, "Forerunner 645 Music", 2888, 3004, gb = true),
        m(FORERUNNER, "Forerunner 245", 3076, 3145, gb = true),
        m(FORERUNNER, "Forerunner 245 Music", 3077, 3321, gb = true),
        m(FORERUNNER, "Forerunner 945", 3113, 3441, gb = true),
        m(FORERUNNER, "Forerunner 945 LTE", 3652, 3978, gb = true),
        m(FORERUNNER, "Forerunner 745", 3589, 3794, gb = true),
        m(FORERUNNER, "Forerunner 55", 3869, 4033, gb = true),
        m(FORERUNNER, "Forerunner 255", 3992, gb = true),
        m(FORERUNNER, "Forerunner 255 Music", 3990, gb = true),
        m(FORERUNNER, "Forerunner 255S", 3993, gb = true),
        m(FORERUNNER, "Forerunner 255S Music", 3991, gb = true),
        m(FORERUNNER, "Forerunner 955", 4024, gb = true),
        m(FORERUNNER, "Forerunner 265", 4257, gb = true),
        m(FORERUNNER, "Forerunner 265S", 4258, gb = true),
        m(FORERUNNER, "Forerunner 965", 4315, gb = true),
        m(FORERUNNER, "Forerunner 165", 4432, gb = true),
        m(FORERUNNER, "Forerunner 165 Music", 4433, gb = true),
        m(FORERUNNER, "Forerunner 970", 4565, gb = true),
        m(FORERUNNER, "Forerunner 570", 4570, 4574, gb = true),
        m(FORERUNNER, "Forerunner 170", 4815, gb = true),
        m(FORERUNNER, "Forerunner 170 Music", 4814, gb = true),
        m(FORERUNNER, "Forerunner 70", 4916, gb = true),
        // Fenix
        m(FENIX, "fenix", 1551),
        m(FENIX, "fenix 2", 1967, gb = true),
        m(FENIX, "fenix 3", 2050, 2188, 2189, 2293, 2407, 2408, gb = true),
        m(FENIX, "fenix 3 HR", 2413, 2473, 2474, 2475, 2476, 2477, gb = true),
        m(FENIX, "fenix Chronos", 2432, 2675, gb = true, aliases = listOf("fenix 3 Chronos")),
        m(FENIX, "fenix 5s", 2544, 2797, gb = true),
        m(FENIX, "fenix 5", 2697, 2796, gb = true),
        m(FENIX, "fenix 5X", 2604, 2798, gb = true),
        m(FENIX, "fenix 5s Plus", 2900, 3134, gb = true),
        m(FENIX, "fenix 5 Plus", 3110, gb = true),
        m(FENIX, "fenix 5X Plus", 3111, 3135, gb = true),
        m(FENIX, "fenix 6S Sport", 3287, 3512, gb = true),
        m(FENIX, "fenix 6S", 3288, 3513, gb = true),
        m(FENIX, "fenix 6 Sport", 3289, 3514, gb = true),
        m(FENIX, "fenix 6", 3290, 3515, gb = true),
        m(FENIX, "fenix 6X", 3291, 3516, gb = true),
        m(FENIX, "fenix 6 Pro", gb = true),
        m(FENIX, "fenix 6 Sapphire", gb = true),
        m(FENIX, "fenix 6S Pro", gb = true),
        m(FENIX, "fenix 6S Pro Solar", gb = true),
        m(FENIX, "fenix 6S Sapphire", gb = true),
        m(FENIX, "fenix 6X Pro", gb = true),
        m(FENIX, "fenix 6X Pro Solar", gb = true),
        m(FENIX, "fenix 6X Sapphire", gb = true),
        m(FENIX, "fenix 7S", 3905, 3908, gb = true),
        m(FENIX, "fenix 7", 3906, 3909, gb = true),
        m(FENIX, "fenix 7X", 3907, 3910, gb = true),
        m(FENIX, "fenix 7S Pro Solar", 4374, gb = true),
        m(FENIX, "fenix 7 Pro Solar", 4375, 4595, gb = true),
        m(FENIX, "fenix 7X Pro Solar", 4376, gb = true),
        m(FENIX, "fenix 7S Pro", gb = true),
        m(FENIX, "fenix 7 Pro", gb = true),
        m(FENIX, "fenix 7X Pro", gb = true),
        m(FENIX, "fenix 8", 4534, 4536, gb = true),
        m(FENIX, "fenix 8 Solar", 4532, 4533, gb = true),
        m(FENIX, "fenix 8 Pro", 4631, gb = true),
        m(FENIX, "fenix E", 4666, gb = true),
        m(FENIX, "fenix 9", 5133, 5134, gb = true),
        m(FENIX, "fenix 9 Pro", 4952, 4953, 4954, gb = true),
        m(FENIX, "fenix 9 Pro Solar", 4955, 4956, gb = true),
        // Epix
        m(EPIX, "epix (Gen 1)", 1988, 2332, 2457, gb = true),
        m(EPIX, "epix (Gen 2)", 3943, 3944, gb = true, aliases = listOf("epix")),
        m(EPIX, "epix Pro (Gen 2)", 4312, 4313, 4314, gb = true, aliases = listOf("epix pro")),
        // Enduro
        m(ENDURO, "Enduro", 3638, 3872, gb = true),
        m(ENDURO, "Enduro 2", 4341, gb = true),
        m(ENDURO, "Enduro 3", 4575, gb = true),
        m(ENDURO, "Enduro 4", 5169, gb = true),
        // Tactix
        m(TACTIX, "tactix 7", 4135, gb = true),
        m(TACTIX, "tactix 8", 4775, gb = true),
        m(TACTIX, "tactix 8 Solar", 4776, gb = true),
        // Quatix
        m(QUATIX, "quatix 6", gb = true),
        m(QUATIX, "quatix 6X", gb = true),
        m(QUATIX, "quatix 7", gb = true),
        m(QUATIX, "quatix 7X", gb = true),
        m(QUATIX, "quatix 8", gb = true),
        // Marq
        m(MARQ, "MARQ Driver", 3246, 3420),
        m(MARQ, "MARQ Aviator", 3247, 3421),
        m(MARQ, "MARQ Captain", 3248, 3448),
        m(MARQ, "MARQ Commander", 3249, 3449),
        m(MARQ, "MARQ Expedition", 3250, 3450),
        m(MARQ, "MARQ Athlete", 3251, 3451),
        m(MARQ, "MARQ Adventurer", 3624, 3648),
        m(MARQ, "MARQ Golfer", 3739, 3850),
        m(MARQ, "MARQ (Gen 2)", 4105),
        m(MARQ, "MARQ (Gen 2) Aviator", 4124),
        m(MARQ, "MARQ (Gen 2) Commander", 4472),
        m(MARQ, "MARQ 3", 5000),
        // Venu
        m(VENU, "Venu", 3226, 3737, 3740, gb = true),
        m(VENU, "Venu 2", 3703, 3950, gb = true),
        m(VENU, "Venu 2S", 3704, 3949, gb = true),
        m(VENU, "Venu 2 Plus", 3851, 4017, gb = true),
        m(VENU, "Venu 3", 4260, gb = true),
        m(VENU, "Venu 3S", 4261, gb = true),
        m(VENU, "Venu 4", 4643, 4644, gb = true, newSync = true),
        m(VENU, "Venu X1", 4603, gb = true, newSync = true),
        m(VENU, "Venu Sq", 3600, 3837, gb = true),
        m(VENU, "Venu Sq Music", 3596, 3599, gb = true),
        m(VENU, "Venu Sq 2", 4115, gb = true),
        m(VENU, "Venu Sq 2 Music", 4116, gb = true),
        // Vivoactive
        m(VIVOACTIVE, "vívoactive", 1907, 2160, gb = true),
        m(VIVOACTIVE, "vívoactive HR", 2337, 2497, gb = true),
        m(VIVOACTIVE, "vívoactive 3", 2700, 3473, gb = true, aliases = listOf("vívoactive3")),
        m(VIVOACTIVE, "vívoactive 3 Music", 2988, 3066, 3163, gb = true),
        m(VIVOACTIVE, "vívoactive 3T", 3446, gb = true),
        m(VIVOACTIVE, "vívoactive 4", 3225, 3388, 3389, gb = true),
        m(VIVOACTIVE, "vívoactive 4S", 3224, 3387, gb = true),
        m(VIVOACTIVE, "vívoactive 5", 4426, gb = true),
        m(VIVOACTIVE, "vívoactive 6", 4625, gb = true),
        // Vivomove
        m(VIVOMOVE, "vívomove", 2368, gb = true),
        m(VIVOMOVE, "vívomove HR", 2772, 2945, gb = true),
        m(VIVOMOVE, "vívomove 3", 3378, 3422),
        m(VIVOMOVE, "vívomove 3 Premium", 3308),
        m(VIVOMOVE, "vívomove Sport", 3982, gb = true),
        m(VIVOMOVE, "vívomove Style", gb = true),
        m(VIVOMOVE, "vívomove Trend", 3983, gb = true),
        // Vivosmart
        m(VIVOSMART, "vívosmart", 1956, 2135, 2294, gb = true),
        m(VIVOSMART, "vívosmart HR", 2348, 2361, gb = true),
        m(VIVOSMART, "vívosmart HR+", 2347, 2362, gb = true),
        m(VIVOSMART, "vívosmart 3", 2622, 2831, gb = true),
        m(VIVOSMART, "vívosmart 4", 2927, 3218, gb = true),
        m(VIVOSMART, "vívosmart 5", 4063, gb = true),
        // Vivofit
        m(VIVOFIT, "vívofit", 1837),
        m(VIVOFIT, "vívofit 2", 2150),
        m(VIVOFIT, "vívofit 3", 2406),
        m(VIVOFIT, "vívofit 4", 2878),
        m(VIVOFIT, "vívofit jr", 2606),
        // Vivosport
        m(VIVOSPORT, "vívosport", 2623, 2832, gb = true),
        // Vivoki
        m(VIVOKI, "vívoki", 1885),
        // Instinct
        m(INSTINCT, "Instinct", gb = true),
        m(INSTINCT, "Instinct Esports", 3126),
        m(INSTINCT, "Instinct Solar", 3466, gb = true, aliases = listOf("Instinct Dual Power")),
        m(INSTINCT, "Instinct Tactical", gb = true),
        m(INSTINCT, "Instinct Solar Tactical", gb = true, aliases = listOf("Instinct Solar Tac")),
        m(INSTINCT, "Instinct 2", 3888, 4071, gb = true),
        m(INSTINCT, "Instinct 2 Solar", gb = true),
        m(INSTINCT, "Instinct 2 SolTac", gb = true),
        m(INSTINCT, "Instinct 2S", 3889, gb = true),
        m(INSTINCT, "Instinct 2S Solar", gb = true),
        m(INSTINCT, "Instinct 2S Surf", gb = true),
        m(INSTINCT, "Instinct 2X", 4394, gb = true),
        m(INSTINCT, "Instinct 2X Solar", gb = true, aliases = listOf("Instinct 2X Sol")),
        m(INSTINCT, "Instinct 2X Tactical", gb = true),
        m(INSTINCT, "Instinct Crossover", 4155, gb = true),
        m(INSTINCT, "Instinct Crossover AMOLED", 4678),
        m(INSTINCT, "Instinct E", 4583, 4584, gb = true),
        m(INSTINCT, "Instinct 3", 4586, 4587, gb = true),
        m(INSTINCT, "Instinct 3 Solar", 4585, 4759, gb = true),
        m(INSTINCT, "Instinct 3 Tactical", gb = true, aliases = listOf("Instinct 3 Tac")),
        m(INSTINCT, "Instinct 3 SolTac", gb = true, aliases = listOf("Instinct 3 Solar Tac", "Instinct 3 Solar Tactical", "Instinct 3 Solar Tactical Edition")),
        // Descent
        m(DESCENT, "Descent Mk1", 2859),
        m(DESCENT, "Descent Mk2", 3258, 3702),
        m(DESCENT, "Descent Mk2S", 3542, 3930),
        m(DESCENT, "Descent Mk3", 4222, gb = true),
        m(DESCENT, "Descent Mk3i", 4223, gb = true),
        m(DESCENT, "Descent G1", 4005, 4132, gb = true),
        m(DESCENT, "Descent G2", 4588, gb = true),
        // Lily
        m(LILY, "Lily", 3615, 3809, gb = true),
        m(LILY, "Lily 2", 4380, gb = true),
        m(LILY, "Lily 2 Active", gb = true),
        m(LILY, "Lily Athlete", 4477),
        // Swim
        m(SWIM, "Swim", 1499),
        m(SWIM, "Swim 2", 3405, 3639, gb = true),
        // Bounce
        m(BOUNCE, "Bounce 2", 4745),
        // D2
        m(D2, "D2 Bravo", 2262),
        m(D2, "D2 Bravo Titanium", 2547),
        m(D2, "D2 Charlie", 2819),
        m(D2, "D2 Air", 2187, gb = true),
        m(D2, "D2 Air X10", 4125, gb = true),
        m(D2, "D2 Air X15", 4944, gb = true),
        m(D2, "D2 Mach 1", gb = true),
        m(D2, "D2 Mach 1 Pro", 4556, gb = true),
        m(D2, "D2 Mach 2", 4879, gb = true),
        m(D2, "D2 Mach 2 Pro", 5056, gb = true),
        // Cirqa
        m(CIRQA, "Cirqa Smart Band", 5019, gb = true, newSync = true),
        // Legacy
        m(LEGACY, "Legacy Hero Captain Marvel", 3500, 3535),
        m(LEGACY, "Legacy Hero First Avenger", 3501, 3536),
        m(LEGACY, "Legacy Saga Rey", 3498, 3537),
        m(LEGACY, "Legacy Saga Darth Vader", 3499, 3538),
        // Approach
        m(APPROACH, "Approach S6", 1936),
        m(APPROACH, "Approach S10", 3049),
        m(APPROACH, "Approach S12", 3823, 3986),
        m(APPROACH, "Approach S20", 2266),
        m(APPROACH, "Approach S40", 3314),
        m(APPROACH, "Approach S42", 3934, 4002),
        m(APPROACH, "Approach S44", 4647),
        m(APPROACH, "Approach S50", 4656),
        m(APPROACH, "Approach S60", 2656),
        m(APPROACH, "Approach S70", 4233),
        m(APPROACH, "Approach S72", 4874, 4875),
        m(APPROACH, "Approach G10", 1405, kind = GarminKind.GOLF),
        m(APPROACH, "Approach G12", 3927, 4001, kind = GarminKind.GOLF),
        m(APPROACH, "Approach G80", 3085, kind = GarminKind.GOLF),
        m(APPROACH, "Approach Z80", 2806, kind = GarminKind.GOLF),
        m(APPROACH, "Approach X10", 2962, kind = GarminKind.GOLF),
        m(APPROACH, "Approach X40", 2292, kind = GarminKind.GOLF),
        // Edge
        m(EDGE, "Edge 200", 1325, 1555),
        m(EDGE, "Edge 500", 1036, 1199, 1213, 1387, 1422),
        m(EDGE, "Edge 800", 1169, 1333, 1334, 1386, 1497),
        m(EDGE, "Edge 510", 1561, 1742, 1821, 1918),
        m(EDGE, "Edge 810", 1567, 1721, 1822, 1823),
        m(EDGE, "Edge Touring", 1736),
        m(EDGE, "Edge 1000", 1836, 2052, 2053, 2070, 2100, 2274),
        m(EDGE, "Edge Explore 1000", 2204),
        m(EDGE, "Edge 520", 2067, 2260, 2261),
        m(EDGE, "Edge 25", 2147, 2288, 2289),
        m(EDGE, "Edge 20", 2238, 2290),
        m(EDGE, "Edge 820", 2530, 2599, 2600, 2628, 2629, 2630),
        m(EDGE, "Edge Explore 820", 2531),
        m(EDGE, "Edge 1030", 2713, 2924, 3095),
        m(EDGE, "Edge Explore", 3011),
        m(EDGE, "Edge 130", 2909, 3092),
        m(EDGE, "Edge 520 Plus", 3112, 3142),
        m(EDGE, "Edge 530", 3121, 3349),
        m(EDGE, "Edge 830", 3122, 3350),
        m(EDGE, "Edge 130 Plus", 3558, 3813),
        m(EDGE, "Edge 1030 Plus", 3570, 3812),
        m(EDGE, "Edge 1040", 3843, 4305),
        m(EDGE, "Edge 540", 4061),
        m(EDGE, "Edge 840", 4062),
        m(EDGE, "Edge Explore 2", 4169),
        m(EDGE, "Edge 1050", 4440),
        m(EDGE, "Edge 550", 4633),
        m(EDGE, "Edge 850", 4634),
        m(EDGE, "Edge MTB", 4655),
        m(EDGE, "Edge 1060", 4962),
        m(EDGE, "Edge Explore 3", 4970),
        m(EDGE, "Edge Explore 3 Plus", 4972),
        m(EDGE, "Edge Remote", kind = GarminKind.ACCESSORY),
        // Etrex
        m(ETREX, "eTrex Touch", 2140),
        // Oregon
        m(OREGON, "Oregon 7xx", 2441, 2512),
        // Rino
        m(RINO, "Rino 7xx", 2444),
        // Gpsmap
        m(GPSMAP, "GPSMAP 66", 3028),
        m(GPSMAP, "GPSMAP 66i", 3284),
        // Foretrex
        m(FORETREX, "Foretrex 601/701", 2769),
        // Index
        m(INDEX, "Index S2"),
        m(INDEX, "Index Smart Scale"),
        m(INDEX, "Index Sleep Monitor"),
    )

    private fun m(
        family: GarminFamily,
        name: String,
        vararg productNumbers: Int,
        gb: Boolean = false,
        newSync: Boolean = false,
        kind: GarminKind? = null,
        aliases: List<String> = emptyList(),
    ) = GarminModel(family, name, kind ?: family.kind, productNumbers.toList(), inGadgetbridge = gb, newSyncProtocol = newSync, aliases = aliases)
}
