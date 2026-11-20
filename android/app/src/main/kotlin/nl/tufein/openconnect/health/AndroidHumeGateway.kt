package nl.tufein.openconnect.health

import android.content.Context
import nl.tufein.openconnect.core.runCatchingUnlessCancelled
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.CancellationException
import nl.tufein.openconnect.core.health.HealthAccess
import nl.tufein.openconnect.core.runCatchingUnlessCancelled
import nl.tufein.openconnect.core.hume.HumeExercise
import nl.tufein.openconnect.core.hume.HumeGateway
import nl.tufein.openconnect.core.hume.HumeReadings
import nl.tufein.openconnect.core.hume.HumeSample
import nl.tufein.openconnect.core.hume.HumeSleep
import nl.tufein.openconnect.core.hume.HumeStage
import nl.tufein.openconnect.core.hume.HumeStageKind
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import kotlin.reflect.KClass

/**
 * The phone's Health Connect, read for the Hume Band: only the records the Hume app itself wrote (Health Connect says
 * which app wrote each record), so another app's steps or heart rate are never taken for the band's. Like the other links it
 * can only really be tried on a phone with Health Connect and the Hume app; the rules around it are tested with a fake.
 */
class AndroidHumeGateway(private val context: Context, private val zone: () -> ZoneId = { ZoneId.systemDefault() }) : HumeGateway {
    companion object {
        private fun read(type: KClass<out androidx.health.connect.client.records.Record>) = HealthPermission.getReadPermission(type)

        /** Without these three there is no point: the night, the steps and the heart rate. */
        val CORE: Set<String> = setOf(read(StepsRecord::class), read(SleepSessionRecord::class), read(HeartRateRecord::class))

        /** The rest is read when allowed and skipped when not. */
        val OPTIONAL: Set<String> = setOf(
            read(RestingHeartRateRecord::class),
            read(HeartRateVariabilityRmssdRecord::class),
            read(OxygenSaturationRecord::class),
            read(ActiveCaloriesBurnedRecord::class),
            read(ExerciseSessionRecord::class),
            read(DistanceRecord::class),
        )
        private const val PAGE = 2_000
    }

    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    private suspend fun granted(): Set<String> = client.permissionController.getGrantedPermissions()

    override suspend fun access(): HealthAccess {
        val status = runCatchingUnlessCancelled { HealthConnectClient.getSdkStatus(context) }.getOrDefault(HealthConnectClient.SDK_UNAVAILABLE)
        if (status != HealthConnectClient.SDK_AVAILABLE) return HealthAccess.UNAVAILABLE
        return try {
            if (granted().containsAll(CORE)) HealthAccess.READY else HealthAccess.NEEDS_PERMISSION
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            HealthAccess.UNAVAILABLE
        }
    }

