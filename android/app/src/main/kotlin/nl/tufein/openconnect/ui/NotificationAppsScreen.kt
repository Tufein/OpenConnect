// OpenConnect, SPDX-License-Identifier: AGPL-3.0-or-later
package nl.tufein.openconnect.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.tufein.openconnect.R
import nl.tufein.openconnect.notify.InstalledApp

/**
 * The apps whose notifications go to the watch. The chosen ones come first, so what is switched on is easy to find and
 * switch off again; the rest follow alphabetically. A chosen app that is no longer installed stays listed by its package
 * name, so it can still be removed.
 */
@Composable
fun NotificationAppsScreen(
    /** Null while Android is still being asked. */
    apps: List<InstalledApp>?,
    chosen: Set<String>,
    onBack: () -> Unit,
    onChoose: (packageName: String, on: Boolean) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val rows = apps?.let { installed ->
        val known = installed.map { it.packageName }.toSet()
        val gone = (chosen - known).map { InstalledApp(it, it) }
        (installed + gone).sortedWith(compareByDescending<InstalledApp> { it.packageName in chosen }.thenBy { it.label.lowercase() })
    }
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            PixelIconButton(onClick = onBack) {
                PixelIcon(PixelIcons.ArrowLeft, contentDescription = stringResource(R.string.back))
            }
            Text(stringResource(R.string.forward_apps_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Text(
                stringResource(R.string.forward_apps_body),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
            )
        }
        when {
            rows == null -> item { Text(stringResource(R.string.forward_apps_loading), style = MaterialTheme.typography.bodyMedium) }
            rows.isEmpty() -> item { Text(stringResource(R.string.forward_apps_none), style = MaterialTheme.typography.bodyMedium) }
            else -> items(rows, key = { it.packageName }) { app ->
                SwitchRow(
                    checked = app.packageName in chosen,
                    onCheckedChange = { on -> onChoose(app.packageName, on) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(app.label, style = MaterialTheme.typography.bodyMedium)
                    if (app.label != app.packageName) {
                        Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
