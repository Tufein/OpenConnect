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

/** [skipped] could not be sent at all (no duration); [unchanged] were left out of an automatic export because Health Connect already has them. */
data class HealthExportReport(val sent: Int, val skipped: Int, val unchanged: Int = 0)

data class HealthImportReport(val daysFilled: Int, val nightsFilled: Int)

/**
 * Moves data between OpenConnect and Health Connect, in a way that cannot make a mess:
 *  - **Out:** every activity that did not itself come from Health Connect, under a stable id, so repeating it is harmless.
 *    The button sends all of them; the automatic export after a sync sends only those that are new or edited since the
 *    last time (the [ledger] remembers what went out), so a long history is not resent after every sync.
 *  - **In:** only steps and sleep, and only where OpenConnect has nothing yet. What your watch gave stays; Health Connect
 *    fills the gaps. Activities are not read back, because the same run could then exist twice.
 */
class HealthConnectSync(
    private val repo: Repository,
    private val gateway: HealthConnectGateway,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val ledger: ExportLedger = InMemoryExportLedger(),
) {
    companion object {
        const val SOURCE = "healthconnect"
        const val BATCH = 100
        private const val MIN_DURATION_S = 1.0
    }

    /**
     * Sends the activities to Health Connect. With [onlyNew] only those the ledger does not know in this form. Every batch
     * that went through is remembered at once, so a failure halfway costs only the batches after it.
     */
    suspend fun export(onlyNew: Boolean = false): HealthExportReport {
        val all = repo.listActivities(limit = Int.MAX_VALUE).filter { it.source != SOURCE }
        val sessions = all.mapNotNull(::session)
        val known = if (onlyNew) ledger.fingerprints() else emptyMap()
        val prints = sessions.associate { it.clientId to ExportLedger.fingerprint(it) }
        val due = sessions.filter { known[it.clientId] != prints.getValue(it.clientId) }
        var sent = 0
        for (batch in due.chunked(BATCH)) {
            val accepted = gateway.write(batch)
            sent += accepted
            if (accepted == batch.size) ledger.remember(batch.associate { it.clientId to prints.getValue(it.clientId) })
        }
        return HealthExportReport(sent, all.size - sessions.size, sessions.size - due.size)
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
