package nl.tufein.openconnect.core

import kotlinx.coroutines.runBlocking
import nl.tufein.openconnect.core.health.HealthAccess
import nl.tufein.openconnect.core.hume.HumeDay
import nl.tufein.openconnect.core.hume.HumeDemo
import nl.tufein.openconnect.core.hume.HumeException
import nl.tufein.openconnect.core.hume.HumeExercise
import nl.tufein.openconnect.core.hume.HumeGateway
import nl.tufein.openconnect.core.hume.HumeOrigins
import nl.tufein.openconnect.core.hume.HumeReadings
import nl.tufein.openconnect.core.hume.HumeSample
import nl.tufein.openconnect.core.hume.HumeSleep
import nl.tufein.openconnect.core.hume.HumeStage
import nl.tufein.openconnect.core.hume.HumeStageKind
import nl.tufein.openconnect.core.hume.HumeSync
import nl.tufein.openconnect.core.hume.HumeWorkout
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A Health Connect that answers from memory and remembers what it was asked. */
class FakeHumeGateway(
    var access: HealthAccess = HealthAccess.READY,
    /** The apps that wrote data, and what each of them wrote. */
    val byOrigin: MutableMap<String, HumeReadings> = HashMap(),
) : HumeGateway {
    val reads = ArrayList<Set<String>>()
    var failWith: IOException? = null

    override suspend fun access(): HealthAccess = access

    override suspend fun permissionsToAsk(): Set<String> = setOf("read-steps")

    override suspend fun origins(from: LocalDate, to: LocalDate): Set<String> = byOrigin.keys.toSet()

    override suspend fun read(origins: Set<String>, from: LocalDate, to: LocalDate): HumeReadings {
        failWith?.let { throw it }
        reads += origins
        val all = origins.mapNotNull(byOrigin::get)
        return HumeReadings(
            steps = all.flatMap { it.steps.entries }.associate { it.key to it.value }.filterKeys { it in from..to },
            activeKcal = all.flatMap { it.activeKcal.entries }.associate { it.key to it.value }.filterKeys { it in from..to },
            heartRate = all.flatMap { it.heartRate },
            restingHr = all.flatMap { it.restingHr.entries }.associate { it.key to it.value },
            hrv = all.flatMap { it.hrv },
            spo2 = all.flatMap { it.spo2 },
            sleeps = all.flatMap { it.sleeps },
            exercises = all.flatMap { it.exercises },
        )
    }
}

/** Counts the writes, so a test can tell "nothing was written" from "the same thing was written again". */
private class CountingRepository(private val inner: InMemoryRepository = InMemoryRepository()) : Repository by inner {
    var humeWrites = 0
    override fun upsertHumeDay(day: HumeDay) {
        humeWrites++
        inner.upsertHumeDay(day)
    }
}

class HumeTest {
    private val hume = "com.elink.fittrackhealth.pro"
    private val today = LocalDate.parse("2026-11-18")
    private val repo = CountingRepository()
    private val gateway = FakeHumeGateway()

    private fun at(day: String, time: String): Instant = Instant.parse("${day}T$time:00Z")

    private fun sync(days: Int = 30, zone: ZoneId = ZoneOffset.UTC, chosen: String? = null) =
        runBlocking { HumeSync(gateway, repo, zone).sync(days, today, chosen) }

    private fun stored(day: String): HumeDay? = repo.listHumeDays(LocalDate.parse(day), LocalDate.parse(day)).singleOrNull()

    /** A night from 23:00 to 07:00 with stages: 100 min deep, 250 min light, 90 min REM, 30 min awake. */
    private val night = HumeSleep(
        start = at("2026-11-16", "23:00"),
        end = at("2026-11-17", "07:00"),
        stages = listOf(
            HumeStage(HumeStageKind.AWAKE, at("2026-11-16", "23:00"), at("2026-11-16", "23:30")),
            HumeStage(HumeStageKind.LIGHT, at("2026-11-16", "23:30"), at("2026-11-17", "02:00")),
            HumeStage(HumeStageKind.DEEP, at("2026-11-17", "02:00"), at("2026-11-17", "03:40")),
            HumeStage(HumeStageKind.REM, at("2026-11-17", "03:40"), at("2026-11-17", "05:10")),
            HumeStage(HumeStageKind.LIGHT, at("2026-11-17", "05:10"), at("2026-11-17", "05:20")),
            HumeStage(HumeStageKind.OUT_OF_BED, at("2026-11-17", "05:20"), at("2026-11-17", "05:30")),
            HumeStage(HumeStageKind.LIGHT, at("2026-11-17", "05:30"), at("2026-11-17", "07:00")),
        ),
    )

    // ---------------------------------------------------------------------------------- which app wrote it