    @OptIn(androidx.health.connect.client.feature.ExperimentalFeatureAvailabilityApi::class)
    override suspend fun permissionsToAsk(): Set<String> {
        val background = try {
            client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
        return CORE + OPTIONAL + if (background) setOf(HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND) else emptySet()
    }

    private fun range(from: LocalDate, to: LocalDate): TimeRangeFilter {
        val z = zone()
        return TimeRangeFilter.between(from.atStartOfDay(z).toInstant(), to.plusDays(1).atStartOfDay(z).toInstant())
    }

    private inline fun <T> guarded(block: () -> T): T = try {
        block()
    } catch (e: IOException) {
        throw e
    } catch (e: SecurityException) {
        throw e
    } catch (e: CancellationException) {
        throw e // a cancelled coroutine is not a Health Connect failure
    } catch (e: Exception) {
        throw IOException(e.message ?: "Health Connect refused", e)
    }

    override suspend fun origins(from: LocalDate, to: LocalDate): Set<String> = guarded {
        val allowed = granted()
        val filter = range(from, to)
        val found = HashSet<String>()

        // Which apps wrote anything of these kinds in the period. Each kind on its own: Health Connect lets a higher-priority
        // app hide a lower one's steps in a total, but not its heart rate or its nights.
        suspend fun ask(permission: String, metric: AggregateMetric<*>) {
            if (permission !in allowed) return
            runCatchingUnlessCancelled { client.aggregate(AggregateRequest(metrics = setOf(metric), timeRangeFilter = filter)) }
                .getOrNull()?.dataOrigins?.mapTo(found) { it.packageName }
        }
        ask(read(StepsRecord::class), StepsRecord.COUNT_TOTAL)
        ask(read(SleepSessionRecord::class), SleepSessionRecord.SLEEP_DURATION_TOTAL)
        ask(read(HeartRateRecord::class), HeartRateRecord.BPM_AVG)
        ask(read(ActiveCaloriesBurnedRecord::class), ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
        ask(read(ExerciseSessionRecord::class), ExerciseSessionRecord.EXERCISE_DURATION_TOTAL)
        found
    }

    override suspend fun read(origins: Set<String>, from: LocalDate, to: LocalDate): HumeReadings = guarded {
        val z = zone()
        val allowed = granted()
        val only = origins.map(::DataOrigin).toSet()
        val filter = range(from, to)

        fun can(type: KClass<out androidx.health.connect.client.records.Record>) = read(type) in allowed

        suspend fun <T : androidx.health.connect.client.records.Record> records(type: KClass<T>): List<T> {
            if (!can(type)) return emptyList()
            val out = ArrayList<T>()
            var token: String? = null
            do {
                val page = client.readRecords(ReadRecordsRequest(type, filter, dataOriginFilter = only, pageSize = PAGE, pageToken = token))
                out += page.records
                token = page.pageToken.takeIf { page.records.isNotEmpty() }
            } while (token != null)
            return out
        }

        val steps = LinkedHashMap<LocalDate, Int>()
        if (can(StepsRecord::class)) {
            for (b in client.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(from.atStartOfDay(), to.plusDays(1).atStartOfDay()), Period.ofDays(1), only),
            )) {
                b.result[StepsRecord.COUNT_TOTAL]?.let { steps[b.startTime.toLocalDate()] = it.toInt() }
            }
        }
        val kcal = LinkedHashMap<LocalDate, Int>()
        if (can(ActiveCaloriesBurnedRecord::class)) {
            for (b in client.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL), TimeRangeFilter.between(from.atStartOfDay(), to.plusDays(1).atStartOfDay()), Period.ofDays(1), only),
            )) {
                b.result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.let { kcal[b.startTime.toLocalDate()] = Math.round(it.inKilocalories).toInt() }
            }
        }
        val heart = records(HeartRateRecord::class).flatMap { r -> r.samples.map { HumeSample(it.time, it.beatsPerMinute.toDouble()) } }
        val resting = HashMap<LocalDate, MutableList<Long>>()
        for (r in records(RestingHeartRateRecord::class)) resting.getOrPut(r.time.atZone(z).toLocalDate()) { ArrayList() }.add(r.beatsPerMinute)
        val hrv = records(HeartRateVariabilityRmssdRecord::class).map { HumeSample(it.time, it.heartRateVariabilityMillis) }
        val spo2 = records(OxygenSaturationRecord::class).map { HumeSample(it.time, it.percentage.value) }
        val sleeps = records(SleepSessionRecord::class).map { r ->
            HumeSleep(r.startTime, r.endTime, r.stages.mapNotNull { st -> stageKind(st.stage)?.let { HumeStage(it, st.startTime, st.endTime) } })
        }
        val exercises = records(ExerciseSessionRecord::class).map { r ->
            // What the session burned and covered is asked of Health Connect for the session's own time and only of the band's app.
            val totals = runCatching {
                client.aggregate(
                    AggregateRequest(
                        metrics = buildSet {
                            if (can(ActiveCaloriesBurnedRecord::class)) add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
                            if (can(DistanceRecord::class)) add(DistanceRecord.DISTANCE_TOTAL)
                        },
                        timeRangeFilter = TimeRangeFilter.between(r.startTime, r.endTime),
                        dataOriginFilter = only,
                    ),
                )
            }.getOrNull()
            HumeExercise(
                id = r.metadata.id,
                type = exerciseKey(r.exerciseType),
                title = r.title,
                start = r.startTime,
                end = r.endTime,
                calories = totals?.get(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)?.inKilocalories?.let { Math.round(it).toInt() },
                distanceM = totals?.get(DistanceRecord.DISTANCE_TOTAL)?.inMeters,
            )
        }
        HumeReadings(
            steps = steps,
            activeKcal = kcal,
            heartRate = heart,
            restingHr = resting.mapValues { (_, v) -> Math.round(v.average()).toInt() },
            hrv = hrv,
            spo2 = spo2,
            sleeps = sleeps,
            exercises = exercises,
        )
    }

    /** Health Connect's exercise type as the plain key the app's sports are worked out from ("running", "strengthTraining"). */
    private fun exerciseKey(type: Int): String = when (type) {
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> "running"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING, ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> "cycling"
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "walking"
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "hiking"
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL, ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "swimming"
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> "strengthTraining"
        ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "yoga"
        ExerciseSessionRecord.EXERCISE_TYPE_PILATES -> "pilates"
        ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> "elliptical"
        ExerciseSessionRecord.EXERCISE_TYPE_ROWING, ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE -> "rowing"
        else -> "workout"
    }

    private fun stageKind(stage: Int): HumeStageKind? = when (stage) {
        SleepSessionRecord.STAGE_TYPE_AWAKE, SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED -> HumeStageKind.AWAKE
        SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> HumeStageKind.OUT_OF_BED
        SleepSessionRecord.STAGE_TYPE_LIGHT -> HumeStageKind.LIGHT
        SleepSessionRecord.STAGE_TYPE_DEEP -> HumeStageKind.DEEP
        SleepSessionRecord.STAGE_TYPE_REM -> HumeStageKind.REM
        SleepSessionRecord.STAGE_TYPE_SLEEPING -> HumeStageKind.ASLEEP
        else -> null
    }
}
