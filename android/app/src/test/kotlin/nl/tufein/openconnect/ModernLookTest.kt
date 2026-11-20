package nl.tufein.openconnect

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import nl.tufein.openconnect.data.defaultPaletteKey
import nl.tufein.openconnect.ui.ModernShapes
import nl.tufein.openconnect.ui.Style
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The v1.0 look is modern by default: rounded surfaces, clear type, soft shadows. The retro look stays a choice. */
class ModernLookTest {
    @Test
    fun `the modern style is the default`() {
        assertEquals(Style.CLEAN, Style.of(null))
        assertEquals(Style.CLEAN, Style.of("unknown"))
        assertEquals("clean", Style.CLEAN.key)
    }

    @Test
    fun `a fresh install starts on the graphite theme`() {
        assertEquals("graphite", defaultPaletteKey())
    }

    @Test
    fun `surfaces in the modern style have generous rounded corners`() {
        val outline = ModernShapes.medium.createOutline(Size(200f, 120f), LayoutDirection.Ltr, Density(1f))
        assertTrue("a card is a rounded rectangle", outline is Outline.Rounded)
        val radius = (outline as Outline.Rounded).roundRect.topLeftCornerRadius.x
        assertTrue("the corner is clearly rounded, not a hairline bevel", radius >= 12f)
    }
}