    @Test
    fun `the Hume app is recognised by its package name, other apps' data is never taken`() {
        val found = setOf(hume, "com.garmin.android.apps.connectmobile", "com.google.android.apps.fitness", "nl.tufein.openconnect")
        assertEquals(setOf(hume), HumeOrigins.pick(found, null))
        // A package with "hume" in its name is also taken (the app may be published under another id one day).
        assertEquals(setOf("com.humehealth.band"), HumeOrigins.pick(setOf("com.humehealth.band", "com.fitbit.FitbitMobile"), null))
        assertTrue(HumeOrigins.pick(setOf("com.fitbit.FitbitMobile"), null).isEmpty())
    }

    @Test
    fun `a package the person chose beats the guess and is used even if its name says nothing`() {
        val found = setOf(hume, "com.example.otherband")
        assertEquals(setOf("com.example.otherband"), HumeOrigins.pick(found, "com.example.otherband"))
        // A chosen app that has no data in the period leaves nothing to read, instead of falling back to a guess.
        assertTrue(HumeOrigins.pick(found, "com.example.gone").isEmpty())
    }

    @Test
    fun `without any data from the Hume app nothing is written and the error names what Health Connect does hold`() {
        gateway.byOrigin["com.fitbit.FitbitMobile"] = HumeReadings(steps = mapOf(today to 4000))
        val e = assertFailsWith<HumeException> { sync() }
        assertEquals(HumeException.Kind.NO_DATA, e.kind)
        assertEquals(setOf("com.fitbit.FitbitMobile"), e.found)
        assertEquals(0, repo.humeWrites)
        assertTrue(gateway.reads.isEmpty(), "data of an app that is not the band's is not even read")
    }

    @Test
    fun `without permission or without Health Connect the sync says so before it reads anything`() {
        gateway.access = HealthAccess.NEEDS_PERMISSION
        assertEquals(HumeException.Kind.NEEDS_PERMISSION, assertFailsWith<HumeException> { sync() }.kind)
        gateway.access = HealthAccess.UNAVAILABLE
        assertEquals(HumeException.Kind.UNAVAILABLE, assertFailsWith<HumeException> { sync() }.kind)
        assertTrue(gateway.reads.isEmpty())
    }

    // ---------------------------------------------------------------------------------- building a day

    @Test
    fun `a day gets its night from the wake-up date, the heart rate of that night and the rest of the day's numbers`() {
        val d = "2026-11-17"
        gateway.byOrigin[hume] = HumeReadings(
            steps = mapOf(LocalDate.parse(d) to 9_500),
            activeKcal = mapOf(LocalDate.parse(d) to 610),
            heartRate = listOf(
                HumeSample(at(d, "01:00"), 50.0), // asleep
                HumeSample(at(d, "04:00"), 46.0), // asleep, the lowest
                HumeSample(at(d, "12:00"), 80.0),
                HumeSample(at(d, "18:00"), 120.0), // the highest
                HumeSample(at("2026-11-16", "15:00"), 200.0), // another day
            ),
            restingHr = mapOf(LocalDate.parse(d) to 52),
            hrv = listOf(HumeSample(at(d, "03:00"), 60.0), HumeSample(at(d, "05:00"), 70.04), HumeSample(at(d, "15:00"), 20.0)),
            spo2 = listOf(HumeSample(at(d, "03:00"), 96.0), HumeSample(at(d, "05:00"), 97.0)),
            sleeps = listOf(night),
        )
        val report = sync()
        val day = stored(d)!!
        assertEquals(9_500, day.steps)
        assertEquals(610, day.activeKcal)
        assertEquals(440 * 60, day.sleepS, "asleep: 100 deep + 250 light + 90 REM minutes; the time out of bed counts as neither")
        assertEquals(100 * 60, day.deepS)
        assertEquals(90 * 60, day.remS)
        assertEquals(250 * 60, day.lightS)
        assertEquals(30 * 60, day.awakeS, "awake in bed counts, being out of bed does not")
        assertEquals(46, day.lowestHr, "the lowest heart rate of the night, not of the day")
        assertEquals(52, day.restingHr)
        assertEquals(74, day.avgHr, "mean of that day's readings: (50 + 46 + 80 + 120) / 4")
        assertEquals(120, day.maxHr)
        assertEquals(65.0, day.hrv, "the nightly mean of 60.0 and 70.04, to a tenth")
        assertEquals(96.5, day.spo2)
        assertEquals(2, report.daysUpdated, "the 17th, and the 16th that only has the one heart rate reading")
        assertEquals(200, stored("2026-11-16")!!.avgHr)
        assertEquals(1, report.nightsUpdated)
    }

