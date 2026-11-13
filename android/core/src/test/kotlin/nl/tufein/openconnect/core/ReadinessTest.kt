package nl.tufein.openconnect.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ReadinessTest {
    private val day = LocalDate.parse("2026-05-10")

    private fun today(
        bodyBattery: Int? = null,
        hrv: Int? = null,
        restingHr: Int? = null,
        stress: Int? = null,
        date: LocalDate = day,
    ) = DailySummary(date = date, bodyBatteryHigh = bodyBattery, hrvMs = hrv, restingHr = restingHr, avgStress = stress)

    /** Six earlier days with a steady baseline, so HRV 50 and resting heart rate 52 are "normal". */
    private fun history(hrv: Int = 50, restingHr: Int = 52, days: Int = 6) = (1..days).map {
        DailySummary(date = day.minusDays(it.toLong()), hrvMs = hrv, restingHr = restingHr)
    }

    private fun sleep(score: Int? = null, hours: Double = 7.0, date: LocalDate = day) = TestData.sleep(date, hours, score)

    @Test
    fun allFiveSignalsAreWeighted() {
        val readiness = ReadinessCalculator.compute(
            today(bodyBattery = 90, hrv = 60, restingHr = 50, stress = 30),
            sleep(score = 80),
            history(),
        )
        assertNotNull(readiness)
        val parts = readiness.components.associate { it.part to it.score }
        assertEquals(80, parts[ReadinessPart.SLEEP])
        assertEquals(90, parts[ReadinessPart.BODY_BATTERY])
        assertEquals(100, parts[ReadinessPart.HRV]) // 20% above the baseline
        assertEquals(100, parts[ReadinessPart.RESTING_HR]) // below the baseline
        assertEquals(63, parts[ReadinessPart.STRESS]) // 100 - 30 * 1.25 = 62.5
        // (80*35 + 90*25 + 100*15 + 100*15 + 63*10) / 100 = 86.8
        assertEquals(87, readiness.score)
        assertEquals(ReadinessLevel.EXCELLENT, readiness.level)
    }

    @Test
    fun missingSignalsDropOutAndTheRestCountForMore() {
        // Sleep 8 h with no score is a full 100, Body Battery 50: (100*35 + 50*25) / 60
        val readiness = ReadinessCalculator.compute(today(bodyBattery = 50), sleep(hours = 8.0), emptyList())
        assertNotNull(readiness)
        assertEquals(listOf(ReadinessPart.SLEEP, ReadinessPart.BODY_BATTERY), readiness.components.map { it.part })
        assertEquals(79, readiness.score)
        assertEquals(ReadinessLevel.GOOD, readiness.level)
    }

    @Test
    fun oneSignalIsNotEnoughToSayAnything() {
        assertNull(ReadinessCalculator.compute(today(bodyBattery = 80), null, emptyList()))
        assertNull(ReadinessCalculator.compute(null, sleep(score = 90), emptyList()))
        assertNull(ReadinessCalculator.compute(null, null, emptyList()))
        assertNull(ReadinessCalculator.compute(today(), sleep(), history()), "a day with only a short night and no readings")
    }

    @Test
    fun sleepWithoutScoreIsJudgedOnHours() {
        fun sleepPart(hours: Double) = ReadinessCalculator.compute(today(bodyBattery = 70), sleep(hours = hours), emptyList())!!
            .components.first { it.part == ReadinessPart.SLEEP }.score

        assertEquals(100, sleepPart(8.0))
        assertEquals(100, sleepPart(9.5)) // more than enough does not score higher
        assertEquals(75, sleepPart(6.0))
        assertEquals(50, sleepPart(4.0))
    }

    @Test
    fun aNightWithNoSleepRecordedIsIgnored() {
        val none = SleepSummary(date = day, durationS = 0, score = null)
        assertNull(ReadinessCalculator.compute(today(bodyBattery = 70), none, emptyList()))
    }

    @Test
    fun hrvIsComparedWithYourOwnBaseline() {
        fun hrvPart(todayHrv: Int) = ReadinessCalculator.compute(today(bodyBattery = 70, hrv = todayHrv), sleep(score = 70), history(hrv = 50))!!
            .components.firstOrNull { it.part == ReadinessPart.HRV }?.score

        assertEquals(80, hrvPart(50)) // exactly normal
        assertEquals(40, hrvPart(40)) // 20% below
        assertEquals(0, hrvPart(20)) // far below, never negative
        assertEquals(100, hrvPart(75)) // far above, never over 100
    }

    @Test
    fun restingHeartRateAboveNormalLowersTheScore() {
        fun part(todayHr: Int) = ReadinessCalculator.compute(today(bodyBattery = 70, restingHr = todayHr), sleep(score = 70), history(restingHr = 52))!!
            .components.first { it.part == ReadinessPart.RESTING_HR }.score

        assertEquals(90, part(52))
        assertEquals(60, part(55))
        assertEquals(40, part(57))
        assertEquals(0, part(80))
        assertEquals(100, part(50))
    }

    @Test
    fun aBaselineNeedsFiveEarlierDaysAndNeverUsesTodayOrTheFuture() {
        val thin = history(days = 4)
        val withThin = ReadinessCalculator.compute(today(bodyBattery = 70, hrv = 40, restingHr = 60), sleep(score = 70), thin)!!
        assertEquals(setOf(ReadinessPart.SLEEP, ReadinessPart.BODY_BATTERY), withThin.components.map { it.part }.toSet())

        // Today's own values and later days must not pull the baseline towards today.
        val polluted = history() + DailySummary(date = day, hrvMs = 10, restingHr = 90) + DailySummary(date = day.plusDays(1), hrvMs = 10, restingHr = 90)
        val clean = ReadinessCalculator.compute(today(bodyBattery = 70, hrv = 50, restingHr = 52), sleep(score = 70), history())!!
        val withNoise = ReadinessCalculator.compute(today(bodyBattery = 70, hrv = 50, restingHr = 52), sleep(score = 70), polluted)!!
        assertEquals(clean, withNoise)
    }

    @Test
    fun onlyTheLastFourWeeksFormTheBaseline() {
        val old = (29..40).map { DailySummary(date = day.minusDays(it.toLong()), hrvMs = 5, restingHr = 90) }
        val withOld = ReadinessCalculator.compute(today(bodyBattery = 70, hrv = 50, restingHr = 52), sleep(score = 70), history() + old)!!
        val without = ReadinessCalculator.compute(today(bodyBattery = 70, hrv = 50, restingHr = 52), sleep(score = 70), history())!!
        assertEquals(without, withOld)
    }

    @Test
    fun stressIsBetterWhenLow() {
        fun part(stress: Int) = ReadinessCalculator.compute(today(bodyBattery = 70, stress = stress), sleep(score = 70), emptyList())!!
            .components.first { it.part == ReadinessPart.STRESS }.score

        assertEquals(100, part(0))
        assertEquals(50, part(40))
        assertEquals(0, part(80))
        assertEquals(0, part(100)) // never negative
    }

    @Test
    fun levelsHaveSharpEdges() {
        assertEquals(ReadinessLevel.LOW, ReadinessLevel.of(39))
        assertEquals(ReadinessLevel.MODERATE, ReadinessLevel.of(40))
        assertEquals(ReadinessLevel.MODERATE, ReadinessLevel.of(69))
        assertEquals(ReadinessLevel.GOOD, ReadinessLevel.of(70))
        assertEquals(ReadinessLevel.GOOD, ReadinessLevel.of(84))
        assertEquals(ReadinessLevel.EXCELLENT, ReadinessLevel.of(85))
        assertEquals(ReadinessLevel.LOW, ReadinessLevel.of(0))
    }

    @Test
    fun theWeakestAndStrongestSignalExplainTheScore() {
        val readiness = ReadinessCalculator.compute(today(bodyBattery = 95, stress = 70), sleep(score = 40), emptyList())!!
        assertEquals(ReadinessPart.BODY_BATTERY, readiness.strongest.part)
        assertEquals(ReadinessPart.STRESS, readiness.weakest.part)
    }
}
