package nl.tufein.openconnect.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import nl.tufein.openconnect.R
import nl.tufein.openconnect.core.Sport
import nl.tufein.openconnect.core.StoredReport

fun sportLabel(sport: Sport): Int = when (sport) {
    Sport.RUNNING -> R.string.sport_running
    Sport.CYCLING -> R.string.sport_cycling
    Sport.SWIMMING -> R.string.sport_swimming
    Sport.WALKING -> R.string.sport_walking
    Sport.HIKING -> R.string.sport_hiking
    Sport.STRENGTH -> R.string.sport_strength
    Sport.YOGA -> R.string.sport_yoga
    Sport.OTHER -> R.string.sport_other
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(top = 8.dp),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
fun SurfaceCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, unit: String? = null) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                if (unit != null) {
                    Text(
                        " $unit",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
            }
        }
    }
}

/** Two tiles side by side; a missing second tile leaves the first at half width. */
@Composable
fun TileRow(first: @Composable (Modifier) -> Unit, second: (@Composable (Modifier) -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        first(Modifier.weight(1f))
        if (second != null) second(Modifier.weight(1f)) else androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
    }
}

@Composable
fun EmptyState(onDemo: () -> Unit, onImport: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            stringResource(R.string.empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onDemo, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_demo)) }
        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_import)) }
    }
}

/** An AI (or offline template) report with a button to write or refresh it. */
@Composable
fun ReportCard(
    report: StoredReport?,
    busy: Boolean,
    onGenerate: (force: Boolean) -> Unit,
    usingClaude: Boolean,
    modifier: Modifier = Modifier,
) {
    SurfaceCard(modifier) {
        Text(stringResource(R.string.report_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(if (usingClaude) R.string.report_via_claude else R.string.report_via_template),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (report != null) {
            MarkdownText(report.text)
            Text(
                stringResource(R.string.report_footer, report.provider, report.model ?: "–"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (busy) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.report_writing), style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            Button(onClick = { onGenerate(report != null) }) {
                Text(stringResource(if (report == null) R.string.report_generate else R.string.report_regenerate))
            }
        }
    }
}
