package nl.tufein.openconnect.core.watch

/** Why a watch face could not be copied, in terms the screen can explain. */
class WatchFaceException(val kind: Kind) : Exception(kind.name) {
    enum class Kind { NOT_PRG, TOO_BIG, EMPTY, NO_USB_WATCH, CANNOT_WRITE, ALREADY_THERE }
}

/**
 * Checks a watch face or Connect IQ app file before it goes to the watch. A `.prg` is what the Connect IQ SDK builds;
 * faces bought or downloaded in Garmin's store are tied to that watch and come through Garmin's own Connect IQ app.
 */
object WatchFace {
    /** Larger than any watch face; a file this big is something else. */
    const val MAX_BYTES = 8 * 1024 * 1024

    /** A safe file name for the watch: letters, digits, dash and underscore, ending in `.prg`. */
    fun fileName(original: String): String {
        val base = original.substringAfterLast('/').substringBeforeLast('.').replace(Regex("[^A-Za-z0-9_-]"), "_").trim('_').take(40)
        return (base.ifEmpty { "watchface" }) + ".prg"
    }

    fun check(original: String, bytes: ByteArray) {
        if (!original.endsWith(".prg", ignoreCase = true)) throw WatchFaceException(WatchFaceException.Kind.NOT_PRG)
        if (bytes.isEmpty()) throw WatchFaceException(WatchFaceException.Kind.EMPTY)
        if (bytes.size > MAX_BYTES) throw WatchFaceException(WatchFaceException.Kind.TOO_BIG)
    }

    /** Checks the file and copies it to the watch; throws [WatchFaceException] with the reason when it cannot. */
    fun install(source: WatchSource, original: String, bytes: ByteArray): String {
        check(original, bytes)
        val name = fileName(original)
        if (!source.installApp(name, bytes)) throw WatchFaceException(WatchFaceException.Kind.CANNOT_WRITE)
        return name
    }
}
