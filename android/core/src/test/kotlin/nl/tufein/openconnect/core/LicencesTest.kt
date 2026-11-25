package nl.tufein.openconnect.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** v0.55.0: the Over screen lists every licence of what the app is made of; a new library cannot arrive without one. */
class LicencesTest {
    /** Gradle runs core tests in `android/core`; the app's build file is next door. */
    private fun dependencies(): List<String> {
        val files = listOf(File("../app/build.gradle.kts"), File("build.gradle.kts"))
        val pattern = Regex("""(?:implementation|api)\(\s*(?:platform\()?"([^"]+)"""")
        return files.flatMap { f -> pattern.findAll(f.readText()).map { it.groupValues[1] }.toList() }
    }

    @Test
    fun everyLibraryTheAppShipsIsCoveredByALicenceEntry() {
        val deps = dependencies()
        assertTrue(deps.size >= 10, "the build files were found (${deps.size} libraries)")
        val uncovered = deps.filter { dep -> Licences.all.none { l -> l.groups.any { dep.startsWith(it) } } }
        assertTrue(uncovered.isEmpty(), "no licence entry for: $uncovered")
    }

    @Test
    fun theThingsThatAreNotLibrariesAreThere() {
        val names = Licences.all.joinToString(" ") { it.name }
        for (needed in listOf("Cercis", "Gadgetbridge", "Jersey 10", "IBM Plex")) {
            assertTrue(needed in names, "$needed is missing")
        }
    }

    @Test
    fun everyEntryNamesItsLicence() {
        for (l in Licences.all) assertTrue(l.licence.isNotBlank(), "${l.name} has no licence")
    }
}
