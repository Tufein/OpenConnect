package nl.tufein.openconnect.core.health

import nl.tufein.openconnect.core.Activity
import nl.tufein.openconnect.core.DailySummary
import nl.tufein.openconnect.core.Repository
import nl.tufein.openconnect.core.SleepSummary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** An activity as Health Connect needs it. [clientId] is stable, so sending the same activity again replaces it instead of duplicating it. */
data class HealthSession(
    val clientId: String,
    val sportKey: String,
    val name: String,
    val start: Instant,
    val end: Instant,
    val distanceM: Double,
    val calories: Int?,
)

/** What this phone says about Health Connect. */
enum class HealthAccess {
    /** Health Connect is not on this phone, or must be updated first. */
    UNAVAILABLE,

    /** It is there, but the person has not allowed OpenConnect to use it yet. */
    NEEDS_PERMISSION,

    READY,
}

/** The phone's Health Connect. The real one is in the app module; tests use a fake. Errors are thrown as [java.io.IOException]. */
interface HealthConnectGateway {
    suspend fun access(): HealthAccess

    /** Writes the sessions, replacing earlier copies with the same client id. Returns how many were accepted. */
    suspend fun write(sessions: List<HealthSession>): Int

    /** Steps per day from [from] to [to] inclusive, for days that have any. */
    suspend fun steps(from: LocalDate, to: LocalDate): Map<LocalDate, Int>

    /** Seconds slept per night from [from] to [to] inclusive, by the day the person woke up. */
    suspend fun sleep(from: LocalDate, to: LocalDate): Map<LocalDate, Int>
}

data class HealthExportReport(val sent: Int, val skipped: Int)

data class HealthImportReport(val daysFilled: Int, val nightsFilled: Int)

/**
 * Moves data between OpenConnect and Health Connect, in a way that cannot make a mess:
 *  - **Out:** every activity that did not itself come from Health Connect, under a stable id, so repeating it is harmless.
 *  - **In:** only steps and sleep, and only where OpenConnect has nothing yet. What your watch gave stays; Health Connect
 *    fills the gaps. Activities are not read back, because the same run could then exist twice.
 */
class HealthConnectSync(private val repo: Repository, private val gateway: HealthConnectGateway, private val zone: ZoneId = ZoneId.systemDefault()) {
    companion object {
        const val SOURCE = "healthconnect"
        const val BATCH = 100
        private const val MIN_DURATION_S = 1.0
    }

    suspend fun export(): HealthExportReport {
        val all = repo.listActivities(limit = Int.MAX_VALUE).filter { it.source != SOURCE }
        val sessions = all.mapNotNull(::session)
        var sent = 0
        for (batch in sessions.chunked(BATCH)) sent += gateway.write(batch)
        return HealthExportReport(sent, all.size - sessions.size)
    }

    private fun session(a: Activity): HealthSession? {
        if (a.durationS < MIN_DURATION_S) return null // Health Connect refuses a session that ends when it starts
        return HealthSession(
            clientId = "openconnect-${a.id}",
            sportKey = a.sport.key,
            name = a.name,
            start = a.startTime,
            end = a.startTime.plusMillis((a.durationS * 1000).toLong()),
            distanceM = a.distanceM,
            calories = a.calories,
        )
    }

    suspend fun import(days: Int = 30, today: LocalDate = LocalDate.now(zone)): HealthImportReport {
        val from = today.minusDays(days.toLong() - 1)
        var daysFilled = 0
        for ((date, steps) in gateway.steps(from, today)) {
            if (steps <= 0) continue
            val existing = repo.listDaily(date, date).firstOrNull()
            if (existing == null) {
                repo.upsertDaily(DailySummary(date = date, steps = steps))
                daysFilled++
            } else if (existing.steps == 0) {
                repo.upsertDaily(existing.copy(steps = steps))
                daysFilled++
            }
        }
        var nightsFilled = 0
        for ((date, seconds) in gateway.sleep(from, today)) {
            if (seconds <= 0) continue
            if (repo.listSleep(date, date).isEmpty()) {
                repo.upsertSleep(SleepSummary(date = date, durationS = seconds))
                nightsFilled++
            }
        }
        return HealthImportReport(daysFilled, nightsFilled)
    }
}

/** For phones without Health Connect and for tests that do not care. */
object NoHealthConnect : HealthConnectGateway {
    override suspend fun access() = HealthAccess.UNAVAILABLE

    override suspend fun write(sessions: List<HealthSession>) = 0

    override suspend fun steps(from: LocalDate, to: LocalDate) = emptyMap<LocalDate, Int>()

    override suspend fun sleep(from: LocalDate, to: LocalDate) = emptyMap<LocalDate, Int>()
}
