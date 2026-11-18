package nl.tufein.openconnect.core

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** v0.55.0: a report someone can paste into a GitHub issue. It must never carry a key, a token, a name or a health value. */
class DiagnosticsTest {
    private val now = Instant.parse("2026-11-18T19:40:00Z")

    private fun input(log: List<SyncLogEntry> = emptyList()) = DiagnosticInput(
        appVersion = "0.55.0", androidVersion = "14", sdk = 34, device = "Samsung SM-S911B", language = "nl",
        sourcesOn = listOf("garmin", "oura"), aiProvider = "claude", aiKeySet = true,
        days = 30, nights = 28, samples = 4000, activitiesBySource = listOf("garmin-account" to 12, "garmin-ble" to 3),
        log = log,
    )

    @Test
    fun theReportSaysWhatWasRunningAndWhatWasSynced() {
        val report = Diagnostics.report(
            input(
                listOf(
                    SyncLogEntry(Instant.parse("2026-11-18T19:30:00Z"), SyncLog.SOURCE_ACCOUNT, true, added = 3),
                    SyncLogEntry(Instant.parse("2026-11-18T19:00:00Z"), SyncLog.SOURCE_OURA, false, detail = "Oura answered HTTP 401"),
                ),
            ),
            now,
        )
        assertTrue("App: 0.55.0" in report)
        assertTrue("Android 14 (API 34)" in report && "Samsung SM-S911B" in report)
        assertTrue("garmin-account" in report && "+3" in report, "what a sync brought")
        assertTrue("oura" in report && "FAILED" in report && "HTTP 401" in report, "why a sync failed")
        assertTrue("key stored: yes" in report, "that a key exists, never the key")
        assertTrue("12" in report && "28" in report, "how much data, not which values")
    }

    @Test
    fun everyTextThatCouldBeASecretIsRemoved() {
        val secrets = listOf(
            "sk-ant-api03-AAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            "AIzaSyDfakefakefakefakefakefakefake12345",
            "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b",
            "me.private@example.com",
            "SuperSecretRefreshValue",
            "Zm9vYmFyYmF6cXV4MTIzNDU2Nzg5MA==",
            "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJzdGVmYW4ifQ.signaturepartxyz",
        )
        val details = listOf(
            "request failed: Bearer ${secrets[0]}",
            "GET https://apis.garmin.com/wellness-api/rest/dailies?token=${secrets[4]}&uploadStartTimeInSeconds=1 gave 500",
            "key=${secrets[1]} was rejected",
            "user ${secrets[3]} not found",
            "refresh_token: ${secrets[4]}",
            "bad id ${secrets[2]}",
            "payload ${secrets[5]} broken",
            "jwt ${secrets[6]}",
        )
        val log = details.mapIndexed { i, d -> SyncLogEntry(now.minusSeconds(i * 60L), SyncLog.SOURCE_ACCOUNT, false, detail = d) }
        val report = Diagnostics.report(input(log), now)
        for (secret in secrets) assertFalse(secret in report, "the report leaks: $secret")
        assertTrue("apis.garmin.com" in report, "the host of a failed request stays, it explains the failure")
        assertFalse("uploadStartTimeInSeconds" in report, "a query string goes")
    }

    @Test
    fun onlyTheLatestTwentyLinesAreIncluded() {
        val log = (1..40).map { SyncLogEntry(now.minusSeconds(it * 60L), SyncLog.SOURCE_USB, true, added = it) }
        val report = Diagnostics.report(input(log), now)
        assertTrue("+1" in report && "+20" in report)
        assertFalse("+21\n" in report + "\n", "line 21 is cut")
    }

    @Test
    fun redactLeavesPlainSentencesAlone() {
        val text = "Garmin rejected the key: HTTP 401 after 3 tries"
        kotlin.test.assertEquals(text, Diagnostics.redact(text))
    }
}
