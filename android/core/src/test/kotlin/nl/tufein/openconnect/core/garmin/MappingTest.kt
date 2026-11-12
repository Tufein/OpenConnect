package nl.tufein.openconnect.core.garmin

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import nl.tufein.openconnect.core.DailySummary
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The JSON in resources/garmin has the layouts that Garmin's Personal API answered with on 2026-11-12 (observed with a
 * real account, field names and nesting only), filled with invented values. Garmin does not publish these layouts,
 * so a few tests also feed the mapping layouts that differ, to pin down that it then gives less instead of failing.
 */
class MappingTest {
    private val d10 = LocalDate.parse("2026-11-10")
    private val d11 = LocalDate.parse("2026-11-11")
    private val d12 = LocalDate.parse("2026-11-12")

    private fun fixture(name: String): JsonElement =
        Json.parseToJsonElement(checkNotNull(MappingTest::class.java.getResourceAsStream("/garmin/$name")) { "missing $name" }.readBytes().decodeToString())

    private fun patches(name: String) = DayLeaves.collect(fixture(name)).mapValues { DailyPatch.from(it.value) }

    @Test
    fun `the daily summary sections of a day all land on that day`() {
        val day = patches("dailysummaries.json").getValue(d10)
        assertEquals(8421, day.steps)
        assertEquals(6310.5, day.distanceM)
        assertEquals(13, day.floors)
        assertEquals(10_000, day.stepsGoal)
        assertEquals(410, day.activeKcal)
        assertEquals(52, day.restingHr)
        assertEquals(47, day.minHr)
        assertEquals(171, day.maxHr)
        assertEquals(25, day.moderateMin)
        assertEquals(10, day.vigorousMin)
        assertEquals(31, day.avgStress)
        assertEquals(88, day.maxStress)
        assertEquals(92, day.bodyBatteryHigh)
        assertEquals(18, day.bodyBatteryLow)
        assertEquals(96, day.spo2Avg)
        assertEquals(90, day.spo2Min)
        assertEquals(14.0, day.respiration)
        assertEquals(51, patches("dailysummaries.json").getValue(d11).restingHr)
    }

    @Test
    fun `a day with only some sections gives only those values`() {
        val day = patches("dailysummaries.json").getValue(d11)
        assertEquals(10_234, day.steps)
        assertEquals(9, day.floors)
        assertEquals(46, day.minHr)
        assertNull(day.avgStress)
        assertNull(day.bodyBatteryHigh)
        assertNull(day.spo2Avg)
        assertNull(day.respiration)
        assertFalse(day.isEmpty)
    }

    @Test
    fun `today with every counter at zero is not worth a row`() {
        val today = patches("dailysummaries.json").getValue(d12)
        assertEquals(0, today.floors)
        assertEquals(0, today.moderateMin)
        assertTrue(today.isEmpty)
    }

    @Test
    fun `the layout does not matter, sections that list days work as well as days that hold sections`() {
        val json = Json.parseToJsonElement(
            """{"movement":[{"calendarDate":"2026-11-10","steps":{"value":8421}},{"calendarDate":"2026-11-11","steps":{"value":10234}}],
               "heartRate":[{"calendarDate":"2026-11-10","restingValue":52}]}""",
        )
        val bySection = DayLeaves.collect(json).mapValues { DailyPatch.from(it.value) }
        assertEquals(8421, bySection.getValue(d10).steps)
        assertEquals(52, bySection.getValue(d10).restingHr) // from the other section's list
        assertEquals(10234, bySection.getValue(d11).steps)
    }

    @Test
    fun `step and stress lists are read, unmeasured values are not`() {
        val steps = patches("steps.json")
        assertEquals(10_000, steps.getValue(d10).stepsGoal)
        assertEquals(6310.0, steps.getValue(d10).distanceM)
        assertTrue(steps.getValue(d12).isEmpty, "a day with no steps and only a goal is not worth a row")

        val stress = patches("stress.json")
        assertEquals(32, stress.getValue(d10).avgStress) // nested under "values"
        assertNull(stress.getValue(d12).avgStress) // -1 means not measured
        assertTrue(stress.getValue(d12).isEmpty)
    }

    @Test
    fun `hrv summaries give last night's average`() {
        assertEquals(49, patches("hrv.json").getValue(d11).hrvMs)
    }

    @Test
    fun `a night is read from the sleep answer and its score comes along`() {
        val facts = DayLeaves.collect(fixture("sleep.json"))
        val night = assertNotNull(SleepMapping.night(d11, facts.getValue(d11)))
        assertEquals(27_000, night.durationS)
        assertEquals(5_400, night.deepS)
        assertEquals(15_000, night.lightS)
        assertEquals(6_600, night.remS)
        assertEquals(900, night.awakeS)
        assertEquals(84, night.score) // the overall score, not the 24 of the REM percentage next to it
        // a night with sleep time zero is not a night
        assertNull(SleepMapping.night(d10, facts.getValue(d10)))
    }

    @Test
    fun `sleep time falls back to the sum of the stages`() {
        val json = Json.parseToJsonElement("""{"calendarDate":"2026-11-11","deepSleepSeconds":3600,"lightSleepSeconds":7200,"remSleepSeconds":1800}""")
        val night = assertNotNull(SleepMapping.night(d11, DayLeaves.collect(json).getValue(d11)))
        assertEquals(12_600, night.durationS)
        assertNull(night.score)
    }

