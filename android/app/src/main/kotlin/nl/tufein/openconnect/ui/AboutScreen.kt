package nl.tufein.openconnect.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import nl.tufein.openconnect.core.Diagnostics
import java.time.Instant
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.tufein.openconnect.R
import nl.tufein.openconnect.core.DataSourceKind
import nl.tufein.openconnect.core.DiagnosticInput
import nl.tufein.openconnect.core.Licences
import nl.tufein.openconnect.core.PrivacyStatement
import nl.tufein.openconnect.core.SourceLabels
import nl.tufein.openconnect.core.SourceStatus
import java.util.Locale

const val ABOUT_ROUTE = "about"

private fun sourceName(kind: DataSourceKind): Int = when (kind) {
    DataSourceKind.GARMIN_ACCOUNT -> R.string.source_garmin_account
    DataSourceKind.GARMIN_ACCOUNT_ACTIVITIES -> R.string.source_garmin_activities
    DataSourceKind.WATCH_USB -> R.string.source_watch_usb
    DataSourceKind.WATCH_FOLDER -> R.string.source_watch_folder
    DataSourceKind.WATCH_BLUETOOTH -> R.string.source_watch_bluetooth
    DataSourceKind.LIVE_HEART_RATE -> R.string.source_live_heart_rate
    DataSourceKind.WORKOUTS_TO_WATCH -> R.string.source_workouts_to_watch
    DataSourceKind.HEALTH_CONNECT -> R.string.source_health_connect
    DataSourceKind.OURA -> R.string.source_oura
    DataSourceKind.WHOOP -> R.string.source_whoop
    DataSourceKind.HUME -> R.string.source_hume
    DataSourceKind.FILE_IMPORT -> R.string.source_file_import
    DataSourceKind.AI_CLAUDE -> R.string.source_ai_claude
    DataSourceKind.AI_GEMINI -> R.string.source_ai_gemini
    DataSourceKind.UPDATER -> R.string.source_updater
    DataSourceKind.KEY_VAULT_AND_LOCK -> R.string.source_key_vault_lock
}

/** "experimental, not tried on a real device yet", or "tested on a real device (day, version)". */
@Composable
fun sourceState(status: SourceStatus): String =
    if (status.tested) {
        stringResource(R.string.source_state_tested, Fmt.date(status.testedOn!!), status.version!!)
    } else {
        stringResource(R.string.source_state_experimental)
    }

/** "Oura Ring: experimental, not tried on a real device yet". */
@Composable
fun sourceLine(status: SourceStatus): String =
    stringResource(R.string.source_line, stringResource(sourceName(status.kind)), sourceState(status))

/** The line under a device switch: "Status: experimental, ...". */
@Composable
fun deviceStatus(kind: DataSourceKind): String =
    stringResource(R.string.device_status, sourceState(SourceLabels.status(kind)))

/** The line about one place data can go, by the key in [PrivacyStatement.called]. */
@Composable
private fun privacyHostText(key: String): String = stringResource(
    when (key) {
        "garmin" -> R.string.privacy_host_garmin
        "oura" -> R.string.privacy_host_oura
        "claude" -> R.string.privacy_host_claude
        "gemini" -> R.string.privacy_host_gemini
        else -> R.string.privacy_host_github
    },
)

/** Version, licences, what has been tried on a real device, the Garmin disclaimer and a short privacy note. */
@Composable
fun AboutScreen(
    ui: SettingsUi,
    onBack: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val context = LocalContext.current
    var diagnostics by remember { mutableStateOf(false) }
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
            Text(stringResource(R.string.about_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        }
        item {
            SurfaceCard {
                Text(stringResource(R.string.about_version, ui.versionName), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.about_licence_line), style = MaterialTheme.typography.bodyMedium)
                SecondaryButton(onClick = { openLink(context, "https://github.com/Tufein/OpenConnect") }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.about_source_link))
                }
                SecondaryButton(onClick = { diagnostics = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.diagnostics_button)) }
            }
        }
        item {
            FoldableCard("about-tried", stringResource(R.string.about_tried_title)) {
                Text(stringResource(R.string.about_tried_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                for (kind in DataSourceKind.values()) Text(sourceLine(SourceLabels.status(kind)), style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            FoldableCard("about-privacy", stringResource(R.string.about_privacy_title)) { Text(stringResource(R.string.about_privacy_body), style = MaterialTheme.typography.bodyMedium) }
        }
        item {
            FoldableCard("about-calls", stringResource(R.string.about_calls_title)) {
                Text(stringResource(R.string.about_calls_intro), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                for (key in PrivacyStatement.called.keys) Text(privacyHostText(key), style = MaterialTheme.typography.bodyMedium)
                SecondaryButton(onClick = { openLink(context, PrivacyStatement.STATEMENT_URL) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.about_privacy_statement))
                }
            }
        }
        item {
            FoldableCard("about-licences", stringResource(R.string.about_licences_title)) {
                for (l in Licences.all) {
                    Text(l.name, style = MaterialTheme.typography.titleSmall)
                    Text(l.licence, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Text(l.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Text(stringResource(R.string.about_garmin_disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (diagnostics) DiagnosticsDialog(ui, onDismiss = { diagnostics = false })
}

/** Shows the exact report text, so nothing leaves the phone that was not first on screen, and offers to share it. */
@Composable
fun DiagnosticsDialog(ui: SettingsUi, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val report = remember(ui) { Diagnostics.report(diagnosticInput(ui), Instant.now()) }
    val title = stringResource(R.string.diagnostics_title)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.diagnostics_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(report, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
            }
        },
        confirmButton = { PrimaryButton(onClick = { shareText(context, title, report) }) { Text(stringResource(R.string.diagnostics_share)) } },
        dismissButton = { QuietButton(onClick = onDismiss) { Text(stringResource(R.string.diagnostics_close)) } },
    )
}

/** What the diagnostics report may carry, from what Settings already knows. There is no key or token in [SettingsUi]'s numbers. */
fun diagnosticInput(ui: SettingsUi): DiagnosticInput {
    val s = ui.sources
    val on = buildList {
        if (s.garmin) add("garmin")
        if (s.oura) add("oura")
    }
    val overview = ui.overview
    return DiagnosticInput(
        appVersion = ui.versionName,
        androidVersion = Build.VERSION.RELEASE.orEmpty(),
        sdk = Build.VERSION.SDK_INT,
        device = listOf(Build.MANUFACTURER, Build.MODEL).filter { !it.isNullOrBlank() }.joinToString(" "),
        language = Locale.getDefault().language,
        sourcesOn = on,
        aiProvider = ui.ai.choice,
        aiKeySet = ui.ai.vendors.any { it.hasKey },
        days = overview?.days ?: 0,
        nights = overview?.nights ?: 0,
        samples = overview?.samples ?: 0,
        activitiesBySource = overview?.sources?.map { it.source to it.activities }.orEmpty(),
        log = ui.syncLog,
    )
}

fun shareText(context: Context, chooserTitle: String, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
