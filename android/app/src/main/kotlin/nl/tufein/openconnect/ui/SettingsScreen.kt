package nl.tufein.openconnect.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import nl.tufein.openconnect.R
import nl.tufein.openconnect.ai.AiKeyCheck
import nl.tufein.openconnect.ai.AiKeyProblem
import nl.tufein.openconnect.ai.AiUi
import nl.tufein.openconnect.ai.AiVendorUi
import nl.tufein.openconnect.core.WeeklyGoals
import nl.tufein.openconnect.core.reports.AiVendor

data class SettingsUi(
    val ai: AiUi,
    /** "system", "dark" or "light". */
    val theme: String,
    val versionName: String,
    val goals: WeeklyGoals = WeeklyGoals(),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    ui: SettingsUi,
    onTheme: (String) -> Unit,
    onGoals: (WeeklyGoals) -> Unit,
    onLanguage: (String) -> Unit,
    onChoice: (String) -> Unit,
    onSaveKey: (AiVendor, String) -> Unit,
    onCheckKey: (AiVendor) -> Unit,
    onForgetKey: (AiVendor) -> Unit,
    onModel: (AiVendor, String) -> Unit,
    onDemo: () -> Unit,
    onImport: () -> Unit,
    onExportDaily: () -> Unit,
    onExportActivities: () -> Unit,
    onClear: () -> Unit,
    onWatch: () -> Unit,
    onGarmin: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    var confirmClear by remember { mutableStateOf(false) }
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(stringResource(R.string.tab_settings), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        }
        item {
            SectionTitle(stringResource(R.string.settings_appearance))
            SurfaceCard {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((mode, label) in listOf("system" to R.string.theme_system, "dark" to R.string.theme_dark, "light" to R.string.theme_light)) {
                        FilterChip(selected = ui.theme == mode, onClick = { onTheme(mode) }, label = { Text(stringResource(label)) })
                    }
                }
            }
        }
        item {
            SectionTitle(stringResource(R.string.settings_goals))
            SurfaceCard {
                Text(stringResource(R.string.settings_goals_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                GoalStepper(stringResource(R.string.goal_intensity), ui.goals.intensityMinutes, "min", 10) { onGoals(ui.goals.copy(intensityMinutes = it)) }
                GoalStepper(stringResource(R.string.goal_distance), ui.goals.distanceKm, "km", 5) { onGoals(ui.goals.copy(distanceKm = it)) }
                GoalStepper(stringResource(R.string.goal_activities), ui.goals.activities, "", 1) { onGoals(ui.goals.copy(activities = it)) }
            }
        }
        item {
            SectionTitle(stringResource(R.string.settings_data))
            SurfaceCard {
                Text(stringResource(R.string.settings_data_body), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onGarmin, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_garmin)) }
                Button(onClick = onWatch, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_watch)) }
                OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_import)) }
                OutlinedButton(onClick = onDemo, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_demo)) }
                TextButton(onClick = { confirmClear = true }) {
                    Text(stringResource(R.string.action_clear), color = MaterialTheme.colorScheme.error)
                }
            }
        }
        item {
            SectionTitle(stringResource(R.string.settings_export))
            SurfaceCard {
                Text(stringResource(R.string.settings_export_body), style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(onClick = onExportDaily) { Text(stringResource(R.string.export_daily)) }
                    OutlinedButton(onClick = onExportActivities) { Text(stringResource(R.string.export_activities)) }
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
                    AiVendor.CLAUDE.id to R.string.provider_claude,
                    AiVendor.GEMINI.id to R.string.provider_gemini,
                )) {
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = ui.ai.choice == value, role = Role.RadioButton, onClick = { onChoice(value) }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = ui.ai.choice == value, onClick = null)
                        Text(stringResource(label), modifier = Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text(stringResource(R.string.report_language), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = ui.ai.language == "en", onClick = { onLanguage("en") }, label = { Text("English") })
                    FilterChip(selected = ui.ai.language == "nl", onClick = { onLanguage("nl") }, label = { Text("Nederlands") })
                }
                Text(stringResource(R.string.privacy_ai), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(ui.ai.vendors, key = { it.vendor.id }) { vendor ->
            VendorCard(ui.ai.canStoreKeys, vendor, onSaveKey, onCheckKey, onForgetKey, onModel)
        }
        item {
            SectionTitle(stringResource(R.string.settings_about))
            SurfaceCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LogoMark(size = 40.dp)
                    Text("OpenConnect ${ui.versionName}", style = MaterialTheme.typography.titleMedium)
                }
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

/** One AI provider: where to make a key, the key field (it is never shown again), a check, and the model. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VendorCard(
    canStoreKeys: Boolean,
    ui: AiVendorUi,
    onSaveKey: (AiVendor, String) -> Unit,
    onCheckKey: (AiVendor) -> Unit,
    onForgetKey: (AiVendor) -> Unit,
    onModel: (AiVendor, String) -> Unit,
) {
    val context = LocalContext.current
    val vendor = ui.vendor
    val name = vendor.displayName
    var keyField by remember(vendor) { mutableStateOf("") }
    var modelField by remember(vendor, ui.model) { mutableStateOf(ui.model) }
    SurfaceCard(accent = if (ui.check == AiKeyCheck.OK) MaterialTheme.colorScheme.primary else null) {
        Text(stringResource(R.string.vendor_title, name), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.vendor_body, name), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { openLink(context, vendor.keyPage) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.vendor_open_key_page, name))
        }
        if (!canStoreKeys) {
            Text(stringResource(R.string.key_unavailable), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        } else {
            OutlinedTextField(
                value = keyField,
                onValueChange = { keyField = it },
                label = { Text(stringResource(if (ui.hasKey) R.string.vendor_key_saved else R.string.vendor_key, name)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            // Three buttons do not fit side by side on a narrow phone (and not in Dutch): they wrap onto a second line.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = { onSaveKey(vendor, keyField); keyField = "" }, enabled = keyField.isNotBlank()) {
                    Text(stringResource(R.string.save_key, name))
                }
                if (ui.hasKey) {
                    OutlinedButton(onClick = { onCheckKey(vendor) }, enabled = ui.check != AiKeyCheck.CHECKING) {
                        Text(stringResource(R.string.check_key))
                    }
                    OutlinedButton(onClick = { onForgetKey(vendor) }) { Text(stringResource(R.string.remove_key)) }
                }
            }
            KeyStatus(ui, name)
        }
        Text(stringResource(R.string.model), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = modelField,
            onValueChange = { modelField = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(vendor.defaultModel) },
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(vendor.suggestedModels) { model ->
                FilterChip(
                    selected = modelField == model,
                    onClick = { modelField = model; onModel(vendor, model) },
                    label = { Text(model) },
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = { onModel(vendor, modelField) }, enabled = modelField.trim() != ui.model) {
                Text(stringResource(R.string.save_model))
            }
            if (ui.model != vendor.defaultModel) {
                TextButton(onClick = { modelField = vendor.defaultModel; onModel(vendor, "") }) {
                    Text(stringResource(R.string.default_model))
                }
            }
        }
    }
}

@Composable
private fun KeyStatus(ui: AiVendorUi, name: String) {
    val colors = MaterialTheme.colorScheme
    val (text, color) = when {
        ui.problem == AiKeyProblem.MALFORMED -> stringResource(R.string.vendor_key_malformed, name) to colors.error
        ui.problem == AiKeyProblem.CANNOT_STORE -> stringResource(R.string.key_unavailable) to colors.error
        !ui.hasKey -> return
        ui.check == AiKeyCheck.CHECKING -> {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.padding(2.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.vendor_key_checking, name), style = MaterialTheme.typography.bodySmall)
            }
            return
        }
        ui.check == AiKeyCheck.OK -> stringResource(R.string.vendor_key_ok, name) to colors.primary
        ui.check == AiKeyCheck.REJECTED -> stringResource(R.string.vendor_key_rejected, name) to colors.error
        ui.check == AiKeyCheck.FAILED -> stringResource(R.string.vendor_key_unreachable, name) to colors.secondary
        else -> stringResource(R.string.vendor_key_unknown, name) to colors.onSurfaceVariant
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = color)
        if (ui.detail != null && (ui.check == AiKeyCheck.REJECTED || ui.check == AiKeyCheck.FAILED)) {
            Text(ui.detail, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

/** Opens [url] in the phone's browser; a phone without one leaves the page unopened. */
fun openLink(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // No browser: nothing else to do here.
    }
}

/** A value with minus and plus buttons; 0 means the goal is off. */
@Composable
private fun GoalStepper(label: String, value: Int, unit: String, step: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { onChange((value - step).coerceAtLeast(0)) }) { Text("−") }
        Text(
            if (value == 0) stringResource(R.string.goal_off) else if (unit.isEmpty()) "$value" else "$value $unit",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        OutlinedButton(onClick = { onChange(value + step) }) { Text("+") }
    }
}
