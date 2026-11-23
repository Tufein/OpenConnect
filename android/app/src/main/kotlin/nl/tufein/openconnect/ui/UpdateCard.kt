package nl.tufein.openconnect.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.content.FileProvider
import nl.tufein.openconnect.BuildConfig
import nl.tufein.openconnect.OpenConnectApp
import nl.tufein.openconnect.R
import nl.tufein.openconnect.updates.UpdateError

/** Settings card for the in-app updater: check GitHub, verify the APK, then hand it to Android. */
@Composable
fun UpdateCard() {
    val context = LocalContext.current
    val controller = (context.applicationContext as OpenConnectApp).container.updates
    val state by controller.state.collectAsState()

    fun install() {
        val file = controller.apkFile() ?: return
        if (!context.packageManager.canRequestPackageInstalls()) {
            val settings = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(settings) }
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    FoldableCard("update", stringResource(R.string.settings_update)) {
        Text(stringResource(R.string.update_current, BuildConfig.VERSION_NAME))
        when {
            state.checking -> Text(stringResource(R.string.update_checking))
            state.upToDate -> Text(stringResource(R.string.update_latest))
            state.available != null -> {
                val release = state.available!!
                Text(stringResource(R.string.update_available, release.version))
                if (state.ready) {
                    QuietButton(onClick = ::install, modifier = androidx.compose.ui.Modifier.semantics {
                        contentDescription = "install-update"
                    }) { Text(stringResource(R.string.update_install)) }
                } else if (state.downloading) {
                    Text(stringResource(R.string.update_downloading, state.progress))
                } else {
                    QuietButton(onClick = controller::download) { Text(stringResource(R.string.update_download)) }
                }
            }
        }
        val error = when (state.error) {
            UpdateError.NETWORK -> R.string.update_error_network
            UpdateError.NO_RELEASE -> R.string.update_error_no_release
            UpdateError.CHECKSUM -> R.string.update_error_checksum
            UpdateError.NO_CHECKSUM -> R.string.update_error_no_checksum
            UpdateError.SIGNATURE -> R.string.update_error_signature
            UpdateError.STORAGE -> R.string.update_error_storage
            UpdateError.UNKNOWN -> R.string.update_error_unknown
            null -> null
        }
        if (error != null) Text(stringResource(error), color = MaterialTheme.colorScheme.error)
        QuietButton(onClick = controller::check, enabled = !state.checking && !state.downloading) {
            Text(stringResource(R.string.update_check))
        }
        Text(
            stringResource(R.string.update_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
