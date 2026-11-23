package nl.tufein.openconnect.ui

import android.content.ActivityNotFoundException
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.os.Build
import android.content.pm.PackageManager
import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.luminance
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
import androidx.health.connect.client.PermissionController
import nl.tufein.openconnect.core.DataSourceKind
import nl.tufein.openconnect.core.DataOverview
import nl.tufein.openconnect.core.health.HealthAccess
import nl.tufein.openconnect.health.AndroidHealthConnectGateway
import nl.tufein.openconnect.health.HealthMessage
import nl.tufein.openconnect.health.HealthUi
import nl.tufein.openconnect.core.SyncLog
import nl.tufein.openconnect.core.SyncLogEntry
import nl.tufein.openconnect.core.WeeklyGoals
import nl.tufein.openconnect.core.reports.AiVendor
import nl.tufein.openconnect.core.watch.ConnectIqStore

data class SettingsUi(
    val ai: AiUi,
    /** "system", "dark" or "light". */
    val theme: String,
    val versionName: String,
    val goals: WeeklyGoals = WeeklyGoals(),
    val overview: DataOverview? = null,
    val syncLog: List<SyncLogEntry> = emptyList(),
    val nudges: Boolean = false,
    val appLock: Boolean = false,
    val lockAvailable: Boolean = false,
    val sources: SourcesUi = SourcesUi(),
    val look: Look = Look(),
    /** The icon on the launcher. */
    val appIcon: AppIcon = AppIcon.HANDHELD,
    /** The file import: its progress while it runs and the report of the last one (v1.3.0). */
    val fileImport: ImportUi = ImportUi(),
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
    onCancelImport: () -> Unit = {},
    onExportDaily: () -> Unit,
    onExportActivities: () -> Unit,
    onClear: () -> Unit,
    onWatch: () -> Unit,
    onGarmin: () -> Unit,
    onOura: () -> Unit = {},
    onAbout: () -> Unit = {},
    /** The choice of devices changed: the Garmin watch or the Oura Ring, each on or off. */
    onSources: (garmin: Boolean, oura: Boolean) -> Unit = { _, _ -> },
    /** A colour theme was picked. */
    onLook: (palette: String) -> Unit = {},
    /** The letters were switched between pixel and clear. */
    onLetters: (letters: String) -> Unit = {},
    /** The style was switched between retro, sleek and classic. */
    onStyle: (style: String) -> Unit = {},
    /** A launcher icon was picked. */
    onAppIcon: (AppIcon) -> Unit = {},
    onBackup: () -> Unit = {},
    onRestore: () -> Unit = {},
    onExportAll: () -> Unit = {},
    onClearLog: () -> Unit = {},
    onNudges: (Boolean) -> Unit = {},
    onAppLock: (Boolean) -> Unit = {},
    health: HealthUi = HealthUi(),
    onHealthChecked: () -> Unit = {},
    onHealthExport: () -> Unit = {},
    onHealthImport: () -> Unit = {},
    onHealthAuto: (Boolean) -> Unit = {},
    onHealthAutoImport: (Boolean) -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(),
) {
    var confirmClear by remember { mutableStateOf(false) }
    var diagnostics by remember { mutableStateOf(false) }
    // Folded away at first; kept while the app is open, also when another tab is looked at in between.
    var lookOpen by rememberSaveable { mutableStateOf(false) }
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
        // Everything about the look (dark or light, style, letters, colours and the launcher icon) sits in one section that
        // is folded away until it is tapped, so the settings people come for more often are not three screens down.
        item {
            FoldSection(
                title = stringResource(R.string.settings_appearance),
                summary = lookSummary(ui.look),
                open = lookOpen,
                onToggle = { lookOpen = !lookOpen },
                modifier = Modifier.testTag("appearance-fold"),
            ) {
                Text(stringResource(R.string.settings_look_preview), style = MaterialTheme.typography.titleMedium)
                LookPreview()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((mode, label) in listOf("system" to R.string.theme_system, "dark" to R.string.theme_dark, "light" to R.string.theme_light)) {
                        PixelChip(selected = ui.theme == mode, onClick = { onTheme(mode) }, label = { Text(stringResource(label)) })
                    }
                }
                Text(stringResource(R.string.look_style), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((style, label) in listOf("retro" to R.string.style_retro, "clean" to R.string.style_clean, "classic" to R.string.style_classic)) {
                        PixelChip(selected = ui.look.style == style, onClick = { onStyle(style) }, label = { Text(stringResource(label)) })
                    }
                }
                Text(stringResource(R.string.style_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // Only the retro style has a choice of letters; the other two always use the clear ones.
                if (ui.look.style == "retro") {
                    Text(stringResource(R.string.look_letters), style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((letters, label) in listOf("pixel" to R.string.letters_pixel, "clear" to R.string.letters_clear)) {
                            PixelChip(selected = ui.look.letters == letters, onClick = { onLetters(letters) }, label = { Text(stringResource(label)) })
                        }
                    }
                    Text(stringResource(R.string.letters_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(stringResource(R.string.look_colours), style = MaterialTheme.typography.titleMedium)
                val darkNow = MaterialTheme.colorScheme.background.luminance() < 0.5f
                for (group in PaletteGroup.entries) {
                    val themes = Palette.entries.filter { it.group == group && (it != Palette.DYNAMIC || android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) }
                    if (themes.isEmpty()) continue
                    Text(stringResource(group.label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (palette in themes) {
                            PixelChip(
                                selected = ui.look.palette == palette.key,
                                onClick = { onLook(palette.key) },
                                label = { Text(stringResource(palette.label)) },
                                leadingIcon = { PaletteSwatch(palette, darkNow) },
                            )
                        }
                    }
                }
                Text(stringResource(R.string.look_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.settings_app_icon), style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (icon in AppIcon.entries) {
                        AppIconChoice(icon, selected = ui.appIcon == icon, onClick = { onAppIcon(icon) })
                    }
                }
                Text(stringResource(R.string.icon_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            FoldableCard("settings-goals", stringResource(R.string.settings_goals)) {
                Text(stringResource(R.string.settings_goals_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                GoalStepper(stringResource(R.string.goal_intensity), ui.goals.intensityMinutes, "min", 10) { onGoals(ui.goals.copy(intensityMinutes = it)) }
                GoalStepper(stringResource(R.string.goal_distance), ui.goals.distanceKm, "km", 5) { onGoals(ui.goals.copy(distanceKm = it)) }
                GoalStepper(stringResource(R.string.goal_activities), ui.goals.activities, "", 1) { onGoals(ui.goals.copy(activities = it)) }
            }
        }
        item {
            FoldableCard("settings-devices", stringResource(R.string.settings_devices)) {
                Text(stringResource(R.string.settings_devices_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                DeviceSwitch(stringResource(R.string.device_garmin), stringResource(R.string.device_garmin_body), ui.sources.garmin, deviceStatus(DataSourceKind.GARMIN_ACCOUNT)) { onSources(it, ui.sources.oura) }
                if (ui.sources.garmin) {
                    PrimaryButton(onClick = onGarmin, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_garmin)) }
                    PrimaryButton(onClick = onWatch, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_watch)) }
                    ConnectIqButton()
                }
                DeviceSwitch(stringResource(R.string.device_oura), stringResource(R.string.device_oura_body), ui.sources.oura, deviceStatus(DataSourceKind.OURA)) { onSources(ui.sources.garmin, it) }
                if (ui.sources.oura) {
                    PrimaryButton(onClick = onOura, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_oura)) }
                }
            }
        }
        item {
            FoldableCard("settings-data", stringResource(R.string.settings_data)) {
                Text(stringResource(R.string.settings_data_body), style = MaterialTheme.typography.bodyMedium)
                SecondaryButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_import)) }
                ImportStatus(ui.fileImport, onCancelImport)
                SecondaryButton(onClick = onDemo, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_demo)) }
                QuietButton(onClick = { confirmClear = true }) {
                    Text(stringResource(R.string.action_clear), color = MaterialTheme.colorScheme.error)
                }
            }
        }
        item {
            FoldableCard("settings-export", stringResource(R.string.settings_export)) {
                Text(stringResource(R.string.settings_export_body), style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton(onClick = onExportDaily) { Text(stringResource(R.string.export_daily)) }
                    SecondaryButton(onClick = onExportActivities) { Text(stringResource(R.string.export_activities)) }
                }
            }
        }
        item {
            FoldableCard("settings-nudges", stringResource(R.string.settings_nudges)) {
                Text(stringResource(R.string.settings_nudges_body), style = MaterialTheme.typography.bodyMedium)
                val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) onNudges(true) }
                val context = LocalContext.current
                SwitchRow(
                    checked = ui.nudges,
                    onCheckedChange = { on ->
                        val needsAsk = on && Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        if (needsAsk) permission.launch(Manifest.permission.POST_NOTIFICATIONS) else onNudges(on)
                    },
                ) {
                    Text(stringResource(R.string.settings_nudges_switch), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            FoldableCard("settings-lock", stringResource(R.string.settings_lock)) {
                Text(stringResource(R.string.settings_lock_body), style = MaterialTheme.typography.bodyMedium)
                if (!ui.lockAvailable && !ui.appLock) {
                    Text(stringResource(R.string.settings_lock_unavailable), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                SwitchRow(checked = ui.appLock, enabled = ui.lockAvailable || ui.appLock, onCheckedChange = onAppLock, modifier = Modifier.testTag("app-lock-switch")) {
                    Text(stringResource(R.string.settings_lock_switch), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            FoldableCard("settings-health", stringResource(R.string.settings_health)) { HealthConnectBody(health, onHealthChecked, onHealthExport, onHealthImport, onHealthAuto, onHealthAutoImport) }
        }
        item {
            FoldableCard("settings-backup", stringResource(R.string.settings_backup)) {
                Text(stringResource(R.string.settings_backup_body), style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton(onClick = onBackup) { Text(stringResource(R.string.action_backup)) }
                    SecondaryButton(onClick = onRestore) { Text(stringResource(R.string.action_restore)) }
                    SecondaryButton(onClick = onExportAll) { Text(stringResource(R.string.action_export_all)) }
                }
            }
        }
        item {
            FoldableCard("settings-sources", stringResource(R.string.settings_sources)) { SourcesBody(ui.overview) }
        }
        item {
            FoldableCard("settings-synclog", stringResource(R.string.settings_synclog)) { SyncLogBody(ui.syncLog, onClearLog) }
        }
        item {
            FoldableCard("settings-ai", stringResource(R.string.settings_ai)) {
                Text(stringResource(R.string.settings_ai_body), style = MaterialTheme.typography.bodyMedium)
                for ((value, label) in listOf(
                    "template" to R.string.provider_template,
                    AiVendor.CLAUDE.id to R.string.provider_claude,
                    AiVendor.GEMINI.id to R.string.provider_gemini,
                )) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(selected = ui.ai.choice == value, role = Role.RadioButton, onClick = { onChoice(value) }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = ui.ai.choice == value, onClick = null)
                        Text(stringResource(label), modifier = Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text(stringResource(R.string.report_language), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PixelChip(selected = ui.ai.language == "en", onClick = { onLanguage("en") }, label = { Text("English") })
                    PixelChip(selected = ui.ai.language == "nl", onClick = { onLanguage("nl") }, label = { Text("Nederlands") })
                }
                Text(stringResource(R.string.privacy_ai), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // Only the AI that was picked is on screen; the other one is out of the way (its saved key is kept, unused).
        items(ui.ai.vendors.filter { it.vendor.id == ui.ai.choice }, key = { it.vendor.id }) { vendor ->
            VendorCard(ui.ai.canStoreKeys, vendor, onSaveKey, onCheckKey, onForgetKey, onModel)
        }
        item {
            UpdateCard()
        }
        item {
            FoldableCard("settings-about", stringResource(R.string.settings_about)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LogoMark(size = 40.dp)
                    Text("OpenConnect ${ui.versionName}", style = MaterialTheme.typography.titleMedium)
                }
                Text(stringResource(R.string.about_body), style = MaterialTheme.typography.bodyMedium)
                Text("github.com/Tufein/OpenConnect", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SecondaryButton(onClick = onAbout, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.about_open_button)) }
                SecondaryButton(onClick = { diagnostics = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.diagnostics_button)) }
            }
        }
    }
    if (diagnostics) DiagnosticsDialog(ui, onDismiss = { diagnostics = false })
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.action_clear)) },
            text = { Text(stringResource(R.string.clear_body)) },
            confirmButton = { PrimaryButton(onClick = { confirmClear = false; onClear() }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { QuietButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
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
        SecondaryButton(onClick = { openLink(context, vendor.keyPage) }, modifier = Modifier.fillMaxWidth()) {
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
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(onClick = { onSaveKey(vendor, keyField); keyField = "" }, enabled = keyField.isNotBlank()) {
                    Text(stringResource(R.string.save_key, name))
                }
                if (ui.hasKey) {
                    SecondaryButton(onClick = { onCheckKey(vendor) }, enabled = ui.check != AiKeyCheck.CHECKING) {
                        Text(stringResource(R.string.check_key))
                    }
                    SecondaryButton(onClick = { onForgetKey(vendor) }) { Text(stringResource(R.string.remove_key)) }
                }
            }
            KeyStatus(ui, name)
        }
        OutlinedTextField(
            value = modelField,
            onValueChange = { modelField = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.model)) },
            placeholder = { Text(vendor.defaultModel) },
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(vendor.suggestedModels) { model ->
                PixelChip(
                    selected = modelField == model,
                    onClick = { modelField = model; onModel(vendor, model) },
                    label = { Text(model) },
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton(onClick = { onModel(vendor, modelField) }, enabled = modelField.trim() != ui.model) {
                Text(stringResource(R.string.save_model))
            }
            if (ui.model != vendor.defaultModel) {
                QuietButton(onClick = { modelField = vendor.defaultModel; onModel(vendor, "") }) {
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
                PixelSpinner(Modifier.padding(2.dp))
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
        SecondaryButton(onClick = { onChange((value - step).coerceAtLeast(0)) }) { Text("−") }
        Text(
            if (value == 0) stringResource(R.string.goal_off) else if (unit.isEmpty()) "$value" else "$value $unit",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        SecondaryButton(onClick = { onChange(value + step) }) { Text("+") }
    }
}

@Composable
private fun sourceLabel(source: String): String = stringResource(
    when (source) {
        "garmin" -> R.string.source_garmin
        "watch", "usb", "folder", "garmin-ble" -> R.string.source_watch
        "demo" -> R.string.source_demo
        "oura" -> R.string.source_oura
        else -> R.string.source_import
    },
)

/** Where the data came from: activities per source with the period, then the daily, sleep and watch readings. */
@Composable
private fun SourcesBody(overview: DataOverview?) {
    if (overview == null || (overview.sources.isEmpty() && overview.days == 0 && overview.nights == 0)) {
        Text(stringResource(R.string.sources_empty), style = MaterialTheme.typography.bodyMedium)
        return
    }
    for (s in overview.sources) {
        val zone = java.time.ZoneId.systemDefault()
        val first = s.first
        val last = s.last
        val period = if (first != null && last != null) {
            " · " + Fmt.shortDate(first.atZone(zone).toLocalDate()) + " – " + Fmt.shortDate(last.atZone(zone).toLocalDate())
        } else {
            ""
        }
        Text(
            stringResource(R.string.sources_line, sourceLabel(s.source), s.activities) + period,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    val firstDay = overview.firstDay
    val lastDay = overview.lastDay
    val span = if (firstDay != null && lastDay != null) " · " + Fmt.shortDate(firstDay) + " – " + Fmt.shortDate(lastDay) else ""
    Text(
        stringResource(R.string.sources_health, overview.days, overview.nights, Fmt.thousands(overview.samples)) + span,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SyncLogBody(log: List<SyncLogEntry>, onClear: () -> Unit) {
    if (log.isEmpty()) {
        Text(stringResource(R.string.synclog_empty), style = MaterialTheme.typography.bodyMedium)
        return
    }
    for (e in log.take(SHOWN_LOG)) {
        val where = stringResource(
            when (e.source) {
                SyncLog.SOURCE_USB -> R.string.synclog_usb
                SyncLog.SOURCE_FOLDER -> R.string.synclog_folder
                SyncLog.SOURCE_BLE -> R.string.synclog_ble
                SyncLog.SOURCE_OURA -> R.string.synclog_oura
                SyncLog.SOURCE_HEALTH -> R.string.synclog_health
                SyncLog.SOURCE_ARCHIVE -> R.string.synclog_archive
                else -> R.string.synclog_account
            },
        )
        val result = if (e.ok) stringResource(R.string.synclog_ok, e.added) else stringResource(R.string.synclog_failed, e.detail.ifBlank { "?" })
        Text(
            Fmt.dateTime(e.time) + " · " + where + " · " + result,
            style = MaterialTheme.typography.bodySmall,
            color = if (e.ok) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
        )
    }
    QuietButton(onClick = onClear) { Text(stringResource(R.string.synclog_clear)) }
}

private const val SHOWN_LOG = 15

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HealthConnectBody(
    health: HealthUi,
    onChecked: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onAuto: (Boolean) -> Unit,
    onAutoImport: (Boolean) -> Unit,
) {
    val permission = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { onChecked() }
    Text(stringResource(R.string.settings_health_body), style = MaterialTheme.typography.bodyMedium)
    when (health.access) {
        HealthAccess.UNAVAILABLE -> Text(stringResource(R.string.health_unavailable), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HealthAccess.NEEDS_PERMISSION -> PrimaryButton(
            onClick = { permission.launch(AndroidHealthConnectGateway.ASKED_PERMISSIONS) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.health_allow)) }
        HealthAccess.READY -> {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(onClick = onExport, enabled = !health.busy) { Text(stringResource(R.string.health_send)) }
                SecondaryButton(onClick = onImport, enabled = !health.busy) { Text(stringResource(R.string.health_read)) }
            }
            SwitchRow(checked = health.auto, onCheckedChange = onAuto) {
                Text(stringResource(R.string.health_auto), style = MaterialTheme.typography.bodyMedium)
            }
            SwitchRow(checked = health.autoImport, onCheckedChange = onAutoImport) {
                Text(stringResource(R.string.health_auto_import), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    if (health.busy) Text(stringResource(R.string.health_busy), style = MaterialTheme.typography.bodySmall)
    when (val m = health.message) {
        is HealthMessage.Exported -> Text(stringResource(R.string.health_sent, m.sent, m.skipped), style = MaterialTheme.typography.bodySmall)
        is HealthMessage.Imported -> Text(stringResource(R.string.health_read_done, m.days, m.nights, m.weights), style = MaterialTheme.typography.bodySmall)
        is HealthMessage.Failed -> Text(stringResource(R.string.health_failed, m.detail ?: "?"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        null -> Unit
    }
}

/** A device that can be switched on or off, with a line on what it brings. */
@Composable
fun DeviceSwitch(title: String, body: String, checked: Boolean, status: String? = null, onChange: (Boolean) -> Unit) {
    SwitchRow(checked = checked, onCheckedChange = onChange, switchFirst = false) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (status != null) Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
    }
}

/** Two squares next to a theme's name: its background and its main colour, so the chip shows the theme before it is picked. */
@Composable
private fun PaletteSwatch(p: Palette, dark: Boolean) {
    val colors = paletteSwatch(p, dark)
    val background = colors?.first ?: MaterialTheme.colorScheme.background
    val main = colors?.second ?: MaterialTheme.colorScheme.primary
    androidx.compose.foundation.layout.Row(Modifier.border(1.dp, inkColor().copy(alpha = 0.6f))) {
        Box(Modifier.size(width = 9.dp, height = 16.dp).background(background))
        Box(Modifier.size(width = 9.dp, height = 16.dp).background(main))
    }
}

/** One launcher icon to pick: its picture, with the name under it; the chosen one has an ink frame and a hard shadow. */
@Composable
private fun AppIconChoice(icon: AppIcon, selected: Boolean, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.medium
    Column(
        Modifier
            .width(76.dp)
            .selectable(selected = selected, role = androidx.compose.ui.semantics.Role.RadioButton, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier
                .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.onBackground else inkColor().copy(alpha = 0.4f), shape)
                .padding(3.dp),
        ) { AppIconPreview(icon, size = 56.dp) }
        Text(
            stringResource(icon.label),
            style = MaterialTheme.typography.labelSmall,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** What is chosen now, for the header of the folded Appearance section: colour theme, style and, in retro, the letters. */
@Composable
private fun lookSummary(look: Look): String {
    val style = when (look.style) {
        "clean" -> R.string.style_clean
        "classic" -> R.string.style_classic
        else -> R.string.style_retro
    }
    val parts = mutableListOf(stringResource(Palette.of(look.palette).label), stringResource(style))
    if (look.style == "retro") parts += stringResource(if (look.letters == "clear") R.string.letters_clear else R.string.letters_pixel)
    return parts.joinToString(" · ")
}

/**
 * Opens Garmin's Connect IQ app itself, not a web page (v0.43.0). The order of what is tried is worked out by
 * [ConnectIqStore.steps]; [openConnectIq] walks it. Only when nothing on the phone could open any of it is that said.
 */
@Composable
private fun ConnectIqButton() {
    val context = LocalContext.current
    var failed by remember { mutableStateOf(false) }
    SecondaryButton(onClick = { failed = openConnectIq(context) == null }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(if (ConnectIqStore.hasListing) R.string.connect_iq_open_listing else R.string.connect_iq_open))
    }
    Text(
        stringResource(if (failed) R.string.connect_iq_failed else R.string.connect_iq_note),
        style = MaterialTheme.typography.bodySmall,
        color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
