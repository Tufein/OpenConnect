package nl.tufein.openconnect.updates

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import nl.tufein.openconnect.core.updates.Signers
import java.io.File

/**
 * Asks Android who signed the installed app and who signed a downloaded APK (the certificate history, so a rotated key that
 * the old one signed over to still counts). The comparison itself is [Signers.compatible], tested on the JVM. Reading a real
 * APK's signature needs a real phone: that part has not been tried on one.
 */
object ApkSigners {
    fun sameAsInstalled(context: Context, apk: File): Boolean = try {
        val pm = context.packageManager
        val installed = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val download = pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
        Signers.compatible(signers(installed), signers(download))
    } catch (e: Exception) {
        false
    }

    private fun signers(info: PackageInfo?): Set<String> {
        val signing = info?.signingInfo ?: return emptySet()
        val certificates = if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
        return certificates.orEmpty().map { Signers.digest(it.toByteArray()) }.toSet()
    }
}
