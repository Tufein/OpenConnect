package nl.tufein.openconnect.core.updates

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest

/** What the updater needs from GitHub's "latest release" answer. */
data class ReleaseInfo(
    val version: String,
    val apkUrl: String,
    val checksumUrl: String?,
    val sizeBytes: Long? = null,
    val notes: String = "",
)

/** `"0.47.0"` vs `"0.47.1"`: compare version parts as numbers, and unknown parts as text. */
fun isNewer(current: String, available: String): Boolean {
    val a = current.trim().removePrefix("v").split('.')
    val b = available.trim().removePrefix("v").split('.')
    for (i in 0 until maxOf(a.size, b.size)) {
        val left = a.getOrNull(i) ?: "0"
        val right = b.getOrNull(i) ?: "0"
        val ln = left.toIntOrNull()
        val rn = right.toIntOrNull()
        val cmp = when {
            ln != null && rn != null -> ln.compareTo(rn)
            else -> left.compareTo(right)
        }
        if (cmp != 0) return cmp < 0
    }
    return false
}

/** Find the signed APK and its checksum in a GitHub release. The tag is the version. */
fun parseRelease(json: String): ReleaseInfo? {
    val root = runCatching { Json.parseToJsonElement(json) as? JsonObject }.getOrNull() ?: return null
    val tag = root.text("tag_name")?.removePrefix("v")?.takeIf { it.isNotBlank() } ?: return null
    val assets = runCatching { root.getValue("assets").jsonArray }.getOrNull() ?: return null
    var apk: JsonObject? = null
    var checksum: JsonObject? = null
    for (element in assets) {
        val asset = element as? JsonObject ?: continue
        val name = asset.text("name") ?: continue
        if (name == "openconnect-v$tag.apk" || (name.endsWith(".apk") && apk == null)) apk = asset
        if (name == "openconnect-v$tag.apk.sha256" || (name.endsWith(".sha256") && checksum == null)) checksum = asset
    }
    val url = apk?.text("browser_download_url") ?: return null
    return ReleaseInfo(
        version = tag,
        apkUrl = url,
        checksumUrl = checksum?.text("browser_download_url"),
        sizeBytes = apk?.get("size")?.runCatching { jsonPrimitive.content.toLongOrNull() }?.getOrNull(),
        notes = root.text("body").orEmpty(),
    )
}

/** The checksum file has the hash and the file name on one line; only the hash matters here. */
fun matchesSha256(bytes: ByteArray, checksumFile: String): Boolean {
    val expected = Regex("[0-9a-fA-F]{64}").find(checksumFile)?.value?.lowercase() ?: return false
    val actual = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    return expected == actual
}

private fun JsonObject.text(key: String): String? = this[key]?.runCatching { jsonPrimitive.content }?.getOrNull()

