package nl.tufein.openconnect.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.tufein.openconnect.R
import nl.tufein.openconnect.garmin.Ago
import nl.tufein.openconnect.garmin.SyncBadge
import nl.tufein.openconnect.garmin.ago
import java.time.Instant

/** "5 min ago" in the phone's language. */
@Composable
fun agoText(then: Instant, now: Instant): String = when (val a = ago(then, now)) {
    Ago.JustNow -> stringResource(R.string.ago_now)
    is Ago.Minutes -> stringResource(R.string.ago_minutes, a.n)
    is Ago.Hours -> stringResource(R.string.ago_hours, a.n)
    is Ago.Days -> stringResource(R.string.ago_days, a.n)
}

/**
 * The sync status at the top of Home, like the one in Garmin Connect: when the last sync was, or how far a running one
 * is, and a tap does the obvious thing ([onClick] decides: sync now, or open the Garmin screen).
 */
@Composable
fun SyncPill(badge: SyncBadge, now: Instant, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val ok = MaterialTheme.colorScheme.primary
    val warn = MaterialTheme.colorScheme.secondary
    val bad = MaterialTheme.colorScheme.error
    val quiet = MaterialTheme.colorScheme.outline
    val (dot: Color, text: String) = when (badge) {
        SyncBadge.NoAccount -> quiet to stringResource(R.string.sync_connect)
        is SyncBadge.Syncing -> warn to if (badge.cancelling) {
            stringResource(R.string.sync_cancelling)
        } else {
            stringResource(R.string.sync_running, badge.done.coerceAtMost(badge.total), badge.total)
        }
        SyncBadge.KeyRejected -> bad to stringResource(R.string.sync_key_rejected)
        is SyncBadge.Failed -> bad to if (badge.lastSync != null) {
            stringResource(R.string.sync_failed_last, agoText(badge.lastSync, now))
        } else {
            stringResource(R.string.sync_failed)
        }
        is SyncBadge.Fresh -> ok to stringResource(R.string.sync_fresh, agoText(badge.lastSync, now))
        SyncBadge.NeverSynced -> quiet to stringResource(R.string.sync_never)
    }
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (badge is SyncBadge.Syncing) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = warn)
            } else {
                Box(Modifier.size(9.dp).background(dot, RoundedCornerShape(50)))
            }
            Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}
