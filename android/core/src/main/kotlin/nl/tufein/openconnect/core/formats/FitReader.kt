package nl.tufein.openconnect.core.formats

/**
 * Low-level FIT reader shared by the activity importer and the watch-data reader.
 *
 * It walks the data messages of a file and hands each one to a callback with just the fields the caller
 * asked for. It handles big and little endian definitions, developer fields (skipped), array fields,
 * "invalid" markers, compressed-timestamp headers and the 16-bit timestamp watches use in monitoring files.
 */
internal object FitReader {
    /** Seconds between the Unix epoch and the FIT epoch (1989-12-31T00:00:00Z). */
    const val FIT_EPOCH_OFFSET = 631_065_600L

    /** FIT timestamps below this are not dates but a device's "seconds since power on". */
    const val MIN_DATE_TIME = 0x1000_0000L

    /** Marks an array element that holds the type's "invalid" value. */
    const val INVALID = Long.MIN_VALUE

    const val FIELD_TIMESTAMP = 253
    private const val FIELD_TIMESTAMP_16 = 26
    private const val MONITORING_MSG = 55

    /** The raw values of one message's wanted fields. Scalars are arrays of one element. */
    class Values(private val map: Map<Int, LongArray>) {
        /** First element of a field, or null when the field is absent or holds the invalid marker. */
        operator fun get(num: Int): Long? = map[num]?.firstOrNull()?.takeIf { it != INVALID }

        /** All elements of an array field, [INVALID] marking the elements without a value. */
        fun array(num: Int): LongArray? = map[num]
    }

    // base type number (code and 0x1F) -> element size
    private val TYPE_SIZE = intArrayOf(1, 1, 1, 2, 2, 4, 4, 1, 4, 8, 1, 2, 4, 1)
    private val SIGNED_TYPES = setOf(1, 3, 5)
    private val ZERO_INVALID_TYPES = setOf(10, 11, 12)

    private class Definition(val global: Int, val big: Boolean, val fields: List<IntArray>, val devBytes: Int)

    private fun u(data: ByteArray, at: Int, size: Int, big: Boolean): Long {
        var v = 0L
        for (i in 0 until size) {
            val b = (data[at + (if (big) i else size - 1 - i)].toLong() and 0xFF)
            v = (v shl 8) or b
        }
        return v
    }

    /** Check signature, sizes and both CRCs; throws [FormatError] with a readable reason. */
    fun verify(data: ByteArray) {
        if (data.size < 16 || String(data, 8, 4, Charsets.ISO_8859_1) != ".FIT") {
            throw FormatError("Not a FIT file (missing .FIT signature)")
        }
        val headerSize = data[0].toInt() and 0xFF
        if (headerSize != 12 && headerSize != 14) throw FormatError("Unsupported FIT header size $headerSize")
        val dataSize = u(data, 4, 4, big = false)
        val end = headerSize + dataSize + 2
        if (data.size < end) throw FormatError("FIT file is truncated")
        if (headerSize == 14) {
            val stored = u(data, 12, 2, big = false).toInt()
            if (stored != 0 && stored != Fit.crc16(data, 0, 12)) throw FormatError("FIT header CRC mismatch")
        }
        val endInt = end.toInt()
        if (u(data, endInt - 2, 2, big = false).toInt() != Fit.crc16(data, 0, endInt - 2)) {
            throw FormatError("FIT file CRC mismatch (file is corrupt)")
        }
    }

    /** Read the elements of one field; null for strings, floats and fields too short for their type. */
    private fun readField(data: ByteArray, at: Int, size: Int, baseCode: Int, big: Boolean): LongArray? {
        val type = baseCode and 0x1F
        if (type >= TYPE_SIZE.size || type == 7 || type == 8 || type == 9) return null
        val elem = TYPE_SIZE[type]
        if (size < elem) return null
        val bits = elem * 8
        val signed = type in SIGNED_TYPES
        val invalid = when {
            type in ZERO_INVALID_TYPES -> 0L
            signed -> (1L shl (bits - 1)) - 1
            else -> (1L shl bits) - 1
        }
        return LongArray(size / elem) { index ->
            val raw = u(data, at + index * elem, elem, big)
            when {
                raw == invalid -> INVALID
                signed && raw >= (1L shl (bits - 1)) -> raw - (1L shl bits)
                else -> raw
            }
        }
    }

