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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import nl.tufein.openconnect.R
import nl.tufein.openconnect.watch.BleSensor
import nl.tufein.openconnect.watch.LivePhase
import nl.tufein.openconnect.watch.LiveUi

/**
 * The controls of a live heart-rate session: the scan button, the sensors that were found, the connection status and,
 * once beats arrive, the current pulse with the lowest, average, highest and the time. The Watch screen and the card on
 * the front page share this, so both always offer the same steps and there is only one live state behind them.
 *
 * [startLabel] lets the front page say "Start live heart rate" where the Watch screen says "Search for sensors".
 * [showStartWhenStopped] is false on the front page: there the recording is saved first, so a tap on "start" cannot
 * throw away an unsaved session. [bpmTag] marks the pulse for tests; the pulse and its "bpm" are one spoken number.
 */
@Composable
fun LiveControls(
    live: LiveUi,
    permissionDenied: Boolean,
    onScan: () -> Unit,
    onConnect: (BleSensor) -> Unit,
    onStop: () -> Unit,
    onSave: () -> Unit,
    startLabel: Int = R.string.watch_live_search,
    showStartWhenStopped: Boolean = true,
    /** One row of lowest, average and highest instead of two, for the smaller card on the front page. */
    compactStats: Boolean = false,
    bpmTag: String? = null,
    valueColor: Color? = null,
) {
    val busy = live.phase == LivePhase.SCANNING || live.phase == LivePhase.CONNECTING || live.phase == LivePhase.STREAMING ||
        live.phase == LivePhase.RECONNECTING
    val canStart = live.phase == LivePhase.IDLE || live.phase == LivePhase.SCANNING ||
        (live.phase == LivePhase.STOPPED && (showStartWhenStopped || !live.hasData))
    if (canStart) {
        PrimaryButton(
            onClick = if (live.phase == LivePhase.SCANNING) onStop else onScan,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(if (live.phase == LivePhase.SCANNING) R.string.watch_live_stop else startLabel))
        }
    }
    if (permissionDenied) {
        Text(stringResource(R.string.watch_live_permission), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    if (live.phase == LivePhase.SCANNING) {
        Text(
            stringResource(if (live.sensors.isEmpty()) R.string.watch_live_none else R.string.watch_live_searching),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        for (sensor in live.sensors) {
            SecondaryButton(onClick = { onConnect(sensor) }, modifier = Modifier.fillMaxWidth()) { Text(sensor.name) }
        }
    }
    if (live.phase == LivePhase.CONNECTING) {
        Text(stringResource(R.string.watch_live_connecting, live.sensorName.orEmpty()), style = MaterialTheme.typography.bodyMedium)
        PixelProgress(Modifier.fillMaxWidth())
    }
    if (live.phase == LivePhase.RECONNECTING) {
        Text(stringResource(R.string.watch_live_reconnecting, live.reconnectAttempt), style = MaterialTheme.typography.bodyMedium)
        PixelProgress(Modifier.fillMaxWidth())
    }
    if (live.phase == LivePhase.STREAMING || live.phase == LivePhase.RECONNECTING || live.hasData) {
        Text(
            when (live.phase) {
                LivePhase.STREAMING -> stringResource(R.string.watch_live_connected, live.sensorName.orEmpty())
                LivePhase.RECONNECTING -> stringResource(R.string.watch_live_connected, live.sensorName.orEmpty())
                else -> stringResource(R.string.watch_live_stopped)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val pulse = live.bpm
        val pulseDescription = pulse?.let { stringResource(R.string.watch_live_bpm_description, it) }
        Row(
            Modifier
                .semantics(mergeDescendants = true) {
                    if (pulseDescription != null) contentDescription = pulseDescription
                }
                .then(if (bpmTag != null) Modifier.testTag(bpmTag) else Modifier),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(pulse?.toString() ?: "–", style = MaterialTheme.typography.displayLarge, color = valueColor ?: MaterialTheme.colorScheme.primary)
            Text("bpm", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 10.dp))
        }
        if (compactStats) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CompactStat(stringResource(R.string.watch_live_min), live.min?.toString() ?: "–", Modifier.weight(1f))
                CompactStat(stringResource(R.string.watch_live_avg), live.avg?.toString() ?: "–", Modifier.weight(1f))
                CompactStat(stringResource(R.string.watch_live_max), live.max?.toString() ?: "–", Modifier.weight(1f))
            }
        } else {
            TileRow(
                { StatTile(stringResource(R.string.watch_live_min), live.min?.toString() ?: "–", it) },
                { StatTile(stringResource(R.string.watch_live_avg), live.avg?.toString() ?: "–", it) },
            )
            TileRow(
                { StatTile(stringResource(R.string.watch_live_max), live.max?.toString() ?: "–", it) },
                { StatTile(stringResource(R.string.watch_live_hrv), live.rmssdMs?.toString() ?: "–", it, unit = "ms") },
            )
        }
        Text(
            stringResource(R.string.watch_live_time) + ": " + Fmt.duration(live.seconds.toDouble()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (live.error != null && live.phase == LivePhase.STOPPED) {
        Text(stringResource(R.string.watch_live_disconnected, live.error), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    if (busy && live.phase != LivePhase.SCANNING) {
        SecondaryButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.watch_live_stop)) }
    }
    if (live.phase == LivePhase.STOPPED && live.hasData) {
        if (live.saved) {
            Text(stringResource(R.string.watch_live_saved), style = MaterialTheme.typography.bodyMedium)
        } else {
            PrimaryButton(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.watch_live_save)) }
        }
    }
}

/** One of the three beats in the compact front-page row: a small label over a serif value, without a second panel. */
@Composable
private fun CompactStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
        )
        Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}