    @Test
    fun `body battery gives the day's high and low and skips unmeasured moments`() {
        val result = DayLeaves.bodyBattery(fixture("bodybattery.json"))
        assertEquals(92 to 18, result.getValue(d10)) // the null reading is ignored
        assertEquals(88 to 40, result.getValue(d11))
    }

    @Test
    fun `body battery uses the position the day names for the level, and guesses without it`() {
        val named = Json.parseToJsonElement(
            """[{"date":"2026-11-10","bodyBatteryValuesArray":[[1762761600000,"MEASURED",35,1.0],[1762765200000,"MEASURED",60,1.0]],
               "bodyBatteryValueDescriptorDTOList":[{"bodyBatteryValueDescriptorIndex":0,"bodyBatteryValueDescriptorKey":"timestamp"},
               {"bodyBatteryValueDescriptorIndex":2,"bodyBatteryValueDescriptorKey":"bodyBatteryLevel"}]}]""",
        )
        assertEquals(60 to 35, DayLeaves.bodyBattery(named).getValue(d10)) // the 1.0 at index 3 is not a level
        val unnamed = Json.parseToJsonElement("""[{"date":"2026-11-10","bodyBatteryValuesArray":[[1762761600000,"MEASURED",40,1.0],[1762765200000,"MEASURED",88,1.0]]}]""")
        assertEquals(88 to 40, DayLeaves.bodyBattery(unnamed).getValue(d10)) // first number from 0 to 100 after the time
        val outOfRange = Json.parseToJsonElement("""[{"date":"2026-11-10","bodyBatteryValuesArray":[[1,150],[2,null]]}]""")
        assertTrue(DayLeaves.bodyBattery(outOfRange).isEmpty())
    }

    @Test
    fun `answers without dates give nothing`() {
        assertTrue(DayLeaves.collect(Json.parseToJsonElement("""{"foo":[1,2,3],"bar":{"baz":"x"}}""")).isEmpty())
        assertTrue(DayLeaves.collect(null).isEmpty())
        assertTrue(DayLeaves.bodyBattery(Json.parseToJsonElement("""[{"x":1}]""")).isEmpty())
    }

    @Test
    fun `two patches for one day keep the larger counter and the first known value`() {
        val a = DailyPatch(steps = 8000, restingHr = 52, hrvMs = null)
        val b = DailyPatch(steps = 9000, restingHr = 50, hrvMs = 47)
        val merged = a.and(b)
        assertEquals(9000, merged.steps)
        assertEquals(52, merged.restingHr)
        assertEquals(47, merged.hrvMs)
    }

    @Test
    fun `a patch never makes stored numbers smaller`() {
        val existing = DailySummary(d10, steps = 12_000, distanceM = 9000.0, restingHr = 55, hrvMs = 40, stepsGoal = 8000)
        val applied = DailyPatch(steps = 8421, distanceM = 6310.0, restingHr = 52, hrvMs = 49, stepsGoal = 10_000).applyTo(d10, existing)
        assertEquals(12_000, applied.steps)
        assertEquals(9000.0, applied.distanceM)
        assertEquals(52, applied.restingHr) // newer value wins for readings
        assertEquals(49, applied.hrvMs)
        assertEquals(10_000, applied.stepsGoal)
        assertEquals(55, DailyPatch(steps = 1).applyTo(d10, existing).restingHr) // not in the patch: kept
    }

    @Test
    fun `json shape shows the layout and no values`() {
        val shape = JsonShape.describe(fixture("sleep.json"))
        assertTrue("sleepTimeSeconds: number" in shape && "calendarDate: string" in shape && "overall: {value: number" in shape && shape.endsWith(" x 2"), shape)
        assertFalse("27000" in shape || "2026-11-11" in shape || "GOOD" in shape, shape)
        assertEquals("number x 3", JsonShape.describe(Json.parseToJsonElement("[1,2,3]")))
        assertEquals("empty", JsonShape.describe(null))
        assertEquals("[]", JsonShape.describe(Json.parseToJsonElement("[]")))
    }

    @Test
    fun `activity type keys map to sports`() {
        assertEquals(nl.tufein.openconnect.core.Sport.RUNNING, garminSport("trail_running"))
        assertEquals(nl.tufein.openconnect.core.Sport.RUNNING, garminSport("treadmill_running"))
        assertEquals(nl.tufein.openconnect.core.Sport.CYCLING, garminSport("indoor_cycling"))
        assertEquals(nl.tufein.openconnect.core.Sport.CYCLING, garminSport("mountain_biking"))
        assertEquals(nl.tufein.openconnect.core.Sport.SWIMMING, garminSport("lap_swimming"))
        assertEquals(nl.tufein.openconnect.core.Sport.WALKING, garminSport("walking"))
        assertEquals(nl.tufein.openconnect.core.Sport.STRENGTH, garminSport("strength_training"))
        assertEquals(nl.tufein.openconnect.core.Sport.OTHER, garminSport("kitesurfing"))
        assertEquals(nl.tufein.openconnect.core.Sport.OTHER, garminSport(null))
    }
}
