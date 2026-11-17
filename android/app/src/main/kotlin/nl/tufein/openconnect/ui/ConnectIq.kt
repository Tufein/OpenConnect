package nl.tufein.openconnect.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import nl.tufein.openconnect.core.watch.ConnectIqStore
import nl.tufein.openconnect.core.watch.StoreStep

/**
 * Walks the steps [ConnectIqStore.steps] gives and stops at the first one the phone can open (v0.43.0).
 *
 * The two things that touch Android are handed in, so the order can be tested without a phone: [launchIntent] is
 * `PackageManager.getLaunchIntentForPackage` (null when the app is not installed) and [start] is
 * `Context.startActivity` (throws when nothing can take the intent).
 */
class ConnectIqLauncher(
    private val launchIntent: (packageName: String) -> Intent?,
    private val start: (Intent) -> Unit,
) {
    /** True when Garmin's Connect IQ app is on the phone. */
    val installed: Boolean get() = launchIntent(ConnectIqStore.PACKAGE) != null

    /** Returns the step that opened, or null when nothing on the phone could open any of them. */
    fun open(listing: String = ConnectIqStore.LISTING_ID): StoreStep? {
        for (step in ConnectIqStore.steps(listing, installed)) {
            val intent = when (step) {
                is StoreStep.LaunchApp -> launchIntent(step.packageName)
                is StoreStep.OpenAddress -> Intent(Intent.ACTION_VIEW, Uri.parse(step.url)).also { view -> step.packageName?.let(view::setPackage) }
            } ?: continue
            try {
                start(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return step
            } catch (_: ActivityNotFoundException) {
                // Nothing takes this one (no Play Store, or Garmin's app does not open store addresses): try the next.
            } catch (_: SecurityException) {
                // The activity exists but may not be started from outside: same answer.
            }
        }
        return null
    }
}

/**
 * Opens Garmin's Connect IQ app, or the place to get it. Seeing whether that app is installed needs the `<queries>`
 * entry for `com.garmin.connectiq` in the manifest (Android 11 and newer hide other apps otherwise).
 */
fun openConnectIq(context: Context, listing: String = ConnectIqStore.LISTING_ID): StoreStep? =
    ConnectIqLauncher(context.packageManager::getLaunchIntentForPackage, context::startActivity).open(listing)
