package nl.tufein.openconnect.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import nl.tufein.openconnect.R

data class SettingsUi(
    val language: String,
    val provider: String,
    val model: String,
    val hasApiKey: Boolean,
    val canStoreApiKey: Boolean,
    val versionName: String,
)

@Composable
fun SettingsScreen(
    ui: SettingsUi,
    onLanguage: (String) -> Unit,
    onProvider: (String) -> Unit,
    onModel: (String) -> Unit,
    onSaveKey: (String) -> Unit,
    onRemoveKey: () -> Unit,
    onDemo: () -> Unit,
    onImport: () -> Unit,
    onClear: () -> Unit,
    onWatch: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    var confirmClear by remember { mutableStateOf(false) }
    var keyField by remember { mutableStateOf("") }
    var modelField by remember(ui.model) { mutableStateOf(ui.model) }
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(stringResource(R.string.tab_settings), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        }
        item {
            SectionTitle(stringResource(R.string.settings_data))
            SurfaceCard {
                Text(stringResource(R.string.settings_data_body), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onWatch, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_watch)) }
                OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_import)) }
                OutlinedButton(onClick = onDemo, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_demo)) }
                TextButton(onClick = { confirmClear = true }) {
                    Text(stringResource(R.string.action_clear), color = MaterialTheme.colorScheme.error)
                }
            }
        }
        item {
            SectionTitle(stringResource(R.string.settings_ai))
            SurfaceCard {
                Text(stringResource(R.string.settings_ai_body), style = MaterialTheme.typography.bodyMedium)
                for ((value, label) in listOf(
                    "auto" to R.string.provider_auto,
                    "template" to R.string.provider_template,
                    "anthropic" to R.string.provider_claude,
                )) {
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = ui.provider == value, role = Role.RadioButton, onClick = { onProvider(value) }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = ui.provider == value, onClick = null)
                        Text(stringResource(label), modifier = Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text(stringResource(R.string.report_language), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = ui.language == "en", onClick = { onLanguage("en") }, label = { Text("English") })
                    FilterChip(selected = ui.language == "nl", onClick = { onLanguage("nl") }, label = { Text("Nederlands") })
                }
                if (!ui.canStoreApiKey) {
                    Text(stringResource(R.string.key_unavailable), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                } else {
                    OutlinedTextField(
                        value = keyField,
                        onValueChange = { keyField = it },
                        label = { Text(stringResource(if (ui.hasApiKey) R.string.api_key_saved else R.string.api_key)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onSaveKey(keyField); keyField = "" }, enabled = keyField.isNotBlank()) {
                            Text(stringResource(R.string.save))
                        }
                        if (ui.hasApiKey) {
                            OutlinedButton(onClick = onRemoveKey) { Text(stringResource(R.string.remove_key)) }
                        }
                    }
                }
                OutlinedTextField(
                    value = modelField,
                    onValueChange = { modelField = it; onModel(it) },
                    label = { Text(stringResource(R.string.model)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.privacy_ai), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            SectionTitle(stringResource(R.string.settings_about))
            SurfaceCard {
                Text("OpenConnect ${ui.versionName}", style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.about_body), style = MaterialTheme.typography.bodyMedium)
                Text("github.com/Tufein/OpenConnect", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.action_clear)) },
            text = { Text(stringResource(R.string.clear_body)) },
            confirmButton = { Button(onClick = { confirmClear = false; onClear() }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}


