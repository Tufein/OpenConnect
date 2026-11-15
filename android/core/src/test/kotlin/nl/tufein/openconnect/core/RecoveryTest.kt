package nl.tufein.openconnect.core

import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecoveryTest {
    private val today = LocalDate.parse("2026-06-30")

    /** A session without heart rate: a load of 2 per minute. */
    private fun session(daysAgo: Long, load: Int, id: String = "s-$daysAgo-$load") = Activity(
        id = id,
        startTime = today.minusDays(daysAgo).atTime(8, 0).toInstant(ZoneOffset.UTC),
        durationS = load / 2.0 * 60,
    )

    /** A week of loads, oldest first, ending today, after a month of easy base so the history rule is met. */
    private fun week(vararg loads: Int, base: Boolean = true): List<Activity> {
        val days = loads.toList().mapIndexedNotNull { i, l -> if (l > 0) session((loads.size - 1 - i).toLong(), l) else null }
        val old = if (base) listOf(session(40, 60, id = "old")) else emptyList()
        return old + days
    }

    // monotony and strain

    @Test
    fun `no load or not enough history gives nothing`() {
        assertNull(MonotonyCalculator.compute(emptyList(), today))
        assertNull(MonotonyCalculator.compute(week(100, 100, 100, 100, 100, 100, 100, base = false), today)) // only 7 days of history
        assertNull(MonotonyCalculator.compute(listOf(session(30, 60)), today)) // nothing in the last week
    }

    @Test
    fun `the same load every day is as monotonous as it gets`() {
        val r = MonotonyCalculator.compute(week(120, 120, 120, 120, 120, 120, 120), today)!!
        assertEquals(MonotonyCalculator.MAX_MONOTONY, r.monotony, 1e-9)
        assertEquals(840.0, r.weekLoad, 1e-9)
        assertEquals(840.0 * MonotonyCalculator.MAX_MONOTONY, r.strain, 1e-9)
        assertEquals(MonotonyVerdict.MONOTONOUS, r.verdict)
        assertEquals(0, r.restDays)
    }

    @Test
    fun `hard and easy days with rest in between are varied`() {
        val r = MonotonyCalculator.compute(week(200, 0, 100, 0, 200, 0, 100), today)!!
        assertEquals(1.029, r.monotony, 0.001)
        assertEquals(MonotonyVerdict.VARIED, r.verdict)
        assertEquals(3, r.restDays)
        assertEquals(600.0, r.weekLoad, 1e-9)
        assertEquals(600.0 * r.monotony, r.strain, 1e-9)
    }

    @Test
    fun `verdicts follow the monotony`() {
        assertEquals(MonotonyVerdict.UNIFORM, MonotonyCalculator.compute(week(120, 120, 120, 120, 60, 60, 0), today)!!.verdict)
        assertEquals(MonotonyVerdict.MONOTONOUS, MonotonyCalculator.compute(week(100, 100, 100, 100, 100, 100, 0), today)!!.verdict)
        assertEquals(MonotonyVerdict.VARIED, MonotonyCalculator.compute(week(40, 0, 160, 0, 80, 0, 120), today)!!.verdict)
    }

    @Test
    fun `the usual strain is the average of the four weeks before`() {
        val twoMonths = (0 until 5).flatMap { w ->
            // five weeks, each: 200, 0, 100, 0, 200, 0, 100 ending 7 * w days ago
            listOf(200, 0, 100, 0, 200, 0, 100).mapIndexedNotNull { i, l ->
                if (l > 0) session(7L * w + (6 - i), l, id = "w$w-$i") else null
            }
        }
        val r = MonotonyCalculator.compute(twoMonths, today)!!
        assertNotNull(r.usualStrain)
        assertEquals(r.strain, r.usualStrain!!, 1e-6) // identical weeks
        assertNull(MonotonyCalculator.compute(week(200, 0, 100, 0, 200, 0, 100), today)!!.usualStrain)
    }

    // sleep need

    private fun night(daysAgo: Long, hours: Double) = SleepSummary(date = today.minusDays(daysAgo), durationS = (hours * 3600).toInt())

    private fun nights(vararg hours: Double): List<SleepSummary> = hours.mapIndexed { i, h -> night((hours.size - 1 - i).toLong(), h) }

    @Test
    fun `fewer than four nights say too little`() {
        assertNull(SleepNeedCalculator.compute(nights(7.0, 7.0, 7.0), 0.0, today))
        assertNull(SleepNeedCalculator.compute(nights(7.0, 0.0, 7.0, 0.0, 7.0), 0.0, today)) // empty nights do not count
        assertNotNull(SleepNeedCalculator.compute(nights(7.0, 7.0, 7.0, 7.0), 0.0, today))
    }

    @Test
    fun `enough sleep means no debt and the base need`() {
        val n = SleepNeedCalculator.compute(nights(8.0, 8.0, 8.0, 8.0, 8.0, 8.0, 8.0), 0.0, today)!!
        assertEquals(0.0, n.debtHours, 1e-9)
        assertEquals(SleepDebtLevel.NONE, n.level)
        assertEquals(SleepNeedCalculator.BASE_HOURS, n.needTonightHours, 1e-9)
        assertEquals(8.0, n.lastNightHours!!, 1e-9)
    }

    @Test
    fun `a week of seven hours is a debt of seven hours and you get a quarter of it back each night, up to an hour`() {
        val n = SleepNeedCalculator.compute(nights(7.0, 7.0, 7.0, 7.0, 7.0, 7.0, 7.0), 0.0, today)!!
        assertEquals(7.0, n.debtHours, 1e-9)
        assertEquals(SleepDebtLevel.BIG, n.level)
        assertEquals(9.0, n.needTonightHours, 1e-9) // 8 + min(1, 7 / 4)
        val small = SleepNeedCalculator.compute(nights(8.0, 8.0, 8.0, 8.0, 7.0, 7.0, 7.0), 0.0, today)!!
        assertEquals(3.0, small.debtHours, 1e-9)
        assertEquals(SleepDebtLevel.SMALL, small.level)
        assertEquals(8.75, small.needTonightHours, 1e-9)
    }

    @Test
    fun `a long night makes up for a short one but never counts as a negative debt`() {
        val n = SleepNeedCalculator.compute(nights(9.0, 9.0, 9.0, 7.0, 7.0, 7.0, 7.0), 0.0, today)!!
        assertEquals(1.0, n.debtHours, 1e-9)
        assertEquals(0.0, SleepNeedCalculator.compute(nights(10.0, 10.0, 10.0, 10.0), 0.0, today)!!.debtHours, 1e-9)
    }

    @Test
    fun `a hard day asks for more sleep`() {
        val base = nights(8.0, 8.0, 8.0, 8.0)
        assertEquals(8.0, SleepNeedCalculator.compute(base, 149.0, today)!!.needTonightHours, 1e-9)
        assertEquals(8.25, SleepNeedCalculator.compute(base, 150.0, today)!!.needTonightHours, 1e-9)
        assertEquals(8.5, SleepNeedCalculator.compute(base, 300.0, today)!!.needTonightHours, 1e-9)
    }

    @Test
    fun `only the last seven nights count`() {
        val old = (10L..14L).map { night(it, 3.0) }
        val n = SleepNeedCalculator.compute(old + nights(8.0, 8.0, 8.0, 8.0), 0.0, today)!!
        assertEquals(0.0, n.debtHours, 1e-9)
        assertEquals(4, n.nights)
    }

    @Test
    fun `the load of a day adds up the sessions of that day`() {
        val a = session(0, 100, id = "a")
        val b = session(0, 60, id = "b")
        assertEquals(160.0, Recovery.dayLoad(listOf(a, b, session(1, 500)), today), 1e-9)
        assertTrue(Recovery.dayLoad(emptyList(), today) == 0.0)
    }
}
