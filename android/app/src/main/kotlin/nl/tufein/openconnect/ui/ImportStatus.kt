package nl.tufein.openconnect.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import nl.tufein.openconnect.R
import nl.tufein.openconnect.core.ArchiveImport
import java.time.Instant
import java.time.ZoneId

/** How many failed files are named on the screen; the sync log and the summary carry the whole number. */
private const val NAMED_FAILURES = 3

/**
 * The file import in Settings (v1.3.0): a progress line with a stop button while it runs, and afterwards what it
 * did: new, already there, skipped (not activities) and failed, the period it covered, and the names of the first
 * files that failed.
 */
@Composable
fun ImportStatus(state: ImportUi, onCancel: () -> Unit) {
    if (state.running) {
        Text(stringResource(R.string.import_running, state.entries, state.imported), style = MaterialTheme.typography.bodySmall)
        QuietButton(onClick = onCancel) { Text(stringResource(R.string.import_cancel)) }
        return
    }
    val report = state.report ?: return
    Text(
        stringResource(R.string.import_summary, report.imported, report.alreadyThere, report.skipped, report.failureCount),
        style = MaterialTheme.typography.bodySmall,
    )
    val first = report.first
    val last = report.last
    if (first != null && last != null) {
        Text(stringResource(R.string.import_period, day(first), day(last)), style = MaterialTheme.typography.bodySmall)
    }
    report.stopped?.let { why ->
        val message = when (why) {
            ArchiveImport.Stop.CANCELLED -> R.string.import_stopped_cancelled
            ArchiveImport.Stop.TOO_MANY_FILES -> R.string.import_stopped_files
            ArchiveImport.Stop.TOO_LARGE -> R.string.import_stopped_large
        }
        Text(stringResource(message), style = MaterialTheme.typography.bodySmall)
    }
    if (report.named > 0) Text(stringResource(R.string.import_named, report.named), style = MaterialTheme.typography.bodySmall)
    if (report.backupInside) Text(stringResource(R.string.import_backup_inside), style = MaterialTheme.typography.bodySmall)
    for (failure in report.failures.take(NAMED_FAILURES)) {
        Text(
            stringResource(R.string.import_failure_line, failure.name, failure.reason),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    if (report.failureCount > NAMED_FAILURES) {
        Text(
            stringResource(R.string.import_more_failures, report.failureCount - NAMED_FAILURES),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    if (report.shapes.isNotEmpty()) Text(stringResource(R.string.import_shapes, report.shapes.size), style = MaterialTheme.typography.bodySmall)
    if (report.shapes.isNotEmpty() || report.failureCount > 0) CopyReport(report)
}

/** Puts the report (counts, file name patterns and key names; no values, no addresses) on the clipboard, to paste into a GitHub issue. */
@Composable
private fun CopyReport(report: ArchiveImport.Report) {
    val context = LocalContext.current
    var copied by remember(report) { mutableStateOf(false) }
    QuietButton(onClick = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Cercis import report", report.diagnostics()))
        copied = true
    }) { Text(stringResource(if (copied) R.string.import_copied else R.string.import_copy)) }
}

private fun day(instant: Instant): String = Fmt.date(instant.atZone(ZoneId.systemDefault()).toLocalDate())
