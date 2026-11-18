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

/**
 * Is [available] a newer version than [current]? `"0.47.0"` vs `"0.47.1"`: the numbers before any `-` are compared as numbers.
 * A pre-release (`1.0.0-rc.1`) is older than the same version without the suffix (`1.0.0`), as in semantic versioning, so a
 * phone on a release candidate is offered the final release, and a phone on the final release is never offered a candidate.
 * Between two pre-releases the dot-separated parts are compared one by one, numbers as numbers and below words (`rc.2` > `rc.1`).
 */
fun isNewer(current: String, available: String): Boolean = compareVersions(current, available) < 0

private fun compareVersions(a: String, b: String): Int {
    val (coreA, preA) = splitVersion(a)
    val (coreB, preB) = splitVersion(b)
    for (i in 0 until maxOf(coreA.size, coreB.size)) {
        val left = coreA.getOrNull(i) ?: "0"
        val right = coreB.getOrNull(i) ?: "0"
        val ln = left.toIntOrNull()
        val rn = right.toIntOrNull()
        val cmp = if (ln != null && rn != null) ln.compareTo(rn) else left.compareTo(right)
        if (cmp != 0) return cmp
    }
    return when {
        preA.isEmpty() && preB.isEmpty() -> 0
        preA.isEmpty() -> 1 // the final release is newer than any candidate of it
        preB.isEmpty() -> -1
        else -> {
            for (i in 0 until maxOf(preA.size, preB.size)) {
                val left = preA.getOrNull(i) ?: return -1 // fewer parts: older (rc < rc.1)
                val right = preB.getOrNull(i) ?: return 1
                val ln = left.toIntOrNull()
                val rn = right.toIntOrNull()
                val cmp = when {
                    ln != null && rn != null -> ln.compareTo(rn)
                    ln != null -> -1 // numbers sort below words
                    rn != null -> 1
                    else -> left.compareTo(right)
                }
                if (cmp != 0) return cmp
            }
            0
        }
    }
}

/** `"v1.0.0-rc.1+build5"` into the number parts `[1, 0, 0]` and the pre-release parts `[rc, 1]`; build metadata is ignored. */
private fun splitVersion(version: String): Pair<List<String>, List<String>> {
    val clean = version.trim().removePrefix("v").substringBefore('+')
    val core = clean.substringBefore('-')
    val pre = if ('-' in clean) clean.substringAfter('-') else ""
    return core.split('.') to pre.split('.').filter { it.isNotEmpty() }
}

/** Find the signed APK and its checksum in a GitHub release. The tag is the version. */
fun parseRelease(json: String): ReleaseInfo? {
    val root = runCatching { Json.parseToJsonElement(json) as? JsonObject }.getOrNull() ?: return null
    // The updater asks for the latest full release, which GitHub never fills with a pre-release or a draft; if one is
    // handed over anyway (another address, a changed server), it is refused here too.
    if (root.text("prerelease") == "true" || root.text("draft") == "true") return null
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

