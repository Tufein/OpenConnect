package nl.tufein.openconnect

import nl.tufein.openconnect.ui.Palette
import org.junit.Test
import kotlin.test.assertEquals

/**
 * A colour theme stored by an older version is no longer offered. It must never crash the app or bring back a screen
 * that the picker no longer shows: every unknown key opens as Olive, the calm default.
 */
class PaletteFallbackTest {
    @Test
    fun `an unknown or older key opens as olive and a known key stays itself`() {
        assertEquals(Palette.OLIVE, Palette.of(null))
        assertEquals(Palette.OLIVE, Palette.of(""))
        assertEquals(Palette.OLIVE, Palette.of("handheld"))
        assertEquals(Palette.OLIVE, Palette.of("synthwave"))
        assertEquals(Palette.OLIVE, Palette.of("gameboy"))
        for (palette in Palette.entries) assertEquals(palette, Palette.of(palette.key))
    }
}
