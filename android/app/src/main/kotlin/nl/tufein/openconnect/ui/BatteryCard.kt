package nl.tufein.openconnect.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.tufein.openconnect.R
import nl.tufein.openconnect.data.SettingsStore
import java.time.Instant

/** What one thing this app talks to last said about its charge; [level] null means it never said. */
data class BatteryReading(val level: Int? = null, val at: Long = 0L)

/** The watch and the ring side by side, for the card on the front page and on the Watch screen. */
data class BatteriesUi(
    val watch: BatteryReading = BatteryReading(),
    val ring: BatteryReading = BatteryReading(),
    /** Oura refused the ring's charge; the card explains that a new login is needed. */
    val ringRefused: Boolean = false,
)

/** Reads what was kept in the settings; -1 and 0 mean "never reported", which is shown as a dash. */
fun SettingsStore.batteries(): BatteriesUi = BatteriesUi(
    watch = BatteryReading(watchBattery.takeIf { it in 0..100 }, watchBatteryAt),
    ring = BatteryReading(ringBattery.takeIf { it in 0..100 }, ringBatteryAt),
    ringRefused = ringBatteryRefused,
)

/**
 * The charge of the watch and the ring, with when it was last reported. Oura reports the ring's charge on every sync;
 * the watch does it over Bluetooth when it connects. WHOOP and Hume do not report one, which is why only these two do.
 */
@Composable
fun BatteryCard(
    ui: BatteriesUi = BatteriesUi(),
    now: Instant = Instant.now(),
    modifier: Modifier = Modifier,
    /** False for someone without a Garmin watch: no row for a device they do not own. */
    showWatch: Boolean = true,
    /** False for someone without an Oura ring. */
    showRing: Boolean = true,
    /** Opens the Oura tab, so the ring's refused charge can be allowed with a new login. */
    onOpenOura: (() -> Unit)? = null,
) {
    FoldableCard("battery", stringResource(R.string.battery_title), modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (showWatch) Reading(stringResource(R.string.battery_watch), ui.watch, now)
            if (showRing) Reading(stringResource(R.string.battery_ring), ui.ring, now)
            if (showRing && ui.ringRefused && ui.ring.level == null) {
                Text(
                    stringResource(R.string.battery_ring_refused),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                if (onOpenOura != null) {
                    SecondaryButton(onClick = onOpenOura) { Text(stringResource(R.string.battery_ring_open)) }
                }
            }
        }
    }
}

@Composable
private fun Reading(label: String, reading: BatteryReading, now: Instant) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                reading.level?.let { "$it%" } ?: "—",
                style = MaterialTheme.typography.titleMedium,
                color = if (reading.level == null) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
            )
            Text(
                if (reading.at > 0) {
                    stringResource(R.string.battery_updated, agoText(Instant.ofEpochMilli(reading.at), now))
                } else {
                    stringResource(R.string.battery_never)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
