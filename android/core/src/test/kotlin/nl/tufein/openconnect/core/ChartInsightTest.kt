package nl.tufein.openconnect.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ChartInsightTest {
    private val start = LocalDate.parse("2026-06-01")
    private fun days(vararg v: Double?) = v.mapIndexed { i, x -> start.plusDays(i.toLong()) to x }

    @Test
    fun `no readings gives no insight`() {
        assertNull(ChartInsight.of(ChartMetric.STEPS, days(null, null)))
        assertNull(ChartInsight.of(ChartMetric.STEPS, emptyList()))
    }

    @Test
    fun `summary has average, extremes with their dates and the latest value`() {
        val i = assertNotNull(ChartInsight.of(ChartMetric.STEPS, days(4000.0, null, 12000.0, 8000.0)))
        assertEquals(3, i.daysWithData)
        assertEquals(8000.0, i.average, 1e-9)
        assertEquals(4000.0, i.lowest)
        assertEquals(start, i.lowestDate)
        assertEquals(12000.0, i.highest)
        assertEquals(start.plusDays(2), i.highestDate)
        assertEquals(8000.0, i.latest)
    }

    @Test
    fun `steps are judged on the average against 7000 and 10000`() {
        fun verdict(v: Double) = ChartInsight.of(ChartMetric.STEPS, days(v, v, v))!!.verdict
        assertEquals(Verdict.GOOD, verdict(10_000.0))
        assertEquals(Verdict.FAIR, verdict(7_000.0))
        assertEquals(Verdict.WATCH, verdict(6_999.0))
    }

    @Test
    fun `sleep is good between 7 and 9 hours, fair a little outside and worth watching under 6`() {
        fun verdict(v: Double) = ChartInsight.of(ChartMetric.SLEEP, days(v, v, v))!!.verdict
        assertEquals(Verdict.GOOD, verdict(7.5))
        assertEquals(Verdict.FAIR, verdict(6.5))
        assertEquals(Verdict.FAIR, verdict(9.5))
        assertEquals(Verdict.WATCH, verdict(5.5))
    }

    @Test
    fun `lower is better for resting heart rate and stress`() {
        assertEquals(Verdict.GOOD, ChartInsight.of(ChartMetric.RESTING_HR, days(55.0, 56.0, 54.0))!!.verdict)
        assertEquals(Verdict.WATCH, ChartInsight.of(ChartMetric.RESTING_HR, days(85.0, 86.0, 84.0))!!.verdict)
        assertEquals(Verdict.GOOD, ChartInsight.of(ChartMetric.STRESS, days(20.0, 22.0, 18.0))!!.verdict)
        assertEquals(Verdict.WATCH, ChartInsight.of(ChartMetric.STRESS, days(70.0, 72.0, 68.0))!!.verdict)
    }

    @Test
    fun `body battery, spo2 and respiration have their ranges`() {
        assertEquals(Verdict.GOOD, ChartInsight.of(ChartMetric.BODY_BATTERY, days(80.0, 90.0, 85.0))!!.verdict)
        assertEquals(Verdict.WATCH, ChartInsight.of(ChartMetric.BODY_BATTERY, days(30.0, 40.0, 35.0))!!.verdict)
        assertEquals(Verdict.GOOD, ChartInsight.of(ChartMetric.SPO2, days(96.0, 97.0, 96.0))!!.verdict)
        assertEquals(Verdict.WATCH, ChartInsight.of(ChartMetric.SPO2, days(88.0, 89.0, 88.0))!!.verdict)
        assertEquals(Verdict.GOOD, ChartInsight.of(ChartMetric.RESPIRATION, days(14.0, 15.0, 14.0))!!.verdict)
        assertEquals(Verdict.WATCH, ChartInsight.of(ChartMetric.RESPIRATION, days(30.0, 31.0, 30.0))!!.verdict)
    }

    @Test
    fun `hrv has no universal range, so it is judged against your own average`() {
        val steady = ChartInsight.of(ChartMetric.HRV, days(50.0, 52.0, 51.0, 50.0))!!
        assertEquals(Verdict.GOOD, steady.verdict)
        val dropped = ChartInsight.of(ChartMetric.HRV, days(60.0, 62.0, 61.0, 40.0))!!
        assertEquals(Verdict.WATCH, dropped.verdict)
    }

    @Test
    fun `trend compares the last 7 readings with the 7 before and ignores small changes`() {
        val rising = ChartInsight.of(ChartMetric.STEPS, days(*(List(7) { 5000.0 } + List(7) { 7000.0 }).toTypedArray()))!!
        assertEquals(Trend.UP, rising.trend)
        assertEquals(40.0, rising.changePct!!, 1e-9)
        assertEquals(true, rising.improving)

        val flat = ChartInsight.of(ChartMetric.STEPS, days(*(List(7) { 5000.0 } + List(7) { 5100.0 }).toTypedArray()))!!
        assertEquals(Trend.STABLE, flat.trend)

        val falling = ChartInsight.of(ChartMetric.STEPS, days(*(List(7) { 8000.0 } + List(7) { 4000.0 }).toTypedArray()))!!
        assertEquals(Trend.DOWN, falling.trend)
        assertEquals(false, falling.improving)
    }

    @Test
    fun `a falling resting heart rate counts as improving`() {
        val i = ChartInsight.of(ChartMetric.RESTING_HR, days(*(List(7) { 62.0 } + List(7) { 56.0 }).toTypedArray()))!!
        assertEquals(Trend.DOWN, i.trend)
        assertEquals(true, i.improving)
    }

    @Test
    fun `too little data gives no trend`() {
        val i = ChartInsight.of(ChartMetric.STEPS, days(5000.0, 6000.0, 7000.0, 8000.0))!!
        assertNull(i.trend)
        assertNull(i.improving)
    }

    @Test
    fun `steps report how many days reached the 10000 goal`() {
        val i = ChartInsight.of(ChartMetric.STEPS, days(12000.0, 9000.0, 10000.0, null))!!
        assertEquals(2, i.goalDays)
        assertNull(ChartInsight.of(ChartMetric.STRESS, days(10.0, 20.0, 30.0))!!.goalDays)
    }

    @Test
    fun `readiness and sleep score are judged like their own levels`() {
        fun readiness(v: Double) = ChartInsight.of(ChartMetric.READINESS, days(v, v, v))!!.verdict
        assertEquals(Verdict.GOOD, readiness(75.0))
        assertEquals(Verdict.FAIR, readiness(55.0))
        assertEquals(Verdict.WATCH, readiness(30.0))
        fun sleepScore(v: Double) = ChartInsight.of(ChartMetric.SLEEP_SCORE, days(v, v, v))!!.verdict
        assertEquals(Verdict.GOOD, sleepScore(85.0))
        assertEquals(Verdict.FAIR, sleepScore(70.0))
        assertEquals(Verdict.WATCH, sleepScore(50.0))
    }
}
