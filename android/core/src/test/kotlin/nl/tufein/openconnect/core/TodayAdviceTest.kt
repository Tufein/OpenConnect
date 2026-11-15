package nl.tufein.openconnect.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TodayAdviceTest {
    private fun readiness(level: ReadinessLevel) = Readiness(
        score = when (level) {
            ReadinessLevel.LOW -> 20
            ReadinessLevel.MODERATE -> 55
            ReadinessLevel.GOOD -> 75
            ReadinessLevel.EXCELLENT -> 90
        },
        level = level,
        components = emptyList(),
    )

    @Test
    fun `nothing known means no advice`() {
        assertNull(TodayAdviceCalculator.compute(null, null))
    }

    @Test
    fun `the weakest of readiness and form decides`() {
        val cases = listOf(
            Triple(ReadinessLevel.LOW, FormStatus.FRESH, AdviceLevel.REST),
            Triple(ReadinessLevel.EXCELLENT, FormStatus.STRAINED, AdviceLevel.REST),
            Triple(ReadinessLevel.MODERATE, FormStatus.FRESH, AdviceLevel.EASY),
            Triple(ReadinessLevel.GOOD, FormStatus.BUILDING, AdviceLevel.NORMAL),
            Triple(ReadinessLevel.GOOD, FormStatus.FRESH, AdviceLevel.NORMAL),
            Triple(ReadinessLevel.EXCELLENT, FormStatus.BALANCED, AdviceLevel.NORMAL),
            Triple(ReadinessLevel.EXCELLENT, FormStatus.FRESH, AdviceLevel.PUSH),
        )
        for ((r, f, expected) in cases) {
            assertEquals(expected, TodayAdviceCalculator.compute(readiness(r), f)!!.level, "$r with $f")
        }
    }

    @Test
    fun `either signal alone is enough`() {
        assertEquals(AdviceLevel.EASY, TodayAdviceCalculator.compute(readiness(ReadinessLevel.MODERATE), null)!!.level)
        assertEquals(AdviceLevel.REST, TodayAdviceCalculator.compute(null, FormStatus.STRAINED)!!.level)
        assertEquals(AdviceLevel.PUSH, TodayAdviceCalculator.compute(null, FormStatus.FRESH)!!.level)
    }

    @Test
    fun `it says what holds you back`() {
        assertEquals(AdviceLimit.READINESS, TodayAdviceCalculator.compute(readiness(ReadinessLevel.LOW), FormStatus.FRESH)!!.limitedBy)
        assertEquals(AdviceLimit.FORM, TodayAdviceCalculator.compute(readiness(ReadinessLevel.EXCELLENT), FormStatus.STRAINED)!!.limitedBy)
        assertEquals(AdviceLimit.BOTH, TodayAdviceCalculator.compute(readiness(ReadinessLevel.LOW), FormStatus.STRAINED)!!.limitedBy)
        assertEquals(AdviceLimit.BOTH, TodayAdviceCalculator.compute(readiness(ReadinessLevel.GOOD), FormStatus.BUILDING)!!.limitedBy)
        assertEquals(AdviceLimit.NONE, TodayAdviceCalculator.compute(readiness(ReadinessLevel.EXCELLENT), FormStatus.FRESH)!!.limitedBy)
        assertEquals(AdviceLimit.READINESS, TodayAdviceCalculator.compute(readiness(ReadinessLevel.GOOD), null)!!.limitedBy)
    }
}
