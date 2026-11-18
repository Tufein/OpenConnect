package nl.tufein.openconnect.updates

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.tufein.openconnect.core.updates.ReleaseInfo
import nl.tufein.openconnect.core.updates.UpdateHttp
import nl.tufein.openconnect.core.updates.get
import nl.tufein.openconnect.core.updates.isNewer
import nl.tufein.openconnect.core.updates.matchesSha256
import nl.tufein.openconnect.core.updates.parseRelease
import java.io.File
import java.io.IOException

enum class UpdateError { NETWORK, NO_RELEASE, CHECKSUM, NO_CHECKSUM, SIGNATURE, STORAGE, UNKNOWN }

data class UpdateState(
    val checking: Boolean = false,
    val available: ReleaseInfo? = null,
    val upToDate: Boolean = false,
    val downloading: Boolean = false,
    val progress: Int = 0,
    val ready: Boolean = false,
    val error: UpdateError? = null,
)

private class UpdateFailure(val kind: UpdateError) : RuntimeException(kind.name)

/**
 * Checks GitHub for a newer APK, verifies its checksum and its signer, and keeps it in the cache until installed.
 * A release without a checksum file is refused (it cannot be verified), and so is an APK that is not signed by the same key
 * as the installed app; [sameSigner] answers that for a downloaded file (Android would refuse it too, but with no reason).
 */
class UpdateController(
    private val dir: File,
    private val currentVersion: String,
    private val http: UpdateHttp,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val releasesUrl: String = "https://api.github.com/repos/Tufein/OpenConnect/releases/latest",
    private val sameSigner: (File) -> Boolean = { true },
) {
    private val _state = MutableStateFlow(UpdateState())
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** The verified file; null until [download] has finished successfully. */
    fun apkFile(): File? {
        val release = _state.value.available ?: return null
        return if (_state.value.ready) File(dir, "openconnect-v${release.version}.apk") else null
    }

    fun check() {
        if (_state.value.checking) return
        _state.update { it.copy(checking = true, error = null, available = null, upToDate = false, ready = false) }
        scope.launch(io) {
            try {
                val text = http.get(releasesUrl).toString(Charsets.UTF_8)
                val release = parseRelease(text) ?: throw UpdateFailure(UpdateError.NO_RELEASE)
                _state.update {
                    if (isNewer(currentVersion, release.version)) it.copy(available = release)
                    else it.copy(upToDate = true)
                }
            } catch (e: IOException) {
                _state.update { it.copy(error = UpdateError.NETWORK) }
            } catch (e: UpdateFailure) {
                _state.update { it.copy(error = e.kind) }
            } catch (e: Exception) {
                _state.update { it.copy(error = UpdateError.UNKNOWN) }
            } finally {
                _state.update { it.copy(checking = false) }
            }
        }
    }

    fun download() {
        val release = _state.value.available ?: return
        if (_state.value.downloading) return
        _state.update { it.copy(downloading = true, progress = 0, error = null) }
        scope.launch(io) {
            try {
                if (!dir.exists() && !dir.mkdirs()) throw UpdateFailure(UpdateError.STORAGE)
                val file = File(dir, "openconnect-v${release.version}.apk")
                val bytes = if (file.exists()) file.readBytes() else {
                    http.get(release.apkUrl) { read ->
                        val size = release.sizeBytes
                        val percent = if (size != null && size > 0) (read * 100L / size).toInt().coerceIn(0, 99)
                        else (read / 1024 / 1024).coerceAtMost(99)
                        _state.update { it.copy(progress = percent) }
                    }
                }
                // No checksum file means nothing to check the download against: refuse rather than trust it.
                val checksumUrl = release.checksumUrl ?: throw UpdateFailure(UpdateError.NO_CHECKSUM)
                val checksum = http.get(checksumUrl).toString(Charsets.UTF_8)
                if (!matchesSha256(bytes, checksum)) {
                    file.delete()
                    throw UpdateFailure(UpdateError.CHECKSUM)
                }
                file.writeBytes(bytes)
                if (!sameSigner(file)) {
                    file.delete()
                    throw UpdateFailure(UpdateError.SIGNATURE)
                }
                _state.update { it.copy(progress = 100, ready = true) }
            } catch (e: IOException) {
                _state.update { it.copy(error = UpdateError.NETWORK) }
            } catch (e: UpdateFailure) {
                _state.update { it.copy(error = e.kind) }
            } catch (e: Exception) {
                _state.update { it.copy(error = UpdateError.UNKNOWN) }
            } finally {
                _state.update { it.copy(downloading = false) }
            }
        }
    }
}
