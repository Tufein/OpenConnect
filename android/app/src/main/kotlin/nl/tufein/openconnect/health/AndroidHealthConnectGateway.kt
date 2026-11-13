package nl.tufein.openconnect.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import nl.tufein.openconnect.core.health.HealthAccess
import nl.tufein.openconnect.core.health.HealthConnectGateway
import nl.tufein.openconnect.core.health.HealthSession
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.Period
import java.time.ZoneId

/**
 * The phone's Health Connect. Like the Bluetooth and USB links it can only really be tried on a phone that has Health
 * Connect (built into Android 14 and newer, an app from Google Play before that); the rules around it are tested with a fake.
 */
class AndroidHealthConnectGateway(private val context: Context, private val zone: () -> ZoneId = { ZoneId.systemDefault() }) : HealthConnectGateway {
    companion object {
        /** What the person is asked to allow: write sessions, distance and calories; read steps and sleep. */
        val PERMISSIONS: Set<String> = setOf(
            HealthPermission.getWritePermission(ExerciseSessionRecord::class),
            HealthPermission.getWritePermission(DistanceRecord::class),
            HealthPermission.getWritePermission(TotalCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
        )

        fun exerciseType(sportKey: String): Int = when (sportKey) {
            "running" -> ExerciseSessionRecord.EXERCISE_TYPE_RUNNING
            "cycling" -> ExerciseSessionRecord.EXERCISE_TYPE_BIKING
            "swimming" -> ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL
            "walking" -> ExerciseSessionRecord.EXERCISE_TYPE_WALKING
            "hiking" -> ExerciseSessionRecord.EXERCISE_TYPE_HIKING
            "strength" -> ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING
            "yoga" -> ExerciseSessionRecord.EXERCISE_TYPE_YOGA
            else -> ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT
        }
    }

    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    override suspend fun access(): HealthAccess {
        if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) return HealthAccess.UNAVAILABLE
        return try {
            if (client.permissionController.getGrantedPermissions().containsAll(PERMISSIONS)) HealthAccess.READY else HealthAccess.NEEDS_PERMISSION
        } catch (e: Exception) {
            HealthAccess.UNAVAILABLE
        }
    }

    private inline fun <T> guarded(block: () -> T): T = try {
        block()
    } catch (e: IOException) {
        throw e
    } catch (e: Exception) {
        throw IOException(e.message ?: "Health Connect refused", e)
    }

    override suspend fun write(sessions: List<HealthSession>): Int = guarded {
        val z = zone()
        val records = ArrayList<androidx.health.connect.client.records.Record>()
        for (s in sessions) {
            val startOffset = z.rules.getOffset(s.start)
            val endOffset = z.rules.getOffset(s.end)
            records += ExerciseSessionRecord(
                startTime = s.start, startZoneOffset = startOffset, endTime = s.end, endZoneOffset = endOffset,
                metadata = Metadata(clientRecordId = s.clientId),
                exerciseType = exerciseType(s.sportKey), title = s.name.ifBlank { null },
            )
            if (s.distanceM > 0) {
                records += DistanceRecord(
                    startTime = s.start, startZoneOffset = startOffset, endTime = s.end, endZoneOffset = endOffset,
                    distance = Length.meters(s.distanceM), metadata = Metadata(clientRecordId = s.clientId + "-distance"),
                )
            }
            val kcal = s.calories
            if (kcal != null && kcal > 0) {
                records += TotalCaloriesBurnedRecord(
                    startTime = s.start, startZoneOffset = startOffset, endTime = s.end, endZoneOffset = endOffset,
                    energy = Energy.kilocalories(kcal.toDouble()), metadata = Metadata(clientRecordId = s.clientId + "-energy"),
                )
            }
        }
        client.insertRecords(records)
        sessions.size
    }

    override suspend fun steps(from: LocalDate, to: LocalDate): Map<LocalDate, Int> = guarded {
        val buckets = client.aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = setOf<AggregateMetric<Long>>(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(from.atStartOfDay(), to.plusDays(1).atStartOfDay()),
                timeRangeSlicer = Period.ofDays(1),
            ),
        )
        buckets.mapNotNull { b ->
            val count = b.result[StepsRecord.COUNT_TOTAL] ?: return@mapNotNull null
            b.startTime.toLocalDate() to count.toInt()
        }.toMap()
    }

    override suspend fun sleep(from: LocalDate, to: LocalDate): Map<LocalDate, Int> = guarded {
        val z = zone()
        val filter = TimeRangeFilter.between(from.minusDays(1).atStartOfDay(z).toInstant(), to.plusDays(1).atTime(LocalTime.MIN).atZone(z).toInstant())
        val byNight = HashMap<LocalDate, Long>()
        var token: String? = null
        do {
            val page = client.readRecords(ReadRecordsRequest(SleepSessionRecord::class, filter, pageToken = token))
            for (r in page.records) {
                val woke = r.endTime.atZone(z).toLocalDate()
                if (woke in from..to) byNight.merge(woke, Duration.between(r.startTime, r.endTime).seconds, Long::plus)
            }
            token = page.pageToken
        } while (token != null)
        byNight.mapValues { it.value.toInt() }
    }
}
