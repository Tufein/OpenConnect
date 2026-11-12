package nl.tufein.openconnect.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import nl.tufein.openconnect.R
import nl.tufein.openconnect.core.garmin.EndpointResult
import nl.tufein.openconnect.core.garmin.EndpointStatus
import nl.tufein.openconnect.core.garmin.GarminApiException
import nl.tufein.openconnect.core.garmin.GarminSyncReport
import nl.tufein.openconnect.core.garmin.diagnostics
import nl.tufein.openconnect.garmin.GarminController
import nl.tufein.openconnect.garmin.GarminSyncUi
import nl.tufein.openconnect.garmin.GarminUi
import nl.tufein.openconnect.garmin.KeyCheck
import nl.tufein.openconnect.garmin.KeyProblem

const val GARMIN_KEY_PAGE = "https://connect.garmin.com/app/settings/personalApiToken"

@Composable
fun GarminScreen(
    ui: GarminUi,
    onBack: () -> Unit,
    onSaveKey: (String) -> Unit,
    onForgetKey: () -> Unit,
    onDays: (Int) -> Unit,
    onAutoSync: (Boolean) -> Unit,
    onSync: () -> Unit,
    onCancel: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val context = LocalContext.current
    var keyField by remember { mutableStateOf("") }
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Text(stringResource(R.string.garmin_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        }
        item {
            Text(stringResource(R.string.garmin_intro), style = MaterialTheme.typography.bodyMedium)
        }
        item {
            SectionTitle(stringResource(R.string.garmin_key_title))
            SurfaceCard {
                Text(stringResource(R.string.garmin_key_body), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { openKeyPage(context) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.garmin_open_key_page))
                }
                if (!ui.canStoreKey) {
                    Text(stringResource(R.string.garmin_key_cannot_store), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                } else {
                    OutlinedTextField(
                        value = keyField,
                        onValueChange = { keyField = it },
                        label = { Text(stringResource(if (ui.hasKey) R.string.garmin_key_field_saved else R.string.garmin_key_field)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onSaveKey(keyField); keyField = "" }, enabled = keyField.isNotBlank()) {
                            Text(stringResource(R.string.save))
                        }
                        if (ui.hasKey) {
                            OutlinedButton(onClick = onForgetKey) { Text(stringResource(R.string.remove_key)) }
                        }
                    }
                    KeyStatus(ui)
                }
            }
        }
        item {
            SectionTitle(stringResource(R.string.garmin_sync_title))
            SurfaceCard {
                Text(stringResource(R.string.garmin_sync_body), style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (days in GarminController.DAY_CHOICES) {
                        FilterChip(
                            selected = ui.days == days,
                            onClick = { onDays(days) },
                            label = { Text(stringResource(R.string.garmin_days_n, days)) },
                        )
                    }
                }
                val running = ui.sync as? GarminSyncUi.Running
                if (running == null) {
                    Button(onClick = onSync, enabled = ui.hasKey, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.garmin_sync_button))
                    }
                } else {
                    Running(running, onCancel)
                }
                ui.lastSync?.let {
                    Text(
                        stringResource(R.string.garmin_last_sync, Fmt.dateTime(it)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                when (val sync = ui.sync) {
                    is GarminSyncUi.Done -> Result(sync.report)
                    is GarminSyncUi.Failed -> Text(
                        stringResource(R.string.garmin_failed, sync.detail.orEmpty()),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    else -> Unit
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Switch(checked = ui.autoSync, onCheckedChange = onAutoSync, enabled = ui.hasKey)
                Text(stringResource(R.string.garmin_auto), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        }
        item {
            Text(
                stringResource(R.string.garmin_terms),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun KeyStatus(ui: GarminUi) {
    val (text, isError) = when {
        ui.keyProblem == KeyProblem.MALFORMED -> R.string.garmin_key_malformed to true
        ui.keyProblem == KeyProblem.CANNOT_STORE -> R.string.garmin_key_cannot_store to true
        !ui.hasKey -> return
        ui.keyCheck == KeyCheck.CHECKING -> R.string.garmin_key_checking to false
        ui.keyCheck == KeyCheck.ACCEPTED -> R.string.garmin_key_ok to false
        ui.keyCheck == KeyCheck.REJECTED -> R.string.garmin_key_rejected to true
        ui.keyCheck == KeyCheck.UNREACHABLE -> R.string.garmin_key_unreachable to false
        else -> R.string.garmin_key_unknown to false
    }
    Text(
        stringResource(text),
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Running(running: GarminSyncUi.Running, onCancel: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val label = if (running.cancelling) {
            stringResource(R.string.garmin_sync_cancelling)
        } else {
            stringResource(R.string.garmin_sync_running, partName(running.label))
        }
        Text(label, style = MaterialTheme.typography.bodyMedium)
        LinearProgressIndicator(progress = { (running.done / running.total.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = onCancel, enabled = !running.cancelling, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.garmin_sync_cancel))
        }
    }
}

@Composable
private fun partName(id: String): String = when (id) {
    "dailySummaries" -> stringResource(R.string.garmin_part_dailySummaries)
    "steps" -> stringResource(R.string.garmin_part_steps)
    "stress" -> stringResource(R.string.garmin_part_stress)
    "hrv" -> stringResource(R.string.garmin_part_hrv)
    "sleep" -> stringResource(R.string.garmin_part_sleep)
    "bodyBattery" -> stringResource(R.string.garmin_part_bodyBattery)
    "activities" -> stringResource(R.string.garmin_part_activities)
    else -> id
}

@Composable
private fun Result(report: GarminSyncReport) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.garmin_done_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.garmin_done_days, report.daysUpdated, report.nightsUpdated), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.garmin_done_activities, report.activitiesAdded, report.activitiesKnown), style = MaterialTheme.typography.bodyMedium)
        if (report.activitiesWithoutFile > 0) {
            Text(stringResource(R.string.garmin_done_without_file, report.activitiesWithoutFile), style = MaterialTheme.typography.bodySmall)
        }
        if (report.activitiesPending > 0) {
            Text(stringResource(R.string.garmin_done_pending, report.activitiesPending), style = MaterialTheme.typography.bodySmall)
        }
        if (report.cancelled) Text(stringResource(R.string.garmin_cancelled), style = MaterialTheme.typography.bodyMedium)
        report.stopped?.let { stop ->
            Text(stopText(stop), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        for (endpoint in report.endpoints) EndpointLine(endpoint)
        if (report.hasProblems) {
            OutlinedButton(
                onClick = { copyToClipboard(context, report.diagnostics()); copied = true },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) { Text(stringResource(R.string.garmin_copy_details)) }
            if (copied) Text(stringResource(R.string.garmin_copied), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun EndpointLine(endpoint: EndpointResult) {
    val name = partName(endpoint.id)
    val (text, isError) = when (endpoint.status) {
        EndpointStatus.OK -> stringResource(R.string.garmin_endpoint_ok, name, endpoint.records) to false
        EndpointStatus.EMPTY -> stringResource(R.string.garmin_endpoint_empty, name) to false
        EndpointStatus.UNRECOGNISED -> stringResource(R.string.garmin_endpoint_unrecognised, name) to true
        EndpointStatus.FAILED -> stringResource(R.string.garmin_endpoint_failed, name) to true
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun stopText(stop: GarminApiException): String = stringResource(
    when (stop.kind) {
        GarminApiException.Kind.KEY_REJECTED, GarminApiException.Kind.NO_KEY -> R.string.garmin_stop_key_rejected
        GarminApiException.Kind.NETWORK -> R.string.garmin_stop_network
        GarminApiException.Kind.RATE_LIMITED -> R.string.garmin_stop_rate
        GarminApiException.Kind.SERVER -> R.string.garmin_stop_server
        else -> R.string.garmin_stop_other
    },
)

private fun openKeyPage(context: Context) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GARMIN_KEY_PAGE)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // No browser on the phone: the address is in the intro for the person to type, nothing else to do here.
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("OpenConnect Garmin sync", text))
}
