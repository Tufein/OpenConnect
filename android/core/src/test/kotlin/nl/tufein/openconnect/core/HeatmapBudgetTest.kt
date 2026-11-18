package nl.tufein.openconnect.core

import java.lang.management.ManagementFactory
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * v0.58.0 (M5): the heatmap reads at most 300 activities with at most 500 positions each (see AppViewModel.loadMap), however
 * much history there is. This builds it from five years of demo tracks the way the app does and checks it stays quick and
 * does not allocate its way into an out-of-memory on a phone. The allocation is measured per thread, which does not depend on
 * when the garbage collector happens to run.
 */
class HeatmapBudgetTest {
    private val mapActivities = 300
    private val mapPoints = 500

    private fun allocatedBytes(): Long {
        val bean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        return bean.getThreadAllocatedBytes(Thread.currentThread().id)
    }

    @Test
    fun theHeatmapOfFiveYearsStaysWithinATimeAndMemoryBudget() {
        val activities = DemoData.generate(LocalDate.of(2026, 11, 18), days = 5 * 365).activities
        assertTrue(activities.size > 1_000, "five years should hold more than a thousand activities, got ${activities.size}")
        val tracks = activities.asReversed().asSequence()
            .filter { it.distanceM > 0 }
            .take(mapActivities)
            .map { it.track }
            .map { track ->
                val positioned = track.filter { it.hasPosition }
                val stride = (positioned.size / mapPoints).coerceAtLeast(1)
                positioned.filterIndexed { i, _ -> i % stride == 0 || i == positioned.lastIndex }
            }
            .toList()
        assertTrue(tracks.sumOf { it.size } <= mapActivities * (mapPoints + 1) * 2, "the thinning keeps the positions bounded")

        val bytesBefore = allocatedBytes()
        val started = System.nanoTime()
        val grid = Heatmap.build(tracks)
        val ms = (System.nanoTime() - started) / 1_000_000
        val megabytes = (allocatedBytes() - bytesBefore) / 1_048_576
        println("PERF heatmap: ${tracks.sumOf { it.size }} positions in $ms ms, $megabytes MB allocated")

        assertNotNull(grid)
        assertTrue(ms < 3_000, "the heatmap took $ms ms")
        assertTrue(megabytes < 100, "the heatmap allocated $megabytes MB")
    }
}