    /**
     * Walk every data message. [wanted] returns the field numbers to read for a global message number,
     * or null to skip that message (its timestamp is still tracked). A message's timestamp, when it has
     * one, is always available as field [FIELD_TIMESTAMP]: compressed-timestamp headers and the 16-bit
     * timestamp of monitoring messages are expanded to a full one.
     */
    fun read(data: ByteArray, wanted: (global: Int) -> Set<Int>?, onMessage: (global: Int, values: Values) -> Unit) {
        verify(data)
        val headerSize = data[0].toInt() and 0xFF
        val end = headerSize + u(data, 4, 4, big = false).toInt()
        val definitions = arrayOfNulls<Definition>(16)
        var lastTs = 0L

        fun readMessage(def: Definition, startAt: Int, compressedTs: Long?): Int {
            var at = startAt
            val want = wanted(def.global)
            val values = HashMap<Int, LongArray>()
            for ((num, size, base) in def.fields) {
                if (want != null && num in want || num == FIELD_TIMESTAMP || num == FIELD_TIMESTAMP_16) {
                    readField(data, at, size, base, def.big)?.let { values[num] = it }
                }
                at += size
            }
            at += def.devBytes

            var ts: Long? = compressedTs ?: values[FIELD_TIMESTAMP]?.firstOrNull()?.takeIf { it != INVALID }
            if (ts == null && def.global == MONITORING_MSG && lastTs >= MIN_DATE_TIME) {
                val low = values[FIELD_TIMESTAMP_16]?.firstOrNull()?.takeIf { it != INVALID }
                if (low != null) ts = lastTs + ((low - (lastTs and 0xFFFF)) and 0xFFFF)
            }
            if (ts != null) {
                values[FIELD_TIMESTAMP] = longArrayOf(ts)
                lastTs = ts
            }
            if (want != null) onMessage(def.global, Values(values))
            return at
        }

        try {
            var pos = headerSize
            while (pos < end) {
                val hdr = data[pos++].toInt() and 0xFF
                when {
                    hdr and 0x80 != 0 -> { // compressed timestamp header: a data message
                        val def = definitions[(hdr shr 5) and 0x03] ?: throw FormatError("FIT data before its definition")
                        val offset = hdr and 0x1F
                        var ts = (lastTs and 0x1FL.inv()) + offset
                        if (offset < (lastTs and 0x1F)) ts += 0x20
                        pos = readMessage(def, pos, ts)
                    }
                    hdr and 0x40 != 0 -> { // definition message
                        val hasDev = hdr and 0x20 != 0
                        pos++ // reserved
                        val big = data[pos++].toInt() == 1
                        val global = u(data, pos, 2, big).toInt()
                        pos += 2
                        val count = data[pos++].toInt() and 0xFF
                        val fields = ArrayList<IntArray>(count)
                        repeat(count) {
                            fields.add(
                                intArrayOf(
                                    data[pos].toInt() and 0xFF,
                                    data[pos + 1].toInt() and 0xFF,
                                    data[pos + 2].toInt() and 0xFF,
                                ),
                            )
                            pos += 3
                        }
                        var devBytes = 0
                        if (hasDev) {
                            val devCount = data[pos++].toInt() and 0xFF
                            repeat(devCount) {
                                devBytes += data[pos + 1].toInt() and 0xFF
                                pos += 3
                            }
                        }
                        definitions[hdr and 0x0F] = Definition(global, big, fields, devBytes)
                    }
                    else -> { // data message
                        val def = definitions[hdr and 0x0F] ?: throw FormatError("FIT data before its definition")
                        pos = readMessage(def, pos, null)
                    }
                }
            }
        } catch (e: IndexOutOfBoundsException) {
            throw FormatError("Could not decode FIT file: malformed or truncated record", e)
        }
    }
}
