package nl.tufein.openconnect.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GearTest {
    private val today = LocalDate.of(2026, 11, 14)
    private val now = Instant.parse("2026-11-14T10:00:00Z")

    private fun act(id: String, day: LocalDate, km: Double, sport: Sport = Sport.RUNNING) = Activity(
        id = id,
        sport = sport,
        startTime = day.atTime(8, 0).toInstant(ZoneOffset.UTC),
        distanceM = km * 1000.0,
    )

    private fun shoe(id: String, since: LocalDate? = null, until: LocalDate? = null, sport: Sport = Sport.RUNNING, start: Double = 0.0, limit: Int = 800) =
        Shoe(id, "Shoe $id", sport, since, until, start, limit)

    @Test
    fun theBookSurvivesItsOwnTextIncludingAwkwardNames() {
        val book = GearBook(
            listOf(
                Shoe("s1", "Speedcross | trail, 100% grip", Sport.HIKING, LocalDate.of(2026, 1, 2), LocalDate.of(2026, 5, 6), 12.5, 650),
                shoe("s2"),
            ),
            mapOf("act|1" to "s1", "act2" to Gear.NONE),
        )
        assertEquals(book, GearBook.decode(book.encode()))
        assertEquals(GearBook(), GearBook.decode(""))
    }

    @Test
    fun aLineThatMakesNoSenseIsSkippedAndTheRestIsKept() {
        val text = "S|s1|Good|running|||0.0|800\nS|s2|Bad km|running|||x|800\nS|s3|Bad date|running|2026-99-99||0.0|800\nnonsense\nO|a|s1\nO|only-two"
        val book = GearBook.decode(text)
        assertEquals(listOf("s1", "s3"), book.shoes.map { it.id })
        assertNull(book.shoes[1].since) // an unreadable date is "no date", not a crash
        assertEquals(mapOf("a" to "s1"), book.overrides)
    }

    @Test
    fun addingNeedsANameCleansItAndKeepsIdsUnique() {
        assertEquals(GearBook(), Gear.add(GearBook(), "   ", Sport.RUNNING, today, false, now))
        val one = Gear.add(GearBook(), " Pegasus |\n40 ", Sport.RUNNING, today, false, now)
        assertEquals("Pegasus  40", one.shoes.single().name) // the bar became a space, the line break went
        val two = Gear.add(one, "Kayano", Sport.RUNNING, today, true, now) // same second: the id moves on
        assertEquals(2, two.shoes.map { it.id }.distinct().size)
        assertEquals(today, two.shoes[0].since)
        assertNull(two.shoes[1].since)
        val long = Gear.cleanName("x".repeat(100))
        assertEquals(Gear.MAX_NAME, long.length)
    }

    @Test
    fun aNewestPairOfTheSportTakesOverFromItsStartDay() {
        val old = shoe("old")
        val new = shoe("new", since = LocalDate.of(2026, 10, 1))
        val book = GearBook(listOf(old, new))
        assertEquals("old", Gear.shoeFor(book, act("a", LocalDate.of(2026, 9, 30), 5.0))!!.id)
        assertEquals("new", Gear.shoeFor(book, act("b", LocalDate.of(2026, 10, 1), 5.0))!!.id)
        assertEquals("new", Gear.shoeFor(book, act("c", today, 5.0))!!.id)
    }

    @Test
    fun aRetiredPairKeepsItsPastAndStopsGettingNewActivities() {
        val a = shoe("a", until = LocalDate.of(2026, 10, 31))
        val b = shoe("b", since = LocalDate.of(2026, 11, 1))
        val book = GearBook(listOf(a, b))
        assertEquals("a", Gear.shoeFor(book, act("x", LocalDate.of(2026, 10, 31), 5.0))!!.id)
        assertEquals("b", Gear.shoeFor(book, act("y", LocalDate.of(2026, 11, 1), 5.0))!!.id)
        // Retired and nothing else to take over: later activities go on no shoes.
        val lonely = GearBook(listOf(a))
        assertNull(Gear.shoeFor(lonely, act("z", today, 5.0)))
    }

    @Test
    fun aPairIsOnlyForItsOwnSport() {
        val book = GearBook(listOf(shoe("run"), shoe("walk", sport = Sport.WALKING)))
        assertEquals("walk", Gear.shoeFor(book, act("w", today, 3.0, Sport.WALKING))!!.id)
        assertNull(Gear.shoeFor(book, act("s", today, 1.0, Sport.SWIMMING)))
    }

    @Test
    fun aHandMadeChoiceBeatsTheRuleAndNoneMeansNoShoes() {
        val book = GearBook(listOf(shoe("a"), shoe("b", since = LocalDate.of(2026, 1, 1))))
        val activity = act("x", today, 5.0)
        assertEquals("b", Gear.shoeFor(book, activity)!!.id)
        val onA = Gear.assign(book, "x", "a")
        assertEquals("a", Gear.shoeFor(onA, activity)!!.id)
        assertNull(Gear.shoeFor(Gear.assign(book, "x", Gear.NONE), activity))
        assertEquals("b", Gear.shoeFor(Gear.assign(onA, "x", null), activity)!!.id)
    }

    @Test
    fun aChoiceForARemovedPairFallsBackToTheRule() {
        val book = Gear.assign(GearBook(listOf(shoe("a"), shoe("b"))), "x", "b")
        val without = Gear.remove(book, "b")
        assertTrue(without.overrides.isEmpty())
        assertEquals("a", Gear.shoeFor(without, act("x", today, 5.0))!!.id)
        // A leftover pointing at nothing is ignored as well.
        assertEquals("a", Gear.shoeFor(GearBook(listOf(shoe("a")), mapOf("x" to "ghost")), act("x", today, 5.0))!!.id)
    }

    @Test
    fun theKilometresAreTheStartKilometresPlusEveryActivityOnThePair() {
        val book = GearBook(listOf(shoe("a", start = 100.0), shoe("b", since = LocalDate.of(2026, 11, 1))))
        val activities = listOf(
            act("1", LocalDate.of(2026, 10, 20), 10.0),
            act("2", LocalDate.of(2026, 10, 25), 5.5),
            act("3", LocalDate.of(2026, 11, 2), 8.0),
            act("4", LocalDate.of(2026, 11, 9), 12.0),
            act("5", LocalDate.of(2026, 11, 10), 3.0, Sport.SWIMMING),
        )
        val usage = Gear.usage(book, activities)
        assertEquals(listOf("a", "b"), usage.map { it.shoe.id })
        assertEquals(115.5, usage[0].km, 1e-9)
        assertEquals(2, usage[0].activities)
        assertEquals(LocalDate.of(2026, 10, 25), usage[0].last)
        assertEquals(20.0, usage[1].km, 1e-9)
        assertEquals(LocalDate.of(2026, 11, 9), usage[1].last)
    }

    @Test
    fun aPairWithoutActivitiesHasOnlyItsStartKilometresAndNoLastDay() {
        val usage = Gear.usage(GearBook(listOf(shoe("a", start = 42.0))), emptyList()).single()
        assertEquals(42.0, usage.km, 0.0)
        assertEquals(0, usage.activities)
        assertNull(usage.last)
    }

    @Test
    fun theStatusChangesAtEightyAndOneHundredPercent() {
        assertEquals(ShoeStatus.FRESH, Gear.status(639.9, 800))
        assertEquals(ShoeStatus.GETTING_OLD, Gear.status(640.0, 800))
        assertEquals(ShoeStatus.GETTING_OLD, Gear.status(799.9, 800))
        assertEquals(ShoeStatus.REPLACE, Gear.status(800.0, 800))
        assertEquals(ShoeStatus.REPLACE, Gear.status(1200.0, 800))
        assertEquals(0.5, ShoeUsage(shoe("a"), 400.0, 1, null, ShoeStatus.FRESH).fraction, 1e-9)
    }

    @Test
    fun onlyPairsInUseThatReachedTheirLimitAreDue() {
        val book = GearBook(
            listOf(
                shoe("worn", start = 790.0),
                shoe("retired", until = LocalDate.of(2026, 1, 1), start = 900.0),
                shoe("fine", sport = Sport.WALKING, start = 100.0),
            ),
        )
        val usage = Gear.usage(book, listOf(act("1", today, 10.0)))
        assertEquals(listOf("worn"), Gear.due(usage).map { it.shoe.id })
    }

    @Test
    fun theLimitMovesInStepsAndStaysInRange() {
        val book = GearBook(listOf(shoe("a")))
        assertEquals(850, Gear.moveLimit(book, "a", Gear.LIMIT_STEP_KM).shoes.single().retireKm)
        assertEquals(Gear.MIN_RETIRE_KM, Gear.moveLimit(book, "a", -5000).shoes.single().retireKm)
        assertEquals(Gear.MAX_RETIRE_KM, Gear.moveLimit(book, "a", 5000).shoes.single().retireKm)
        assertEquals(book, Gear.moveLimit(book, "nobody", 50))
    }

    @Test
    fun retiringAndResumingSetsAndClearsTheLastDay() {
        val book = GearBook(listOf(shoe("a")))
        val retired = Gear.retire(book, "a", today)
        assertEquals(today, retired.shoes.single().until)
        assertNull(Gear.resume(retired, "a").shoes.single().until)
    }

    @Test
    fun theChoicesAreThePairsOfTheActivitiesSport() {
        val book = GearBook(listOf(shoe("run"), shoe("run2"), shoe("walk", sport = Sport.WALKING)))
        assertEquals(listOf("run", "run2"), Gear.choices(book, act("x", today, 5.0)).map { it.id })
        assertTrue(Gear.choices(book, act("y", today, 5.0, Sport.SWIMMING)).isEmpty())
    }
}
