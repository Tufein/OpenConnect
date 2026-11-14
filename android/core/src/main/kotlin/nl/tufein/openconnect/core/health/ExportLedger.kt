package nl.tufein.openconnect.core.health

import java.security.MessageDigest

/**
 * What was already sent to Health Connect: for each activity's client id a short fingerprint of what was sent. An
 * automatic export compares against it and sends only what is new or edited, instead of every activity ever recorded
 * after every sync. The app keeps it in its settings; tests use [InMemoryExportLedger].
 */
interface ExportLedger {
    fun fingerprints(): Map<String, String>

    /** Adds (or replaces) entries, keeping the others. */
    fun remember(sent: Map<String, String>)

    companion object {
        /** A short, stable fingerprint of what an activity looks like to Health Connect. */
        fun fingerprint(s: HealthSession): String {
            val text = listOf(s.sportKey, s.name, s.start.epochSecond, s.end.epochSecond, s.distanceM, s.calories ?: -1).joinToString("|")
            return MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).take(8).joinToString("") { "%02x".format(it) }
        }

        /** One line per entry, id and fingerprint separated by a tab, so the phone can keep it in one preference. */
        fun encode(map: Map<String, String>): String = map.entries.joinToString("\n") { (id, f) -> "$id\t$f" }

        /** Damaged lines are skipped: the worst that can happen is that an activity is sent once more. */
        fun decode(text: String?): Map<String, String> = text.orEmpty().lines().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) parts[0] to parts[1] else null
        }.toMap()
    }
}

class InMemoryExportLedger : ExportLedger {
    private val map = LinkedHashMap<String, String>()

    override fun fingerprints(): Map<String, String> = map.toMap()

    override fun remember(sent: Map<String, String>) {
        map.putAll(sent)
    }
}
