package nl.tufein.openconnect

import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * v0.52.0: Android 9 (API 28) is the lowest Android the app runs on. The number lives in one place, `minSdk` in
 * `build.gradle.kts`, and no code may still ask whether the phone is older than that: such a check can only ever answer
 * the same way, so it is dead weight and a promise that old phones are still looked after.
 *
 * Gradle runs unit tests in the module directory (`android/app`), so the files are read by relative path.
 */
class MinimumAndroidTest {
    private fun minSdk(): Int {
        val gradle = File("build.gradle.kts").readText()
        return Regex("""minSdk\s*=\s*(\d+)""").find(gradle)!!.groupValues[1].toInt()
    }

    /** The API level behind a `Build.VERSION_CODES` name, for the names the app could plausibly use. */
    private val named = mapOf(
        "LOLLIPOP" to 21, "M" to 23, "N" to 24, "N_MR1" to 25, "O" to 26, "O_MR1" to 27, "P" to 28, "Q" to 29,
        "R" to 30, "S" to 31, "S_V2" to 32, "TIRAMISU" to 33, "UPSIDE_DOWN_CAKE" to 34, "VANILLA_ICE_CREAM" to 35,
    )

    @Test
    fun `the lowest Android is 9`() {
        assertEquals(28, minSdk(), "Android 9 is API 28")
    }

    @Test
    fun `no version check asks for something older than the lowest Android`() {
        val min = minSdk()
        val check = Regex("""SDK_INT\s*(>=|<)\s*(?:[\w.]*Build\.VERSION_CODES\.(\w+)|(\d+))""")
        val sources = File("src/main/kotlin").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue(sources.size > 50, "the app sources were found")

        val dead = ArrayList<String>()
        for (file in sources) {
            for (match in check.findAll(file.readText())) {
                val level = match.groupValues[3].toIntOrNull() ?: named[match.groupValues[2]]
                    ?: error("${file.name}: unknown version name ${match.groupValues[2]}, add it to this test")
                // "SDK_INT >= 26" is always true and "SDK_INT < 26" always false once nothing older than 28 can run it.
                if (level <= min) dead.add("${file.name}: SDK_INT ${match.groupValues[1]} $level")
            }
        }
        assertEquals(emptyList(), dead, "these checks can no longer change the outcome, remove them")
    }
}
