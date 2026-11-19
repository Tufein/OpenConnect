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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.tufein.openconnect.R
import nl.tufein.openconnect.core.watch.SkipReason
import nl.tufein.openconnect.notify.CheckState
import nl.tufein.openconnect.notify.ForwardAccess
import nl.tufein.openconnect.notify.ForwardCheckId
import nl.tufein.openconnect.notify.ForwardChecks
import nl.tufein.openconnect.notify.ForwardHint
import nl.tufein.openconnect.notify.ForwardPhase
import nl.tufein.openconnect.notify.ForwardTrail
import nl.tufein.openconnect.notify.ForwardUi
import nl.tufein.openconnect.watch.BleSensor
import nl.tufein.openconnect.core.watch.BleSync
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
    forward: ForwardUi = ForwardUi(),
    /** What forwarding did, line by line, for "What is happening?". */
    forwardLog: List<ForwardTrail.Line> = emptyList(),
    onForwardEnabled: (Boolean) -> Unit = {},
    onForwardApps: () -> Unit = {},
    onForwardTest: () -> Unit = {},
    onForwardReconnect: () -> Unit = {},
    onForwardHide: (Boolean) -> Unit = {},
    onForwardScreenOff: (Boolean) -> Unit = {},
    /** Asked whenever the screen is shown again, so a permission given in Android's settings is noticed. */
    onForwardRefresh: () -> Unit = {},
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
            SectionTitle(stringResource(R.string.forward_title))
            SurfaceCard {
                ForwardControls(
                    ui = forward,
                    log = forwardLog,
                    onEnabled = { on -> withBluetoothThen(on, ::withBluetooth, onForwardEnabled) },
                    onApps = onForwardApps,
                    onTest = onForwardTest,
                    onReconnect = onForwardReconnect,
                    onHide = onForwardHide,
                    onScreenOff = onForwardScreenOff,
                    onAllowBluetooth = { withBluetooth(onForwardRefresh) },
                    onRefresh = onForwardRefresh,
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

/**
 * Switching forwarding on first asks for the Bluetooth permission (the connection needs it), then for the permission to
 * show the persistent notification on Android 13 and newer. Switching off needs nothing.
 */
private fun withBluetoothThen(on: Boolean, withBluetooth: (() -> Unit) -> Unit, set: (Boolean) -> Unit) {
    if (on) withBluetooth { set(true) } else set(false)
}

@Composable
private fun ForwardControls(
    ui: ForwardUi,
    log: List<ForwardTrail.Line>,
    onEnabled: (Boolean) -> Unit,
    onApps: () -> Unit,
    onTest: () -> Unit,
    onReconnect: () -> Unit,
    onHide: (Boolean) -> Unit,
    onScreenOff: (Boolean) -> Unit,
    onAllowBluetooth: () -> Unit,
    onRefresh: () -> Unit,
) {
    val context = LocalContext.current
    // Notification access and the Bluetooth permission are given in Android's own screens: look again on return.
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        onRefresh()
        onPauseOrDispose { }
    }
    // The persistent notification needs Android 13's notification permission; without it forwarding works but shows none.
    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onEnabled(true) }
    Text(stringResource(R.string.forward_body), style = MaterialTheme.typography.bodyMedium)
    Text(stringResource(R.string.forward_one_phone), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
    SwitchRow(
        checked = ui.enabled,
        onCheckedChange = { on ->
            val needsAsk = on && android.os.Build.VERSION.SDK_INT >= 33 &&
                androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            if (needsAsk) notifyPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS) else onEnabled(on)
        },
    ) {
        Text(stringResource(R.string.forward_switch), style = MaterialTheme.typography.bodyMedium)
    }
    if (!ui.enabled) return

    when (ui.phase) {
        ForwardPhase.NEEDS_ACCESS -> {
            Text(stringResource(R.string.forward_needs_access), style = MaterialTheme.typography.bodyMedium)
            PrimaryButton(onClick = { context.startActivity(ForwardAccess.accessSettingsIntent(context)) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.forward_allow_access))
            }
            if (ForwardAccess.mayNeedRestrictedSettings) {
                Text(stringResource(R.string.forward_restricted), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SecondaryButton(onClick = { context.startActivity(ForwardAccess.appDetailsIntent(context)) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.forward_open_app_info))
                }
            }
        }
        ForwardPhase.NEEDS_WATCH -> Text(stringResource(R.string.forward_needs_watch), style = MaterialTheme.typography.bodyMedium)
        ForwardPhase.NEEDS_BLUETOOTH -> {
            Text(stringResource(R.string.forward_needs_bluetooth), style = MaterialTheme.typography.bodyMedium)
            PrimaryButton(onClick = onAllowBluetooth, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.forward_allow_bluetooth)) }
        }
        ForwardPhase.CONNECTING -> {
            Text(stringResource(R.string.forward_connecting, ui.watchName.orEmpty()), style = MaterialTheme.typography.bodyMedium)
            PixelProgress(Modifier.fillMaxWidth())
        }
        ForwardPhase.CONNECTED -> {
            Text(stringResource(R.string.forward_connected, ui.watchName.orEmpty()), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.forward_sent_count, ui.sent), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ui.lastSentAt?.let { at ->
                Text(stringResource(R.string.forward_last_sent, Fmt.time(at)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        ForwardPhase.WAITING -> {
            val watch = ui.watchName ?: stringResource(R.string.forward_watch_fallback)
            Text(
                if (ui.error.isNullOrBlank()) stringResource(R.string.forward_waiting, watch) else stringResource(R.string.forward_waiting_detail, watch, ui.error),
                style = MaterialTheme.typography.bodyMedium,
            )
            SecondaryButton(onClick = onReconnect, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.forward_try_now)) }
        }
        ForwardPhase.PROBLEM -> {
            val watch = ui.watchName ?: stringResource(R.string.forward_watch_fallback)
            Text(stringResource(R.string.forward_problem, watch, ui.error.orEmpty()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            SecondaryButton(onClick = onReconnect, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.forward_try_now)) }
        }
        ForwardPhase.OFF -> Unit
    }
    ForwardHints(ui)

    SecondaryButton(onClick = onApps, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.forward_choose_apps, ui.apps.size))
    }
    if (ui.phase == ForwardPhase.CONNECTED) {
        SecondaryButton(onClick = onTest, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.forward_test)) }
    }
    SwitchRow(checked = ui.hideContent, onCheckedChange = onHide) {
        Text(stringResource(R.string.forward_hide), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.forward_hide_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    SwitchRow(checked = ui.onlyWhenScreenOff, onCheckedChange = onScreenOff) {
        Text(stringResource(R.string.forward_screen_off), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.forward_screen_off_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    // "It did not arrive" has an answer: the reason the last notification was held back
    ui.lastSkipped?.let { reason ->
        Text(
            stringResource(R.string.forward_skipped, stringResource(skipText(reason))),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    ForwardDetails(ui, log)
}

/** What to try, picked from what the steps found: the first thing a person wants to know when it does not work. */
@Composable
private fun ForwardHints(ui: ForwardUi) {
    val context = LocalContext.current
    for (hint in ForwardChecks.hints(ui, ForwardAccess.garminConnectInstalled(context))) {
        Text(stringResource(hintText(hint)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        when (hint) {
            ForwardHint.LISTENER_NOT_STARTED ->
                SecondaryButton(onClick = { context.startActivity(ForwardAccess.accessSettingsIntent(context)) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.forward_allow_access))
                }
            ForwardHint.ALLOW_OWN_NOTIFICATION ->
                SecondaryButton(onClick = { context.startActivity(ForwardAccess.notificationSettingsIntent(context)) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.forward_open_notification_settings))
                }
            ForwardHint.BATTERY ->
                SecondaryButton(onClick = { context.startActivity(ForwardAccess.batterySettingsIntent()) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.forward_open_battery))
                }
            else -> Unit
        }
    }
}

private fun hintText(hint: ForwardHint): Int = when (hint) {
    ForwardHint.LISTENER_NOT_STARTED -> R.string.forward_hint_listener
    ForwardHint.NOTHING_SEEN -> R.string.forward_hint_nothing_seen
    ForwardHint.CLOSE_GARMIN_CONNECT -> R.string.forward_hint_garmin_connect
    ForwardHint.BATTERY -> R.string.forward_hint_battery
    ForwardHint.WATCH_NOT_ASKING -> R.string.forward_hint_not_asking
    ForwardHint.WATCH_SAID_NO -> R.string.forward_hint_said_no
    ForwardHint.WATCH_IGNORES -> R.string.forward_hint_ignores
    ForwardHint.CHOOSE_APPS -> R.string.forward_hint_choose_apps
    ForwardHint.ALLOW_OWN_NOTIFICATION -> R.string.forward_hint_own_notification
}

/**
 * "What is happening?": every step a notification goes through with where it stands, how many were held back and why,
 * and the last lines of the log, which can be copied. Folded away until asked for.
 */
@Composable
private fun ForwardDetails(ui: ForwardUi, log: List<ForwardTrail.Line>) {
    val context = LocalContext.current
    var open by rememberSaveable { mutableStateOf(false) }
    SecondaryButton(onClick = { open = !open }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(if (open) R.string.forward_details_hide else R.string.forward_details_show))
    }
    if (!open) return

    for (check in ForwardChecks.of(ui)) {
        val label = stringResource(checkLabel(check.id))
        val detail = when (check.id) {
            ForwardCheckId.CONNECTION -> when {
                ui.phase == ForwardPhase.CONNECTING -> ui.connectStep?.let { stringResource(stepText(it)) }
                else -> check.detail
            }
            ForwardCheckId.DELIVERY ->
                if (ui.notify.announced > 0) stringResource(R.string.forward_check_delivery_detail, ui.notify.announced, ui.notify.asked, ui.notify.answered) else null
            ForwardCheckId.SEEN -> check.detail?.let { stringResource(R.string.forward_check_seen_detail, it) }
            else -> check.detail
        }
        Text(
            "${checkMark(check.state)} $label" + (detail?.let { ": $it" } ?: ""),
            style = MaterialTheme.typography.bodySmall,
            color = if (check.state == CheckState.FAIL) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (ui.notify.waiting > 0) {
        Text(
            stringResource(R.string.forward_check_waiting, ui.notify.waiting),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (ui.skippedBy.isNotEmpty()) {
        val top = ui.skippedBy.entries.sortedByDescending { it.value }.take(4)
        val words = top.map { stringResource(skipText(it.key)) } // read here: stringResource cannot be called inside a lambda
        val counts = top.indices.joinToString(", ") { "${words[it]} ×${top[it].value}" }
        Text(stringResource(R.string.forward_skipped_counts, counts), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val header = stringResource(R.string.forward_log_header)
    val shown = log.takeLast(LOG_LINES_SHOWN)
    if (shown.isEmpty()) {
        Text(stringResource(R.string.forward_log_empty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Text(
            ForwardTrail.format(shown, header),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    SecondaryButton(
        onClick = {
            val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
            clipboard?.setPrimaryClip(android.content.ClipData.newPlainText(header, ForwardTrail.format(log, header)))
            android.widget.Toast.makeText(context, R.string.forward_log_copied, android.widget.Toast.LENGTH_SHORT).show()
        },
        enabled = log.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.forward_log_copy))
    }
}

private const val LOG_LINES_SHOWN = 14

/** Plain marks that every font has: the Handheld look has no icons. */
private fun checkMark(state: CheckState): String = when (state) {
    CheckState.OK -> "[x]"
    CheckState.WARN -> "[~]"
    CheckState.FAIL -> "[!]"
    CheckState.WAIT -> "[…]"
    CheckState.UNKNOWN -> "[ ]"
}

private fun checkLabel(id: ForwardCheckId): Int = when (id) {
    ForwardCheckId.ACCESS -> R.string.forward_check_access
    ForwardCheckId.LISTENER -> R.string.forward_check_listener
    ForwardCheckId.SEEN -> R.string.forward_check_seen
    ForwardCheckId.BLUETOOTH -> R.string.forward_check_bluetooth
    ForwardCheckId.OWN_NOTIFICATION -> R.string.forward_check_own_notification
    ForwardCheckId.WATCH_KNOWN -> R.string.forward_check_watch_known
    ForwardCheckId.CONNECTION -> R.string.forward_check_connection
    ForwardCheckId.WATCH_ASKED -> R.string.forward_check_watch_asked
    ForwardCheckId.DELIVERY -> R.string.forward_check_delivery
}

private fun stepText(step: GarminStep): Int = when (step) {
    GarminStep.PAIRING -> R.string.forward_step_pairing
    GarminStep.CONNECTING -> R.string.forward_step_connecting
    GarminStep.HANDSHAKE -> R.string.forward_step_handshake
}

private fun skipText(reason: SkipReason): Int = when (reason) {
    SkipReason.OFF -> R.string.forward_skip_off
    SkipReason.OWN_APP -> R.string.forward_skip_own_app
    SkipReason.APP_NOT_CHOSEN -> R.string.forward_skip_app_not_chosen
    SkipReason.ONGOING -> R.string.forward_skip_ongoing
    SkipReason.GROUP_SUMMARY -> R.string.forward_skip_group_summary
    SkipReason.LOCAL_ONLY -> R.string.forward_skip_local_only
    SkipReason.CALL -> R.string.forward_skip_call
    SkipReason.LOW_IMPORTANCE -> R.string.forward_skip_low_importance
    SkipReason.DO_NOT_DISTURB -> R.string.forward_skip_do_not_disturb
    SkipReason.EMPTY -> R.string.forward_skip_empty
    SkipReason.SCREEN_ON -> R.string.forward_skip_screen_on
    SkipReason.DUPLICATE -> R.string.forward_skip_duplicate
    SkipReason.NOT_CONNECTED -> R.string.forward_skip_not_connected
    SkipReason.WATCH_OFF -> R.string.forward_skip_watch_off
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
    // what the search covers, once there is a search: kept out of the card's top so the cards below it stay where they were
    if (g.searching || g.found.isNotEmpty()) {
        Text(stringResource(R.string.watch_ble_families), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    for (watch in g.found.filter { it.address != g.remembered?.address }) {
        SecondaryButton(onClick = { onSync(watch) }, enabled = !running, modifier = Modifier.fillMaxWidth()) {
            Text(if (watch.paired) stringResource(R.string.watch_ble_paired, watch.name) else watch.name)
        }
        // a model that is not on the list the sync was made for says so, instead of promising what nobody has tried
        when (watch.bleSync) {
            BleSync.UNKNOWN -> BleHint(R.string.watch_ble_hint_unknown)
            BleSync.NEEDS_NEWER_PROTOCOL -> BleHint(R.string.watch_ble_hint_newer)
            else -> Unit
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
    SwitchRow(checked = g.archiveAfterRead, onCheckedChange = onArchive) {
        Text(stringResource(R.string.watch_ble_archive), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.watch_ble_archive_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    SwitchRow(checked = g.background, onCheckedChange = onBackground, enabled = g.remembered != null) {
        Text(stringResource(R.string.watch_ble_background), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.watch_ble_background_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (g.remembered != null && !running) {
        QuietButton(onClick = onForget) { Text(stringResource(R.string.watch_ble_forget)) }
    }
}

@Composable
private fun BleHint(text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
