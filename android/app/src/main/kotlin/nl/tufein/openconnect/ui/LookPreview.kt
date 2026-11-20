package nl.tufein.openconnect.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nl.tufein.openconnect.R

/**
 * A small, non-interactive dashboard in the theme that is chosen right now: the card shape and shadow, the title and
 * number type, a signal bar and a pill. It sits in Settings, Appearance and on the first-start screen, so a theme can be
 * judged before it is kept instead of from two little colour squares.
 */
@Composable
fun LookPreview(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Panel(modifier.fillMaxWidth(), tint = colors.primary, padding = 16.dp, spacing = 10.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.oura_readiness),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text("82", style = MaterialTheme.typography.displaySmall, color = colors.primary)
        }
        SegmentBar(0.82f, colors.primary, Modifier.fillMaxWidth().height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            // A pill like the real chips and buttons, without a click action: this is a picture, not a control.
            Row(
                Modifier.background(colors.primary, CircleShape).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.oura_sleep), style = MaterialTheme.typography.labelLarge, color = colors.onPrimary, maxLines = 1)
            }
            Text("7:12", style = MaterialTheme.typography.titleMedium, color = colors.onSurface, maxLines = 1)
            Spacer(Modifier.width(2.dp))
            ColorDot(colors.secondary)
        }
    }
}
