package nl.tufein.openconnect.core

import nl.tufein.openconnect.core.hume.HumeDemo
import nl.tufein.openconnect.core.oura.OuraDemo
import nl.tufein.openconnect.core.whoop.WhoopDemo
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.LocalDate
import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * v0.56.0 (milestone M3): a backup used to hold the database only, so a new phone lost the journal, the shoes, the
 * training plan, the race and the goals. They are in the file now, and restoring them never overwrites what the person
 * already set on this phone.
 */
class UserDataBackupTest {
    private val now = Instant.parse("2026-11-18T12:00:00Z")
    private val day = LocalDate.parse("2026-06-10")

    private fun book(vararg days: Pair<String, List<JournalTag>>) =
        JournalBook(days.associate { (d, tags) -> LocalDate.parse(d) to tags.toSet() }).encode()

    private fun shoe(id: String, name: String, km: Double = 0.0) = Shoe(id, name, Sport.RUNNING, since = day, startKm = km)

    private val everything = UserData(
        journal = book("2026-06-01" to listOf(JournalTag.entries[0], JournalTag.entries[1])),
        gear = GearBook(listOf(shoe("s1", "Pegasus | 40")), mapOf("a1" to "s1")).encode(),
        trainingPlan = "{\"plan\":true}",
        raceGoal = "2026-10-11|half|6000",
        maxHr = 188,
        weeklyGoals = WeeklyGoals(intensityMinutes = 200, distanceKm = 30, activities = 4),
    )

    private fun roundTrip(data: UserData, current: UserData = UserData()): UserData {
        val bytes = Backup.write(InMemoryRepository(), now, data)
        return Backup.restore(bytes, InMemoryRepository(), current).userData
    }

    @Test
    fun `the journal, shoes, plan, race, max heart rate and goals come back`() {
        assertEquals(everything, roundTrip(everything))
    }

    @Test
    fun `work-outs made in the app come back`() {
        // v1.17.0: the work-outs (v1.6.0) were in UserData and merged on restore, but the file never held them, so a new
        // phone lost every work-out that was made in the app.
        val workouts = WorkoutBook(listOf(OwnWorkout("w1", "Tempo | 3x10"), OwnWorkout("w2", "Lang rustig"))).encode()
        assertEquals(workouts, roundTrip(everything.copy(workouts = workouts)).workouts)
        val mine = WorkoutBook(listOf(OwnWorkout("w3", "Heuvels"))).encode()
        val merged = WorkoutBook.decode(roundTrip(UserData(workouts = workouts), UserData(workouts = mine)).workouts)
        assertEquals(listOf("w3", "w1", "w2"), merged.workouts.map { it.id }, "this phone's own work-outs stay, the backup's are added")
    }

    @Test
    fun `the hi-score initials come back, and this phone's own initials win`() {
        assertEquals("SVB", roundTrip(everything.copy(initials = "SVB")).initials)
        assertEquals("ABC", roundTrip(UserData(initials = "SVB"), UserData(initials = "ABC")).initials)
        assertEquals("", roundTrip(UserData()).initials)
    }

    @Test
    fun `the days of the Oura Ring, WHOOP and Hume Band come back as well as the watch`() {
        val source = InMemoryRepository()
        OuraDemo.days(day, 5).forEach(source::upsertOuraDay)
        WhoopDemo.days(day, 5).forEach(source::upsertWhoopDay)
        HumeDemo.days(day, 5).forEach(source::upsertHumeDay)
        val target = InMemoryRepository()
        Backup.restore(Backup.write(source, now), target)
        val from = LocalDate.of(2000, 1, 1)
        val to = LocalDate.of(2100, 1, 1)
        assertEquals(5, target.listOuraDays(from, to).size)
        assertEquals(source.listOuraDays(from, to), target.listOuraDays(from, to))
        assertEquals(source.listWhoopDays(from, to), target.listWhoopDays(from, to))
        assertEquals(source.listHumeDays(from, to), target.listHumeDays(from, to))
    }

