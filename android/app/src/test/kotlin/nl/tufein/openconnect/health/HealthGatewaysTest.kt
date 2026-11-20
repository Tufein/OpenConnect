package nl.tufein.openconnect.health

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import nl.tufein.openconnect.core.health.HealthAccess
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/** Robolectric 4.18 has no Health Connect service; a phone without one must report "unavailable", not crash. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HealthGatewaysTest {
    @Test
    fun aPhoneWithoutTheHealthConnectServiceReportsUnavailable(): Unit = runBlocking<Unit> {
        assertEquals(
            HealthAccess.UNAVAILABLE,
            AndroidHealthConnectGateway(ApplicationProvider.getApplicationContext()).access(),
        )
        assertEquals(
            HealthAccess.UNAVAILABLE,
            AndroidHumeGateway(ApplicationProvider.getApplicationContext()).access(),
        )
    }
}
