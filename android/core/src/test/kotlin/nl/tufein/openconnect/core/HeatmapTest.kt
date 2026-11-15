package nl.tufein.openconnect.core

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HeatmapTest {
    private val t0 = Instant.parse("2026-06-01T08:00:00Z")

    private fun p(lat: Double, lon: Double, i: Int = 0) = TrackPoint(time = t0.plusSeconds(i.toLong()), lat = lat, lon = lon)

    /** A straight line of [n] points going east from the start, 0.0001 degrees (about 7 m at 52 north) per point. */
    private fun east(n: Int, lat: Double = 52.0, lon: Double = 5.0) = (0 until n).map { p(lat, lon + it * 0.0001, it) }

    /** The same going north: 0.0001 degrees of latitude is about 11 m. */
    private fun north(n: Int, lat: Double = 52.0, lon: Double = 5.0) = (0 until n).map { p(lat + it * 0.0001, lon, it) }

    @Test
    fun `no positions means no map`() {
        assertNull(Heatmap.build(emptyList()))
        assertNull(Heatmap.build(listOf(listOf(TrackPoint(time = t0)))))
    }

    @Test
    fun `a wide route fills the columns and a tall one the rows`() {
        val wide = Heatmap.build(listOf(east(400)), size = 40)!!
        assertEquals(40, wide.cols)
        assertTrue(wide.rows <= 3, "rows were ${wide.rows}")
        val tall = Heatmap.build(listOf(north(400)), size = 40)!!
        assertEquals(40, tall.rows)
        assertTrue(tall.cols <= 3, "cols were ${tall.cols}")
    }

    @Test
    fun `each route counts once in a cell however long it stays there`() {
        val loiter = east(200) + (0 until 300).map { p(52.0, 5.0199, 200 + it) } // standing still at the end
        val grid = Heatmap.build(listOf(loiter), size = 20)!!
        assertEquals(1, grid.maxCount)
        assertEquals(1, grid.routes)
    }

    @Test
    fun `the same road run twice is twice as hot as a road run once`() {
        val grid = Heatmap.build(listOf(east(400), east(400), north(400, lon = 5.0)), size = 40)!!
        assertEquals(3, grid.routes)
        assertEquals(3, grid.maxCount) // where the two lines cross or meet
        assertTrue(grid.cells.any { it.count == 2 })
        assertTrue(grid.cells.any { it.count == 1 })
    }

    @Test
    fun `a far away stray point does not shrink the rest into a corner`() {
        val stray = east(300) + p(53.0, 6.0, 999)
        val grid = Heatmap.build(listOf(stray), size = 30)!!
        assertEquals(30, grid.cols) // still laid out on the 300 points, not on the stray
        assertTrue(grid.cells.size >= 25)
    }

    @Test
    fun `points far apart are joined so a sparse track has no holes`() {
        val sparse = listOf(p(52.0, 5.0, 0), p(52.0, 5.01, 1)) // about 680 m between two samples
        val grid = Heatmap.build(listOf(sparse), size = 20)!!
        assertTrue(grid.cells.size >= 15, "cells were ${grid.cells.size}")
    }

    @Test
    fun `levels follow the counts on a log scale`() {
        val grid = HeatGrid(10, 10, emptyList(), maxCount = 100, routes = 100)
        assertEquals(0, grid.level(0))
        assertEquals(1, grid.level(1))
        assertEquals(2, grid.level(10))
        assertEquals(4, grid.level(100))
        assertEquals(1, HeatGrid(1, 1, emptyList(), maxCount = 1, routes = 1).level(1))
        assertNotNull(grid)
    }
}
