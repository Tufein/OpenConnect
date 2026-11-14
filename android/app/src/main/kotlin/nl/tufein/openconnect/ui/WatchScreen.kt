package nl.tufein.openconnect.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.tufein.openconnect.R
import nl.tufein.openconnect.watch.BleSensor
import nl.tufein.openconnect.watch.GarminBleWatch
import nl.tufein.openconnect.watch.GarminStep
import nl.tufein.openconnect.watch.LivePhase
import nl.tufein.openconnect.watch.LiveUi
import nl.tufein.openconnect.watch.SyncFailure
import nl.tufein.openconnect.watch.SyncSource
import nl.tufein.openconnect.watch.SyncStatus
import nl.tufein.openconnect.watch.WatchInfo
import nl.tufein.openconnect.watch.WatchUi
import nl.tufein.openconnect.watch.bluetoothPermissions
import nl.tufein.openconnect.watch.hasBluetoothPermissions

@Composable
fun WatchScreen(
    ui: WatchUi,
    onBack: () -> Unit,
    onSearchUsb: () -> Unit,
    onRead: (WatchInfo) -> Unit,
    onScan: () -> Unit,
    onConnect: (BleSensor) -> Unit,
    onStopLive: () -> Unit,
    onSaveLive: (name: String) -> Unit,
    onReadFolder: (Uri) -> Unit,
    onFindGarmin: () -> Unit = {},
    onStopFindGarmin: () -> Unit = {},
    onSyncGarmin: (GarminBleWatch) -> Unit = {},
    onForgetGarmin: () -> Unit = {},
    onGarminArchive: (Boolean) -> Unit = {},
    onGarminBackground: (Boolean) -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(),
) {
    val context = LocalContext.current
    var permissionDenied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        permissionDenied = !granted.values.all { it }
        if (!permissionDenied) onScan()
    }
    // The Garmin card asks for the same permissions, then carries on with what was tapped.
    var afterGarminPermission by remember { mutableStateOf<(() -> Unit)?>(null) }
    val garminPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        permissionDenied = !granted.values.all { it }
        if (!permissionDenied) afterGarminPermission?.invoke()
        afterGarminPermission = null
    }
    fun withBluetooth(action: () -> Unit) {
        if (hasBluetoothPermissions(context)) {
            action()
        } else {
            afterGarminPermission = action
            garminPermissionLauncher.launch(bluetoothPermissions())
        }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) onReadFolder(tree)
    }
    val liveName = stringResource(R.string.watch_live_name)

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PixelIconButton(onClick = onBack) {
                PixelIcon(PixelIcons.ArrowLeft, contentDescription = stringResource(R.string.back))
            }
            Text(stringResource(R.string.watch_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        }
        item {
            SectionTitle(stringResource(R.string.watch_ble_title))
            SurfaceCard(accent = MaterialTheme.colorScheme.primary) {
                Text(stringResource(R.string.watch_ble_body), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.watch_ble_warning), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                GarminBleControls(
                    ui = ui,
                    permissionDenied = permissionDenied,
                    onFind = { withBluetooth(onFindGarmin) },
                    onStopFind = onStopFindGarmin,
                    onSync = { watch -> withBluetooth { onSyncGarmin(watch) } },
                    onForget = onForgetGarmin,
                    onArchive = onGarminArchive,
                    onBackground = onGarminBackground,
                )
            }
        }
        item {
            SectionTitle(stringResource(R.string.watch_together_title))
            SurfaceCard {
                Text(stringResource(R.string.watch_together_body), style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            SectionTitle(stringResource(R.string.watch_usb_title))
            SurfaceCard {
                Text(stringResource(R.string.watch_usb_body), style = MaterialTheme.typography.bodyMedium)
                UsbControls(ui, onSearchUsb, onRead)
            }
        }
        item {
            SectionTitle(stringResource(R.string.watch_folder_title))
            SurfaceCard {
                Text(stringResource(R.string.watch_folder_body), style = MaterialTheme.typography.bodyMedium)
                SecondaryButton(
                    onClick = { folderPicker.launch(null) },
                    enabled = ui.sync !is SyncStatus.Running,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.watch_folder_pick)) }
            }
        }
        item {
            SectionTitle(stringResource(R.string.watch_live_title))
            SurfaceCard {
                Text(stringResource(R.string.watch_live_body), style = MaterialTheme.typography.bodyMedium)
                LiveControls(
                    live = ui.live,
                    permissionDenied = permissionDenied,
                    onScan = {
                        if (hasBluetoothPermissions(context)) onScan() else permissionLauncher.launch(bluetoothPermissions())
                    },
                    onConnect = onConnect,
                    onStop = onStopLive,
                    onSave = { onSaveLive(liveName) },
                )
            }
        }
        item {
            Text(
                stringResource(R.string.watch_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UsbControls(ui: WatchUi, onSearch: () -> Unit, onRead: (WatchInfo) -> Unit) {
    val running = ui.sync is SyncStatus.Running
    if (ui.watches.isEmpty()) {
        Text(stringResource(R.string.watch_usb_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    for (watch in ui.watches) {
        PrimaryButton(onClick = { onRead(watch) }, enabled = !running, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.watch_read_button, watch.name))
        }
    }
    SecondaryButton(onClick = onSearch, enabled = !running, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.watch_usb_search))
    }
    if (ui.syncSource != SyncSource.GARMIN_BLE) SyncResult(ui.sync, ui.syncSource)
}

@Composable
private fun GarminBleControls(
    ui: WatchUi,
    permissionDenied: Boolean,
    onFind: () -> Unit,
    onStopFind: () -> Unit,
    onSync: (GarminBleWatch) -> Unit,
    onForget: () -> Unit,
    onArchive: (Boolean) -> Unit,
    onBackground: (Boolean) -> Unit,
) {
    val g = ui.garmin
    val running = ui.sync is SyncStatus.Running
    g.remembered?.let { watch ->
        PrimaryButton(onClick = { onSync(watch) }, enabled = !running, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.watch_ble_sync, watch.name))
        }
    }
    SecondaryButton(
        onClick = if (g.searching) onStopFind else onFind,
        enabled = !running,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(if (g.searching) R.string.watch_ble_stop else R.string.watch_ble_find)) }
    if (permissionDenied) {
        Text(stringResource(R.string.watch_live_permission), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    if (g.searching && g.found.isEmpty()) {
        Text(stringResource(R.string.watch_ble_searching), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    for (watch in g.found.filter { it.address != g.remembered?.address }) {
        SecondaryButton(onClick = { onSync(watch) }, enabled = !running, modifier = Modifier.fillMaxWidth()) {
            Text(if (watch.paired) stringResource(R.string.watch_ble_paired, watch.name) else watch.name)
        }
    }
    g.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    if (running && ui.syncSource == SyncSource.GARMIN_BLE && g.step != null) {
        Text(
            when (g.step) {
                GarminStep.PAIRING -> stringResource(R.string.watch_ble_step_pairing)
                GarminStep.CONNECTING -> stringResource(R.string.watch_ble_step_connecting)
                GarminStep.HANDSHAKE -> stringResource(R.string.watch_ble_step_handshake)
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        PixelProgress(Modifier.fillMaxWidth())
    } else if (ui.syncSource == SyncSource.GARMIN_BLE) {
        SyncResult(ui.sync, SyncSource.GARMIN_BLE)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        PixelSwitch(checked = g.archiveAfterRead, onCheckedChange = onArchive)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(stringResource(R.string.watch_ble_archive), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.watch_ble_archive_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        PixelSwitch(checked = g.background, onCheckedChange = onBackground, enabled = g.remembered != null)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(stringResource(R.string.watch_ble_background), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.watch_ble_background_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (g.remembered != null && !running) {
        QuietButton(onClick = onForget) { Text(stringResource(R.string.watch_ble_forget)) }
    }
}

@Composable
private fun SyncResult(sync: SyncStatus, source: SyncSource) {
    val ble = source == SyncSource.GARMIN_BLE
    when (sync) {
        SyncStatus.Idle -> Unit
        is SyncStatus.Running -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (sync.total == 0) {
                Text(stringResource(R.string.watch_sync_opening), style = MaterialTheme.typography.bodyMedium)
                PixelProgress(Modifier.fillMaxWidth())
            } else {
                Text(stringResource(R.string.watch_sync_progress, minOf(sync.done + 1, sync.total), sync.total), style = MaterialTheme.typography.bodyMedium)
                PixelProgress(progress = { sync.done / sync.total.toFloat() }, modifier = Modifier.fillMaxWidth())
                Text(sync.current, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        is SyncStatus.Done -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val r = sync.report
            Text(stringResource(R.string.watch_done_title, sync.device), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.watch_done_files, r.filesRead, r.filesSkipped), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.watch_done_activities, r.activitiesAdded, r.activitiesKnown), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.watch_done_days, r.daysUpdated, r.nightsUpdated), style = MaterialTheme.typography.bodyMedium)
            if (r.errors.isNotEmpty()) {
                Text(stringResource(R.string.watch_done_errors, r.errors.size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                Text(r.errors.take(3).joinToString("\n"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        is SyncStatus.Failed -> Text(
            when (sync.reason) {
                SyncFailure.NOT_FOUND -> stringResource(R.string.watch_fail_not_found)
                SyncFailure.NO_PERMISSION -> stringResource(R.string.watch_fail_permission)
                SyncFailure.CANNOT_OPEN ->
                    if (ble) stringResource(R.string.watch_fail_ble_open, sync.detail ?: "") else stringResource(R.string.watch_fail_open)
                SyncFailure.NO_FILES -> stringResource(if (ble) R.string.watch_fail_ble_files else R.string.watch_fail_files)
                SyncFailure.NO_FILES_IN_FOLDER -> stringResource(R.string.watch_fail_folder_files)
                SyncFailure.NOT_PAIRED -> stringResource(R.string.watch_fail_not_paired)
                SyncFailure.OTHER -> stringResource(R.string.watch_fail_other, sync.detail ?: "")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun LiveControls(
    live: LiveUi,
    permissionDenied: Boolean,
    onScan: () -> Unit,
    onConnect: (BleSensor) -> Unit,
    onStop: () -> Unit,
    onSave: () -> Unit,
) {
    val busy = live.phase == LivePhase.SCANNING || live.phase == LivePhase.CONNECTING || live.phase == LivePhase.STREAMING ||
        live.phase == LivePhase.RECONNECTING
    if (live.phase == LivePhase.IDLE || live.phase == LivePhase.SCANNING || live.phase == LivePhase.STOPPED) {
        PrimaryButton(
            onClick = if (live.phase == LivePhase.SCANNING) onStop else onScan,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(if (live.phase == LivePhase.SCANNING) R.string.watch_live_stop else R.string.watch_live_search))
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
        Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(live.bpm?.toString() ?: "–", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
            Text("bpm", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 10.dp))
        }
        TileRow(
            { StatTile(stringResource(R.string.watch_live_min), live.min?.toString() ?: "–", it) },
            { StatTile(stringResource(R.string.watch_live_avg), live.avg?.toString() ?: "–", it) },
        )
        TileRow(
            { StatTile(stringResource(R.string.watch_live_max), live.max?.toString() ?: "–", it) },
            { StatTile(stringResource(R.string.watch_live_hrv), live.rmssdMs?.toString() ?: "–", it, unit = "ms") },
        )
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
