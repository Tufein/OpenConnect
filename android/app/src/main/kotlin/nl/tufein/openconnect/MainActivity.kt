package nl.tufein.openconnect

import android.content.Intent
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import nl.tufein.openconnect.ui.AppViewModel
import nl.tufein.openconnect.ui.OpenConnectRoot
import nl.tufein.openconnect.ui.OpenConnectTheme

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIncoming(intent)
        setContent {
            OpenConnectTheme {
                var pendingExport by remember { mutableStateOf<Pair<String, String>?>(null) }
                val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
                    if (uris.isNotEmpty()) viewModel.importUris(uris)
                }
                val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
                    val request = pendingExport
                    pendingExport = null
                    if (uri != null && request != null) viewModel.exportActivity(request.first, request.second, uri)
                }
                OpenConnectRoot(
                    vm = viewModel,
                    onImport = { importPicker.launch(arrayOf("*/*")) },
                    onExport = { id, format ->
                        pendingExport = id to format
                        exportPicker.launch(viewModel.exportFileName(id, format))
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncoming(intent)
    }

    /** A GPX/TCX/FIT file shared or opened with OpenConnect is imported straight away. */
    private fun handleIncoming(intent: Intent?) {
        if (intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            viewModel.onWatchAttached()
            return
        }
        val uris = when (intent?.action) {
            Intent.ACTION_VIEW -> listOfNotNull(intent.data)
            Intent.ACTION_SEND -> listOfNotNull(
                if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                },
            )
            else -> emptyList()
        }
        if (uris.isNotEmpty()) viewModel.importUris(uris)
    }
}
