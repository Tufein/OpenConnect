package nl.tufein.openconnect.core.updates

import java.security.MessageDigest

/**
 * Who signed an APK, as SHA-256 digests of the signing certificates (lower-case hex). An update may only be offered to
 * Android when it is signed by the same key as the app that is installed, or by a key that the installed one has signed
 * over to (APK signature rotation lists both in the certificate history).
 */
object Signers {
    fun digest(certificate: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(certificate).joinToString("") { "%02x".format(it) }

    /**
     * True when at least one certificate of the download is also one of the installed app's. An app whose signers cannot be
     * read (empty set) never matches, so an unreadable file is refused rather than trusted.
     */
    fun compatible(installed: Set<String>, download: Set<String>): Boolean =
        installed.isNotEmpty() && download.any { it in installed }
}
