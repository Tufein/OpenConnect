package nl.tufein.openconnect.core

/**
 * One line of the About screen: a piece of what the app is made of and the licence it comes under. [groups] are the
 * Gradle coordinates it covers, so a test can prove that no library in the build files lacks an entry.
 */
data class Licence(val name: String, val licence: String, val note: String, val url: String? = null, val groups: List<String> = emptyList())

object Licences {
    val all: List<Licence> = listOf(
        Licence(
            "OpenConnect", "AGPL-3.0-or-later",
            "Copyright (C) 2025-2026 Tufein and OpenConnect contributors. Releases up to v0.5.0 were MIT.",
            "https://github.com/Tufein/OpenConnect",
        ),
        Licence(
            "Gadgetbridge (the Garmin Bluetooth protocol)", "AGPL-3.0",
            "The Bluetooth sync is ported from Gadgetbridge. Copyright (C) 2023-2026 Petr Kadlec, Daniele Gobbetti, José Rebelo, Thomas Kuehne and other Gadgetbridge contributors.",
            "https://codeberg.org/Freeyourgadget/Gadgetbridge",
        ),
        Licence("Jersey 10 (typeface)", "SIL Open Font License 1.1", "Copyright 2023 The Soft Type Project Authors.", "https://github.com/scfried/soft-type-jersey"),
        Licence("IBM Plex Sans and IBM Plex Mono (typefaces)", "SIL Open Font License 1.1", "Copyright 2017 IBM Corp.", "https://github.com/IBM/plex"),
        Licence("Instrument Serif (typeface)", "SIL Open Font License 1.1", "Copyright 2022 The Instrument Serif Project Authors.", "https://github.com/Instrument/instrument-serif"),
        Licence("Mountain lake at dawn (editorial photo)", "Unsplash License", "Photo by Luca Bravo on Unsplash. Used with attribution; no endorsement implied.", "https://unsplash.com/photos/zAjdgNXsMeg"),
        Licence(
            "AndroidX: Compose, Activity, Lifecycle, Navigation, Security, Core, WorkManager, Health Connect client",
            "Apache-2.0", "Google and the Android Open Source Project.", "https://developer.android.com/jetpack",
            groups = listOf("androidx."),
        ),
        Licence(
            "Kotlin, kotlinx.serialization and kotlinx.coroutines", "Apache-2.0", "JetBrains and contributors.", "https://kotlinlang.org",
            groups = listOf("org.jetbrains.kotlin", "org.jetbrains.kotlinx"),
        ),
    )
}
