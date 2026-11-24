package nl.tufein.openconnect

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import nl.tufein.openconnect.data.defaultPaletteKey
import nl.tufein.openconnect.ui.EditorialShapes
import nl.tufein.openconnect.ui.ModernShapes
import nl.tufein.openconnect.ui.Style
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The v1.9 look is editorial by default: large quiet cards, serif titles and warm olive. Every older style stays a choice. */
class ModernLookTest {
    @Test
    fun `the editorial style is the default`() {
        assertEquals(Style.EDITORIAL, Style.of(null))
        assertEquals(Style.EDITORIAL, Style.of("unknown"))
        assertEquals("editorial", Style.EDITORIAL.key)
        assertEquals("clean", Style.CLEAN.key)
    }

    @Test
    fun `a fresh install starts on the olive theme`() {
        assertEquals("olive", defaultPaletteKey())
    }

    @Test
    fun `surfaces in the modern style have generous rounded corners`() {
        val outline = ModernShapes.medium.createOutline(Size(200f, 120f), LayoutDirection.Ltr, Density(1f))
        assertTrue("a card is a rounded rectangle", outline is Outline.Rounded)
        val radius = (outline as Outline.Rounded).roundRect.topLeftCornerRadius.x
        assertTrue("the corner is clearly rounded, not a hairline bevel", radius >= 12f)
    }

    @Test
    fun `editorial cards use the very large radius`() {
        val outline = EditorialShapes.large.createOutline(Size(320f, 200f), LayoutDirection.Ltr, Density(1f))
        assertTrue("a card is a rounded rectangle", outline is Outline.Rounded)
        val radius = (outline as Outline.Rounded).roundRect.topLeftCornerRadius.x
        assertTrue("the editorial card radius is 24 dp", radius >= 24f)
    }
}