    @Test
    fun `journal days from the backup are added to the days already on the phone`() {
        val current = UserData(journal = book("2026-06-01" to listOf(JournalTag.entries[2])))
        val merged = roundTrip(everything, current)
        val journal = JournalBook.decode(merged.journal)
        val tags = journal.tags(LocalDate.parse("2026-06-01"))
        assertEquals(setOf(JournalTag.entries[0], JournalTag.entries[1], JournalTag.entries[2]), tags, "tags of the same day are joined")
    }

    @Test
    fun `shoes of this phone win over the backup's and the others are added`() {
        val current = UserData(gear = GearBook(listOf(shoe("s1", "Mine", 100.0)), mapOf("a1" to "s9")).encode())
        val gear = GearBook.decode(roundTrip(everything, current).gear)
        assertEquals("Mine", gear.shoes.single { it.id == "s1" }.name)
        assertEquals("s9", gear.overrides["a1"], "an override set here is kept")
        val extra = UserData(gear = GearBook(listOf(shoe("s2", "Kayano")), mapOf("a2" to "s2")).encode())
        val both = GearBook.decode(roundTrip(extra, current).gear)
        assertEquals(setOf("s1", "s2"), both.shoes.map { it.id }.toSet())
        assertEquals("s2", both.overrides["a2"])
    }

    @Test
    fun `what the person already set here is never overwritten`() {
        val current = UserData(trainingPlan = "{\"mine\":1}", raceGoal = "2027-01-01|10k|2400", maxHr = 175, weeklyGoals = WeeklyGoals(180, 25, 5))
        val merged = roundTrip(everything, current)
        assertEquals("{\"mine\":1}", merged.trainingPlan)
        assertEquals("2027-01-01|10k|2400", merged.raceGoal)
        assertEquals(175, merged.maxHr)
        assertEquals(WeeklyGoals(180, 25, 5), merged.weeklyGoals)
    }

    @Test
    fun `an empty phone takes the plan, race, max heart rate and goals from the backup`() {
        val merged = roundTrip(everything, UserData())
        assertEquals(everything.trainingPlan, merged.trainingPlan)
        assertEquals(everything.raceGoal, merged.raceGoal)
        assertEquals(188, merged.maxHr)
        assertEquals(WeeklyGoals(200, 30, 4), merged.weeklyGoals)
    }

    @Test
    fun `a backup from before v0_56 restores and leaves this phone's own settings alone`() {
        val written = GZIPInputStream(ByteArrayInputStream(Backup.write(InMemoryRepository(), now, everything))).readBytes().toString(Charsets.UTF_8)
        val without = kotlinx.serialization.json.JsonObject((kotlinx.serialization.json.Json.parseToJsonElement(written) as kotlinx.serialization.json.JsonObject) - "userData")
        val packed = java.io.ByteArrayOutputStream()
        java.util.zip.GZIPOutputStream(packed).use { it.write(without.toString().toByteArray()) }
        val old = packed.toByteArray()
        val current = everything.copy(maxHr = 170)
        assertEquals(current, Backup.restore(old, InMemoryRepository(), current).userData)
    }

    @Test
    fun `the file holds no key, token or password`() {
        val repo = InMemoryRepository()
        repo.upsertDaily(DailySummary(date = day))
        val text = GZIPInputStream(ByteArrayInputStream(Backup.write(repo, now, everything))).readBytes().toString(Charsets.UTF_8)
        val allowed = setOf("format", "version", "createdAt", "activities", "daily", "sleep", "body", "water", "samples", "oura", "whoop", "hume", "userData")
        val root = kotlinx.serialization.json.Json.parseToJsonElement(text) as kotlinx.serialization.json.JsonObject
        assertEquals(allowed, root.keys, "a new top-level part needs a decision: is it a secret?")
        for (word in listOf("token", "secret", "password", "apikey", "api_key", "client")) {
            assertFalse(word in text.lowercase(), "the backup must not mention \"$word\"")
        }
        assertTrue("userData" in root)
    }
}
