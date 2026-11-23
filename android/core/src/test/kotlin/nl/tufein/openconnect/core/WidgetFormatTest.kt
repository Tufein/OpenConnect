package nl.tufein.openconnect.core

import kotlin.test.Test
import kotlin.test.assertEquals

class WidgetFormatTest {
    @Test
    fun `a small cell shows only readiness and steps`() {
        assertEquals(WidgetFormat.SMALL, WidgetFormats.forSize(110, 70))
        assertEquals(WidgetFormat.SMALL, WidgetFormats.forSize(179, 109))
    }

    @Test
    fun `three by two is the usual card`() {
        assertEquals(WidgetFormat.MEDIUM, WidgetFormats.forSize(180, 110))
        assertEquals(WidgetFormat.MEDIUM, WidgetFormats.forSize(249, 179))
    }

    @Test
    fun `four by three and bigger shows sleep and rest too`() {
        assertEquals(WidgetFormat.LARGE, WidgetFormats.forSize(250, 180))
        assertEquals(WidgetFormat.LARGE, WidgetFormats.forSize(400, 300))
    }

    @Test
    fun `wide but short stays medium, tall but narrow stays small`() {
        assertEquals(WidgetFormat.MEDIUM, WidgetFormats.forSize(400, 110))
        assertEquals(WidgetFormat.SMALL, WidgetFormats.forSize(110, 400))
    }

    @Test
    fun `a launcher that reports no size gets the card from before v1-7`() {
        assertEquals(WidgetFormat.MEDIUM, WidgetFormats.forSize(0, 0))
    }
}