    @Test
    fun `the longest night of a wake-up date wins and a short sleep on another date is not mixed in`() {
        val d = "2026-11-17"
        val nap = HumeSleep(at(d, "14:00"), at(d, "14:40"))
        val eveningNap = HumeSleep(at("2026-11-16", "20:00"), at("2026-11-16", "20:30"))
        gateway.byOrigin[hume] = HumeReadings(sleeps = listOf(night, nap, eveningNap))
        sync()
        // The night has 100 + 150 + 10 + 90 + 90 = 440 minutes of sleep; the nap of the same date is ignored.
        assertEquals(440 * 60, stored(d)!!.sleepS)
        assertEquals(30 * 60, stored("2026-11-16")!!.sleepS, "a half-hour sleep that ended on the 16th is that day's own")
    }

    @Test
    fun `a night without stages counts its whole length as sleep and has no stage numbers`() {
        gateway.byOrigin[hume] = HumeReadings(sleeps = listOf(HumeSleep(at("2026-11-17", "00:00"), at("2026-11-17", "07:30"))))
        sync()
        val day = stored("2026-11-17")!!
        assertEquals(7 * 3600 + 1800, day.sleepS)
        assertNull(day.deepS)
        assertNull(day.remS)
        assertNull(day.lightS)
        assertNull(day.awakeS)
    }

    @Test
    fun `the day is the day on the phone's clock, so a late evening in Amsterdam is not tomorrow in UTC`() {
        val amsterdam = ZoneId.of("Europe/Amsterdam") // UTC+1 in November
        // 23:30 UTC on the 17th is 00:30 on the 18th in Amsterdam.
        gateway.byOrigin[hume] = HumeReadings(heartRate = listOf(HumeSample(at("2026-11-17", "23:30"), 61.0)))
        sync(zone = amsterdam)
        assertNull(stored("2026-11-17"))
        assertEquals(61, stored("2026-11-18")!!.avgHr)
    }

    @Test
    fun `workouts go to the date they started on with the sport, the length, the energy and the distance`() {
        val d = "2026-11-15"
        gateway.byOrigin[hume] = HumeReadings(
            exercises = listOf(
                HumeExercise("e1", "running", "Morning run", at(d, "07:00"), at(d, "07:45"), calories = 480, distanceM = 7_200.0),
                HumeExercise("e2", "strengthTraining", null, at(d, "18:00"), at(d, "18:30")),
            ),
        )
        val report = sync()
        val workouts = stored(d)!!.workouts
        assertEquals(listOf("e1", "e2"), workouts.map { it.id })
        assertEquals("Morning run", workouts[0].name)
        assertEquals(45 * 60L, workouts[0].durationS)
        assertEquals(480, workouts[0].calories)
        assertEquals(7_200.0, workouts[0].distanceM)
        assertEquals("Strength training", workouts[1].name, "no title: the sport's name, spelled out")
        assertEquals(2, report.workoutsAdded)
    }

    @Test
    fun `only the asked period is stored, and a day with nothing from the band is not created`() {
        gateway.byOrigin[hume] = HumeReadings(
            steps = mapOf(today to 3_000, today.minusDays(40) to 8_000),
            heartRate = listOf(HumeSample(at("2026-10-01", "10:00"), 70.0)),
        )
        sync(days = 7)
        assertEquals(listOf(today.toString()), repo.listHumeDays(today.minusDays(60), today).map { it.day })
    }

    @Test
    fun `a second sync that finds the same numbers writes nothing, and a changed day is written again`() {
        gateway.byOrigin[hume] = HumeReadings(steps = mapOf(today to 3_000), sleeps = listOf(night))
        val first = sync()
        val writes = repo.humeWrites
        val again = sync()
        assertEquals(writes, repo.humeWrites)
        assertEquals(0, again.total)
        assertTrue(first.total > 0)

        gateway.byOrigin[hume] = HumeReadings(steps = mapOf(today to 3_400), sleeps = listOf(night))
        val changed = sync()
        assertEquals(1, changed.daysUpdated)
        assertEquals(0, changed.nightsUpdated, "the night did not change, only the steps")
        assertEquals(3_400, stored(today.toString())!!.steps)
    }

    @Test
    fun `a failure of Health Connect reaches the caller as it is and leaves the stored days alone`() {
        gateway.byOrigin[hume] = HumeReadings(steps = mapOf(today to 3_000))
        sync()
        gateway.failWith = IOException("Health Connect refused")
        assertFailsWith<IOException> { sync() }
        assertEquals(3_000, stored(today.toString())!!.steps)
    }

    // ---------------------------------------------------------------------------------- what is stored

