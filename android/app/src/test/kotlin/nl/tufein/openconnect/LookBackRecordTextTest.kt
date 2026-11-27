package nl.tufein.openconnect

import nl.tufein.openconnect.core.RecordEvent
import nl.tufein.openconnect.core.RecordKind
import nl.tufein.openconnect.core.Sport
import nl.tufein.openconnect.ui.recordValues
import java.time.LocalDate
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

/** v1.13.12: how a beaten record reads in the Look back list, on the JVM without a screen. */
class LookBackRecordTextTest {
    private fun event(kind: RecordKind, value: Double, previous: Double) = RecordEvent(
        sport = Sport.RUNNING,
        kind = kind,
        value = value,
        previousValue = previous,
        previousDate = LocalDate.of(2023, 11, 30),
        date = LocalDate.of(2024, 1, 18),
        activityId = "a1",
        name = "Run",
    )

    @Test
    fun aRecordThatDiffersOnScreenReadsInTheUsualPrecision() {
        assertEquals("9.60 km" to "9.56 km", recordValues(event(RecordKind.LONGEST_DISTANCE, 9_600.0, 9_560.0), Locale.US))
        assertEquals("46 m" to "44 m", recordValues(event(RecordKind.MOST_ELEVATION, 46.0, 44.0), Locale.US))
        assertEquals("53:00" to "52:00", recordValues(event(RecordKind.LONGEST_DURATION, 3_180.0, 3_120.0), Locale.US))
    }

    @Test
    fun aStepSmallerThanTheRoundingGetsOneMoreDigitSoItDoesNotLookLikeNothing() {
        // 50.4 m against 50.2 m would read "50 m, before 50 m".
        assertEquals("50.4 m" to "50.2 m", recordValues(event(RecordKind.MOST_ELEVATION, 50.4, 50.2), Locale.US))
        // 9,601 m against 9,598 m would read "9.60 km, before 9.60 km".
        assertEquals("9.601 km" to "9.598 km", recordValues(event(RecordKind.LONGEST_DISTANCE, 9_601.0, 9_598.0), Locale.US))
    }

    @Test
    fun theDecimalSignFollowsTheLocale() {
        assertEquals("50,4 m" to "50,2 m", recordValues(event(RecordKind.MOST_ELEVATION, 50.4, 50.2), Locale.GERMANY))
    }
}
