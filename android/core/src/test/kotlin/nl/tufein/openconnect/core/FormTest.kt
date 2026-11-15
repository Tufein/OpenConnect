package nl.tufein.openconnect.core

import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FormTest {
    private val today = LocalDate.parse("2026-06-30")

    /** One hour without heart rate: a load of 120 (60 minutes at the weight of 2). */
    private fun session(daysAgo: Long, minutes: Int = 60, id: String = "a-$daysAgo-$minutes") = Activity(
        id = id,
        startTime = today.minusDays(daysAgo).atTime(8, 0).toInstant(ZoneOffset.UTC),
        durationS = minutes * 60.0,
    )

    private fun everyDay(from: Long, to: Long) = (to..from).map { session(it) }

    @Test
    fun `nothing to show without activities or with less than two weeks of history`() {
        assertNull(FormCalculator.compute(emptyList(), today))
        assertNull(FormCalculator.compute(listOf(session(5)), today))
        assertNotNull(FormCalculator.compute(listOf(session(13)), today))
    }

    @Test
    fun `a steady load settles fitness and fatigue on that load and form on zero`() {
        val series = FormCalculator.compute(everyDay(from = 300, to = 0), today)!!
        val last = series.latest
        assertEquals(120.0, last.load, 1e-9)
        assertEquals(120.0, last.fitness, 3.0) // 42 days is slow, 300 days is plenty
        assertEquals(120.0, last.fatigue, 0.01)
        assertEquals(0.0, last.form, 3.0)
    }

    @Test
    fun `fitness follows the 42 day average and fatigue the 7 day average`() {
        // One session 13 days ago, then nothing: the first day is a load of 120 on a clean slate.
        val series = FormCalculator.compute(listOf(session(13)), today, shownDays = 14)!!
        val first = series.points.first()
        assertEquals(120.0 / 42, first.fitness, 1e-9)
        assertEquals(120.0 / 7, first.fatigue, 1e-9)
        val rest = 13
        assertEquals(120.0 / 42 * Math.pow(1 - 1.0 / 42, rest.toDouble()), series.latest.fitness, 1e-9)
        assertEquals(120.0 / 7 * Math.pow(1 - 1.0 / 7, rest.toDouble()), series.latest.fatigue, 1e-9)
    }

    @Test
    fun `resting after a hard block makes you fresher because fatigue falls faster than fitness`() {
        val block = everyDay(from = 60, to = 8) // seven days off at the end
        val atEndOfBlock = FormCalculator.compute(block, today.minusDays(8))!!.latest
        val afterRest = FormCalculator.compute(block, today)!!.latest
        assertTrue(afterRest.form > atEndOfBlock.form)
        assertTrue(afterRest.form > 0)
        assertTrue(afterRest.fatigue < afterRest.fitness)
    }

    @Test
    fun `a sudden big week drives form below zero`() {
        val easy = (60L downTo 8L step 2).map { session(it, minutes = 30) }
        val bigWeek = (7L downTo 0L).map { session(it, minutes = 120, id = "big-$it") }
        val latest = FormCalculator.compute(easy + bigWeek, today)!!.latest
        assertTrue(latest.form < 0, "form was ${latest.form}")
    }

    @Test
    fun `two activities on one day add up`() {
        val one = FormCalculator.compute(listOf(session(20), session(13)), today)!!.latest.fatigue
        val two = FormCalculator.compute(listOf(session(20), session(13), session(13, id = "second")), today)!!.latest.fatigue
        assertTrue(two > one)
    }

    @Test
    fun `days without activities count as zero load and the series ends today`() {
        val series = FormCalculator.compute(listOf(session(20)), today, shownDays = 30)!!
        assertEquals(21, series.points.size) // 20 days ago to today, never before the first activity
        assertEquals(today, series.latest.date)
        assertEquals(0.0, series.latest.load, 1e-9)
    }

    @Test
    fun `only the last days asked for are kept and activities in the future are ignored`() {
        val series = FormCalculator.compute(everyDay(from = 200, to = 0) + session(-3, id = "future"), today, shownDays = 90)!!
        assertEquals(90, series.points.size)
        assertEquals(today, series.points.last().date)
        assertEquals(today.minusDays(89), series.points.first().date)
        assertEquals(120.0, series.latest.load, 1e-9) // the future one did not land on today
    }

    @Test
    fun `heart rate sets the weight the same way the weekly load does`() {
        val hard = session(13).copy(avgHr = 175, maxHr = 190) // 175/190 = 92 percent: weight 4
        val first = FormCalculator.compute(listOf(hard), today, shownDays = 14)!!.points.first()
        assertEquals(60 * 4.0, first.load, 1e-9)
    }

    // status

    @Test
    fun `status follows form compared with fitness`() {
        assertEquals(FormStatus.FRESH, FormStatus.of(fitness = 100.0, form = 25.0))
        assertEquals(FormStatus.BALANCED, FormStatus.of(fitness = 100.0, form = 0.0))
        assertEquals(FormStatus.BALANCED, FormStatus.of(fitness = 100.0, form = -5.0))
        assertEquals(FormStatus.BUILDING, FormStatus.of(fitness = 100.0, form = -6.0))
        assertEquals(FormStatus.BUILDING, FormStatus.of(fitness = 100.0, form = -30.0))
        assertEquals(FormStatus.STRAINED, FormStatus.of(fitness = 100.0, form = -31.0))
    }

    @Test
    fun `with almost no fitness there is no status because the ratio would mean nothing`() {
        val series = FormCalculator.compute(listOf(session(13, minutes = 1)), today)!!
        assertNull(series.status)
        val settled = FormCalculator.compute(everyDay(from = 300, to = 0), today)!!
        assertEquals(FormStatus.BALANCED, settled.status)
        // Two months of daily training from nothing still carries fatigue: you are building.
        assertEquals(FormStatus.BUILDING, FormCalculator.compute(everyDay(from = 60, to = 0), today)!!.status)
    }
}
