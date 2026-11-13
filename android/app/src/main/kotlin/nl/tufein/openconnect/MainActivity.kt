package nl.tufein.openconnect

import android.content.Intent
import android.graphics.Color
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import nl.tufein.openconnect.ui.AppViewModel
import nl.tufein.openconnect.ui.LockScreen
import nl.tufein.openconnect.ui.OpenConnectRoot
import nl.tufein.openconnect.ui.OpenConnectTheme

class MainActivity : ComponentActivity() {
    internal val viewModel: AppViewModel by viewModels()

    /** Android's fingerprint / face / screen-lock check; built in onCreate because it registers for a result. */
    private lateinit var unlocker: nl.tufein.openconnect.lock.Unlocker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        unlocker = (application as OpenConnectApp).unlockerOverride?.invoke(this) ?: nl.tufein.openconnect.lock.DeviceUnlocker(this)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIncoming(intent)
        setContent {
            val mode by viewModel.theme.collectAsState()
            val look by viewModel.look.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val dark = when (mode) {
                "dark" -> true
                "light" -> false
                else -> systemDark
            }
            // The bars follow the theme the person picked in Settings, not only the phone's.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
                )
                onDispose { }
            }
            OpenConnectTheme(mode, look.palette, look.style) {
                var pendingExport by remember { mutableStateOf<Pair<String, String>?>(null) }
                var pendingCsv by remember { mutableStateOf<String?>(null) }
                val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
                    if (uris.isNotEmpty()) viewModel.importUris(uris)
                }
                val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
                    val request = pendingExport
                    pendingExport = null
                    if (uri != null && request != null) viewModel.exportActivity(request.first, request.second, uri)
                }
                val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
                    val kind = pendingCsv
                    pendingCsv = null
                    if (uri != null && kind != null) viewModel.exportCsv(kind, uri)
                }
                val backupPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
                    if (uri != null) viewModel.backup(uri)
                }
                val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null) viewModel.restore(uri)
                }
                val watchFacePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null) viewModel.installWatchFace(uri)
                }
                val lockUi by viewModel.lock.collectAsState()
                val lockAvailable = remember(lockUi) { unlocker.available() }
                // With the lock on, the recent-apps list and screenshots must not show the data either.
                DisposableEffect(lockUi.enabled) {
                    if (lockUi.enabled) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    onDispose { }
                }
                // A phone that lost its screen lock has nothing to check against, so the app opens rather than lock you out.
                if (lockUi.locked && lockAvailable) {
                    LockScreen(onUnlock = { unlocker.unlock { ok -> if (ok) viewModel.unlocked() } })
                    LaunchedEffect(Unit) { unlocker.unlock { ok -> if (ok) viewModel.unlocked() } }
                    return@OpenConnectTheme
                }
                OpenConnectRoot(
                    lockAvailable = lockAvailable,
                    onAppLock = { on ->
                        // Switching it on needs the check first, so nobody locks themselves out by accident.
                        if (on) unlocker.unlock { ok -> if (ok) viewModel.setAppLock(true) } else viewModel.setAppLock(false)
                    },
                    onBackup = { backupPicker.launch(viewModel.backupFileName()) },
                    onRestore = { restorePicker.launch(arrayOf("*/*")) },
                    onWatchFace = { watchFacePicker.launch(arrayOf("*/*")) },
                    vm = viewModel,
                    onImport = { importPicker.launch(arrayOf("*/*")) },
                    onExport = { id, format ->
                        pendingExport = id to format
                        exportPicker.launch(viewModel.exportFileName(id, format))
                    },
                    onExportCsv = { kind ->
                        pendingCsv = kind
                        csvPicker.launch(viewModel.csvFileName(kind))
                    },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.lockReturned()
    }

    override fun onStop() {
        super.onStop()
        viewModel.lockLeft()
    }

    override fun onResume() {
        super.onResume()
        // Look for new data whenever the app comes to the front, and every ten minutes while it stays there.
        viewModel.onResume()
    }

    override fun onPause() {
        super.onPause()
        viewModel.onPause()
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

    private companion object {
        // The scrims Android itself uses behind the navigation bar on phones with three buttons.
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
