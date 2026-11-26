package nl.tufein.openconnect.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.tufein.openconnect.R
import nl.tufein.openconnect.watch.BleSensor
import nl.tufein.openconnect.watch.LivePhase
import nl.tufein.openconnect.watch.LiveUi

/**
 * The live heart rate on the front page: one quiet card that starts the same session as the Watch screen and shows it
 * there. It stays compact while nothing runs; once a watch streams, the pulse, the source, the lowest/average/highest
 * beat and the time take the card over, with a clear "live" mark and a stop. A scan result can be picked here, so the
 * Watch screen is not needed for the usual path; the small link to it stays for the whole Watch card.
 *
 * The card only exists for someone who uses Garmin (Home checks that) and follows the front-page switch. The live
 * connection itself lives in [nl.tufein.openconnect.watch.WatchController], so the Watch screen and this card never
 * hold two sessions, and changing tabs does not end the recording.
 */
@Composable
fun HomeLiveHeartRateCard(
    live: LiveUi,
    permissionDenied: Boolean,
    onScan: () -> Unit,
    onConnect: (BleSensor) -> Unit,
    onStop: () -> Unit,
    onSave: () -> Unit,
    onOpenWatch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = OpenConnectColors.metrics.heart
    if (LocalStyle.current == Style.EDITORIAL) {
        EditorialCard(modifier = modifier.testTag("home-live-card"), accent = accent) {
            LiveCardContent(live, permissionDenied, onScan, onConnect, onStop, onSave, onOpenWatch, accent)
        }
    } else {
        SurfaceCard(modifier = modifier.testTag("home-live-card"), accent = accent) {
            LiveCardContent(live, permissionDenied, onScan, onConnect, onStop, onSave, onOpenWatch, accent)
        }
    }
}

@Composable
private fun LiveCardContent(
    live: LiveUi,
    permissionDenied: Boolean,
    onScan: () -> Unit,
    onConnect: (BleSensor) -> Unit,
    onStop: () -> Unit,
    onSave: () -> Unit,
    onOpenWatch: () -> Unit,
    accent: Color,
) {
    val colors = MaterialTheme.colorScheme
    if (LocalStyle.current == Style.EDITORIAL) {
        EditorialSectionTitle(
            stringResource(R.string.watch_live_title),
            trailing = { if (live.phase == LivePhase.STREAMING) LiveBadge() },
        )
    } else {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.watch_live_title),
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (live.phase == LivePhase.STREAMING) LiveBadge()
        }
    }
    if (live.phase == LivePhase.IDLE) {
        Text(
            stringResource(R.string.home_live_idle_body),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
    }
    LiveControls(
        live = live,
        permissionDenied = permissionDenied,
        onScan = onScan,
        onConnect = onConnect,
        onStop = onStop,
        onSave = onSave,
        startLabel = R.string.home_live_start,
        // On the front page an unsaved recording is never thrown away by the button above it: save first, then start again.
        showStartWhenStopped = false,
        // One row of lowest/average/highest keeps the stop within reach on a phone; HRV stays on the Watch screen.
        compactStats = true,
        bpmTag = "home-live-bpm",
        valueColor = accent,
    )
    if (live.phase == LivePhase.STOPPED && live.hasData && live.saved) {
        SecondaryButton(onClick = onScan, modifier = Modifier.fillMaxWidth().testTag("home-live-start-again")) {
            Text(stringResource(R.string.home_live_start_again))
        }
    }
    if (live.phase == LivePhase.IDLE || (live.phase == LivePhase.STOPPED && !live.hasData)) {
        QuietButton(onClick = onOpenWatch, modifier = Modifier.fillMaxWidth().testTag("home-live-watch")) {
            Text(
                stringResource(R.string.home_live_open_watch),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The one word that says a heartbeat is arriving right now, with a dot in the heart colour. */
@Composable
private fun LiveBadge() {
    val accent = OpenConnectColors.metrics.heart
    val shape = if (LocalStyle.current == Style.EDITORIAL) PillShape else MaterialTheme.shapes.small
    Row(
        Modifier
            .clip(shape)
            .background(accent.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val dot: Shape = if (LocalStyle.current == Style.RETRO) RectangleShape else CircleShape
        Box(Modifier.size(7.dp).background(accent, dot))
        Text(stringResource(R.string.home_live_badge), style = MaterialTheme.typography.labelSmall, color = accent)
    }
}
