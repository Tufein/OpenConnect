// OpenConnect, SPDX-License-Identifier: AGPL-3.0-or-later
package nl.tufein.openconnect.notify

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * The few questions about the phone that forwarding asks, kept apart so the rest has no Android types: has the person
 * given "notification access", may the app use Bluetooth, is the screen on. Android shows its own screens for the first
 * two; this builds the intents that open them.
 */
object ForwardAccess {
    /** Android's "notification access" for this app: the switch in Settings that lets it read other apps' notifications. */
    fun hasNotificationAccess(context: Context): Boolean =
        context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

    /** Android 12 and newer ask for "nearby devices" (Bluetooth connect); older versions grant Bluetooth at install. */
    fun bluetoothAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    fun screenOn(context: Context): Boolean = context.getSystemService(PowerManager::class.java)?.isInteractive ?: true

    /** The "Notification access" screen, straight to this app's switch where Android 11 and newer allow it. */
    fun accessSettingsIntent(context: Context): Intent {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(context, PhoneNotificationListener::class.java).flattenToString(),
            )
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /**
     * The app's own settings page. On Android 13 and newer an app that was installed from a file (not a store) cannot
     * be given notification access until the person chose "Allow restricted settings" in that page's menu.
     */
    fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** True when the Garmin Connect app is on this phone: it may be holding the watch's one phone link. */
    fun garminConnectInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(GARMIN_CONNECT, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    /** Android's list of apps that are not limited by battery saving; the person can add OpenConnect there. */
    fun batterySettingsIntent(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private const val GARMIN_CONNECT = "com.garmin.android.apps.connectmobile"

    /** True when this phone may show the "restricted settings" step: Android 13 or newer. */
    val mayNeedRestrictedSettings: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}
