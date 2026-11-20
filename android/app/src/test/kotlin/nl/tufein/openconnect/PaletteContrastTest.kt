package nl.tufein.openconnect

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import nl.tufein.openconnect.ui.Palette
import nl.tufein.openconnect.ui.colorSchemeFor
import org.junit.Assert.assertTrue
import org.junit.Test

/** The same contrast contract as the Robolectric theme test, but pure JVM: quick enough to run before pushing. */
class PaletteContrastTest {
    private fun contrast(a: Color, b: Color): Double {
        val x = a.luminance().toDouble()
        val y = b.luminance().toDouble()
        return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05)
    }

    @Test
    fun everyPaletteIsReadableInDarkAndLight() {
        for (palette in Palette.entries.filter { it != Palette.DYNAMIC }) {
            for (dark in listOf(true, false)) {
                val c = colorSchemeFor(palette, dark)
                val name = "${palette.key} ${if (dark) "dark" else "light"}"
                assertTrue("$name: text on background", contrast(c.onBackground, c.background) >= 7.0)
                assertTrue("$name: text on panel", contrast(c.onSurface, c.surface) >= 7.0)
                assertTrue("$name: muted text", contrast(c.onSurfaceVariant, c.surface) >= 4.5)
                for ((accent, on) in listOf(c.primary to c.onPrimary, c.secondary to c.onSecondary, c.tertiary to c.onTertiary)) {
                    assertTrue("$name: text on an accent", contrast(on, accent) >= 4.5)
                }
                assertTrue("$name: accent against panel", contrast(c.primary, c.surface) >= 3.0)
            }
        }
    }
}