    @Test
    fun `a day survives its trip through JSON and unknown fields of a later version are ignored`() {
        val day = HumeDemo.days(today, 3).last()
        assertEquals(day, HumeDay.fromJson(day.toJson()))
        val withExtra = day.toJson().dropLast(1) + ""","someFutureField":1}"""
        assertEquals(day, HumeDay.fromJson(withExtra))
    }

    @Test
    fun `the demo days are a month of plausible days, the same every time`() {
        val a = HumeDemo.days(today)
        assertEquals(30, a.size)
        assertEquals(today.toString(), a.last().day)
        assertEquals(a, HumeDemo.days(today))
        assertTrue(a.all { (it.restingHr ?: 0) in 40..70 && (it.sleepS ?: 0) in 18_000..36_000 })
        assertTrue(a.any { it.workouts.isNotEmpty() })
    }

    @Test
    fun `Hume's days are in a backup and an older backup without them still restores`() {
        val days = HumeDemo.days(today, 5)
        days.forEach(repo::upsertHumeDay)
        val bytes = Backup.write(repo)
        val other = InMemoryRepository()
        Backup.restore(bytes, other)
        assertEquals(days, other.listHumeDays(LocalDate.parse("2026-01-01"), today))

        val text = java.util.zip.GZIPInputStream(bytes.inputStream()).use { String(it.readBytes(), Charsets.UTF_8) }
        val root = kotlinx.serialization.json.Json.parseToJsonElement(text) as kotlinx.serialization.json.JsonObject
        val withoutHume = kotlinx.serialization.json.JsonObject(root.filterKeys { it != "hume" }).toString()
        val packed = java.io.ByteArrayOutputStream().also { out -> java.util.zip.GZIPOutputStream(out).use { it.write(withoutHume.toByteArray(Charsets.UTF_8)) } }.toByteArray()
        val third = InMemoryRepository()
        Backup.restore(packed, third)
        assertTrue(third.listHumeDays(LocalDate.parse("2026-01-01"), today).isEmpty())
    }

    @Test
    fun `clearing everything clears the band's days too`() {
        HumeDemo.days(today, 3).forEach(repo::upsertHumeDay)
        repo.clearAll()
        assertTrue(repo.listHumeDays(today.minusDays(10), today).isEmpty())
    }

    // ---------------------------------------------------------------------------------- the rest of the app

    @Test
    fun `the Insights day card can read a Hume day, with the numbers a band without scores has`() {
        val day = HumeDay(
            day = today.toString(), steps = 9_000, restingHr = 51, lowestHr = 44, hrv = 66.5, spo2 = 96.5, sleepS = 7 * 3600,
            workouts = listOf(HumeWorkout("e1", "running", at(today.toString(), "07:00").toEpochMilli(), at(today.toString(), "07:30").toEpochMilli(), null, null, 400, 5_000.0, null)),
        )
        val series = DayRecords.hume(today, today, listOf(day))
        assertEquals(InsightSource.HUME, series.source)
        val record = series.record(today)!!
        assertEquals(9_000.0, record.values[ChartMetric.STEPS])
        assertEquals(7.0, record.values[ChartMetric.SLEEP])
        assertEquals(51.0, record.values[ChartMetric.RESTING_HR])
        assertEquals(44.0, record.values[ChartMetric.LOWEST_HR])
        assertEquals(66.5, record.values[ChartMetric.HRV])
        assertEquals(96.5, record.values[ChartMetric.SPO2])
        assertFalse(ChartMetric.READINESS in record.values, "Health Connect carries no readiness or sleep score, so none is made up")
        assertEquals("hume:e1", record.workouts.single().id)
        assertEquals(setOf(ChartMetric.STEPS, ChartMetric.SLEEP, ChartMetric.RESTING_HR, ChartMetric.LOWEST_HR, ChartMetric.HRV, ChartMetric.SPO2), InsightSource.HUME.metrics.toSet())
    }

    @Test
    fun `a Hume workout is listed among the activities like the other bands' and is never stored as one`() {
        val w = HumeWorkout("e9", "walking", at("2026-11-17", "12:00").toEpochMilli(), at("2026-11-17", "12:40").toEpochMilli(), null, null, 150, 3_000.0, "Lunch walk")
        val rows = BandWorkouts.extra(listOf(w), BandSource.HUME, emptyList())
        val row = rows.single()
        assertEquals("hume:e9", row.id)
        assertEquals("hume", row.source)
        assertEquals("Hume Band", row.deviceName)
        assertEquals(Sport.WALKING, row.sport)
        assertEquals(BandSource.HUME, BandSource.ofRow(row.id))
        assertEquals(w, BandWorkouts.find(row.id, BandSource.HUME, listOf(w)))
        assertTrue(repo.listActivities(limit = 10).isEmpty())
    }
}
