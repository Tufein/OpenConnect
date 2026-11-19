// OpenConnect, SPDX-License-Identifier: AGPL-3.0-or-later
package nl.tufein.openconnect.notify

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.util.Locale

/**
 * The apps the person can choose from: those that have an icon in the launcher, which are the apps a person knows by
 * name (a system service that posts notifications has none). Needs the launcher query in the manifest, because Android
 * hides other apps from an app that does not ask.
 */
class AndroidInstalledApps(private val context: Context) : InstalledApps {
    override fun list(): List<InstalledApp> {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(launcher, 0)
            .asSequence()
            .map { it.activityInfo.packageName }
            .filter { it != context.packageName }
            .distinct()
            .map { InstalledApp(it, labelOf(it)) }
            .sortedBy { it.label.lowercase(Locale.ROOT) }
            .toList()
    }

    /** The name the person sees under the app's icon; the package name when Android will not say. */
    fun labelOf(packageName: String): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString().ifBlank { packageName }
    } catch (_: PackageManager.NameNotFoundException) {
        packageName
    }
}
