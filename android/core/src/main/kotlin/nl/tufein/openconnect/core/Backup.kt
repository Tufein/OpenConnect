package nl.tufein.openconnect.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import nl.tufein.openconnect.core.watch.Metric
import nl.tufein.openconnect.core.watch.MetricSample
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class BackupException(message: String) : Exception(message)

/** What a restore brought in. */
data class RestoreReport(val activities: Int, val days: Int, val nights: Int, val samples: Int, val createdAt: Instant)

/**
 * One file with everything the app holds: activities with their tracks, daily summaries, sleep and the watch's readings.
 * It is JSON, compressed with gzip. Restoring merges into what is there (same activity, day or reading is replaced by the
 * backup's copy) and never deletes anything. AI reports and the record of which watch files were read are not part of it:
 * reports can be written again and the next sync simply reads the watch files again.
 */
object Backup {
    const val FORMAT = "openconnect-backup"
    const val VERSION = 1
    private const val MAX_UNPACKED = 400 * 1024 * 1024
    private val FAR_PAST = LocalDate.of(1970, 1, 1)
    private val FAR_FUTURE = LocalDate.of(2200, 1, 1)

    fun write(repo: Repository, now: Instant = Instant.now()): ByteArray {
        val activities = repo.listActivities(limit = Int.MAX_VALUE).mapNotNull { repo.getActivity(it.id) }
        val root = buildJsonObject {
            put("format", FORMAT)
            put("version", VERSION)
            put("createdAt", now.toString())
            put("activities", JsonArray(activities.map(::activityJson)))
            put("daily", JsonArray(repo.listDaily(FAR_PAST, FAR_FUTURE).map(::dailyJson)))
            put("sleep", JsonArray(repo.listSleep(FAR_PAST, FAR_FUTURE).map(::sleepJson)))
            put("samples", buildJsonArray { repo.listSamples(FAR_PAST, FAR_FUTURE).forEach { add(sampleJson(it)) } })
            // v0.17.0: the Oura Ring's days, in their own list. Older app versions simply skip it.
            put("oura", JsonArray(repo.listOuraDays(FAR_PAST, FAR_FUTURE).map { kotlinx.serialization.json.Json.parseToJsonElement(it.toJson()) }))
        }
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(root.toString().toByteArray(Charsets.UTF_8)) }
        return out.toByteArray()
    }

    /** Reads and checks the whole file first; only then is anything written, so a damaged file changes nothing. */
    fun restore(bytes: ByteArray, repo: Repository): RestoreReport {
        val root = parse(bytes)
        val createdAt = try {
            Instant.parse(root.str("createdAt") ?: throw BackupException("This backup has no date."))
        } catch (e: java.time.format.DateTimeParseException) {
            throw BackupException("This backup has a damaged date.")
        }
        val activities: List<Activity>
        val daily: List<DailySummary>
        val sleep: List<SleepSummary>
        val samples: List<MetricSample>
        val oura: List<nl.tufein.openconnect.core.oura.OuraDay>
        try {
            activities = root.list("activities").map { activityOf(it.jsonObject) }
            daily = root.list("daily").map { dailyOf(it.jsonObject) }
            sleep = root.list("sleep").map { sleepOf(it.jsonObject) }
            samples = root.list("samples").map { sampleOf(it.jsonArray) }
            oura = root.list("oura").map { nl.tufein.openconnect.core.oura.OuraDay.fromJson(it.toString()) }
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            throw BackupException("This backup is damaged and was not restored (${e.message}).")
        }
        activities.forEach(repo::upsertActivity)
        daily.forEach(repo::upsertDaily)
        sleep.forEach(repo::upsertSleep)
        repo.upsertSamples(samples)
        oura.forEach(repo::upsertOuraDay)
        return RestoreReport(activities.size, daily.size, sleep.size, samples.size, createdAt)
    }

    private fun parse(bytes: ByteArray): JsonObject {
        val text = try {
            GZIPInputStream(bytes.inputStream()).use {
                val unpacked = it.readNBytes(MAX_UNPACKED + 1)
                if (unpacked.size > MAX_UNPACKED) throw BackupException("This backup is too large to restore.")
                String(unpacked, Charsets.UTF_8)
            }
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            throw BackupException("This is not an OpenConnect backup.")
        }
        val root = try {
            Json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            throw BackupException("This is not an OpenConnect backup.")
        }
        if (root.str("format") != FORMAT) throw BackupException("This is not an OpenConnect backup.")
        val version = root["version"]?.jsonPrimitive?.intOrNull ?: throw BackupException("This backup has no version.")
        if (version > VERSION) throw BackupException("This backup comes from a newer version of OpenConnect. Update the app first.")
        return root
    }

    // ------------------------------------------------------------------------------------------------ writing

    private fun JsonObjectBuilderPut(b: kotlinx.serialization.json.JsonObjectBuilder, key: String, value: Any?) {
        when (value) {
            null -> {}
            is String -> b.put(key, value)
            is Int -> b.put(key, value)
            is Long -> b.put(key, value)
            is Double -> b.put(key, value)
            else -> error("unsupported ${value::class}")
        }
    }

    private fun activityJson(a: Activity) = buildJsonObject {
        put("id", a.id)
        put("source", a.source)
        JsonObjectBuilderPut(this, "externalId", a.externalId)
        put("name", a.name)
        put("sport", a.sport.key)
        put("startTime", a.startTime.toString())
        put("durationS", a.durationS)
        put("distanceM", a.distanceM)
        put("elevationGainM", a.elevationGainM)
        JsonObjectBuilderPut(this, "avgHr", a.avgHr)
        JsonObjectBuilderPut(this, "maxHr", a.maxHr)
        JsonObjectBuilderPut(this, "calories", a.calories)
        JsonObjectBuilderPut(this, "deviceName", a.deviceName)
        // a track point is [time, lat, lon, ele, hr, cadence, power, distance] with null for what is missing
        put("track", buildJsonArray {
            a.track.forEach { p ->
                add(buildJsonArray {
                    add(JsonPrimitive(p.time.toString()))
                    for (v in listOf(p.lat, p.lon, p.ele, p.hr, p.cadence, p.power, p.distanceM)) {
                        add(
                            when (v) {
                                null -> JsonNull
                                is Int -> JsonPrimitive(v)
                                is Double -> JsonPrimitive(v)
                                else -> JsonNull
                            },
                        )
                    }
                })
            }
        })
    }

    private fun dailyJson(d: DailySummary) = buildJsonObject {
        put("date", d.date.toString())
        put("steps", d.steps)
        JsonObjectBuilderPut(this, "stepsGoal", d.stepsGoal)
        put("distanceM", d.distanceM)
        put("activeKcal", d.activeKcal)
        put("floorsClimbed", d.floorsClimbed)
        put("moderateIntensityMin", d.moderateIntensityMin)
        put("vigorousIntensityMin", d.vigorousIntensityMin)
        JsonObjectBuilderPut(this, "restingHr", d.restingHr)
        JsonObjectBuilderPut(this, "minHr", d.minHr)
        JsonObjectBuilderPut(this, "maxHr", d.maxHr)
        JsonObjectBuilderPut(this, "avgStress", d.avgStress)
        JsonObjectBuilderPut(this, "maxStress", d.maxStress)
        JsonObjectBuilderPut(this, "bodyBatteryHigh", d.bodyBatteryHigh)
        JsonObjectBuilderPut(this, "bodyBatteryLow", d.bodyBatteryLow)
        JsonObjectBuilderPut(this, "hrvMs", d.hrvMs)
        JsonObjectBuilderPut(this, "spo2Avg", d.spo2Avg)
        JsonObjectBuilderPut(this, "spo2Min", d.spo2Min)
        JsonObjectBuilderPut(this, "respirationAvg", d.respirationAvg)
    }

    private fun sleepJson(s: SleepSummary) = buildJsonObject {
        put("date", s.date.toString())
        put("durationS", s.durationS)
        put("deepS", s.deepS)
        put("lightS", s.lightS)
        put("remS", s.remS)
        put("awakeS", s.awakeS)
        JsonObjectBuilderPut(this, "score", s.score)
    }

    /** [metric code, time, day, value] keeps the many readings small. */
    private fun sampleJson(s: MetricSample): JsonElement = buildJsonArray {
        add(JsonPrimitive(s.metric.code))
        add(JsonPrimitive(s.time.toString()))
        add(JsonPrimitive(s.day.toString()))
        add(JsonPrimitive(s.value))
    }

    // ------------------------------------------------------------------------------------------------ reading

    private fun JsonObject.str(key: String): String? = this[key]?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull

    private fun JsonObject.need(key: String): String = str(key) ?: throw BackupException("An entry is missing '$key'.")

    private fun JsonObject.int(key: String): Int? = this[key]?.takeIf { it !is JsonNull }?.jsonPrimitive?.intOrNull

    private fun JsonObject.dbl(key: String): Double? = this[key]?.takeIf { it !is JsonNull }?.jsonPrimitive?.doubleOrNull

    private fun JsonObject.list(key: String): JsonArray = this[key]?.jsonArray ?: JsonArray(emptyList())

    private fun instant(s: String): Instant = try {
        Instant.parse(s)
    } catch (e: java.time.format.DateTimeParseException) {
        throw BackupException("An entry has a damaged time: $s")
    }

    private fun date(s: String): LocalDate = try {
        LocalDate.parse(s)
    } catch (e: java.time.format.DateTimeParseException) {
        throw BackupException("An entry has a damaged date: $s")
    }

    private fun JsonElement.dbl(): Double? = takeIf { it !is JsonNull }?.jsonPrimitive?.doubleOrNull

    private fun JsonElement.int(): Int? = takeIf { it !is JsonNull }?.jsonPrimitive?.intOrNull

    private fun activityOf(o: JsonObject) = Activity(
        id = o.need("id"),
        source = o.str("source") ?: "import",
        externalId = o.str("externalId"),
        name = o.str("name") ?: "",
        sport = Sport.fromKey(o.str("sport")),
        startTime = instant(o.need("startTime")),
        durationS = o.dbl("durationS") ?: 0.0,
        distanceM = o.dbl("distanceM") ?: 0.0,
        elevationGainM = o.dbl("elevationGainM") ?: 0.0,
        avgHr = o.int("avgHr"),
        maxHr = o.int("maxHr"),
        calories = o.int("calories"),
        deviceName = o.str("deviceName"),
        track = o.list("track").map { pt ->
            val a = pt.jsonArray
            TrackPoint(
                time = instant(a[0].jsonPrimitive.content),
                lat = a[1].dbl(), lon = a[2].dbl(), ele = a[3].dbl(),
                hr = a[4].int(), cadence = a[5].int(), power = a[6].int(), distanceM = a[7].dbl(),
            )
        },
    )

    private fun dailyOf(o: JsonObject) = DailySummary(
        date = date(o.need("date")),
        steps = o.int("steps") ?: 0,
        stepsGoal = o.int("stepsGoal"),
        distanceM = o.dbl("distanceM") ?: 0.0,
        activeKcal = o.int("activeKcal") ?: 0,
        floorsClimbed = o.int("floorsClimbed") ?: 0,
        moderateIntensityMin = o.int("moderateIntensityMin") ?: 0,
        vigorousIntensityMin = o.int("vigorousIntensityMin") ?: 0,
        restingHr = o.int("restingHr"), minHr = o.int("minHr"), maxHr = o.int("maxHr"),
        avgStress = o.int("avgStress"), maxStress = o.int("maxStress"),
        bodyBatteryHigh = o.int("bodyBatteryHigh"), bodyBatteryLow = o.int("bodyBatteryLow"),
        hrvMs = o.int("hrvMs"), spo2Avg = o.int("spo2Avg"), spo2Min = o.int("spo2Min"),
        respirationAvg = o.dbl("respirationAvg"),
    )

    private fun sleepOf(o: JsonObject) = SleepSummary(
        date = date(o.need("date")),
        durationS = o.int("durationS") ?: 0,
        deepS = o.int("deepS") ?: 0,
        lightS = o.int("lightS") ?: 0,
        remS = o.int("remS") ?: 0,
        awakeS = o.int("awakeS") ?: 0,
        score = o.int("score"),
    )

    private fun sampleOf(a: JsonArray): MetricSample {
        val code = a[0].jsonPrimitive.intOrNull ?: throw BackupException("A reading has no metric.")
        val metric = Metric.entries.firstOrNull { it.code == code } ?: throw BackupException("A reading has an unknown metric ($code).")
        return MetricSample(metric, instant(a[1].jsonPrimitive.content), date(a[2].jsonPrimitive.content), a[3].jsonPrimitive.doubleOrNull ?: throw BackupException("A reading has no value."))
    }
}
