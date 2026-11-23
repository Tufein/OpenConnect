// OpenConnect, SPDX-License-Identifier: AGPL-3.0-or-later
package nl.tufein.openconnect.core.watch.ble

/**
 * The watch's protobuf questions (GFDI 5043, Garmin's "Smart" message), as far as this app deals with them. Most it does
 * not answer, but it must say so the way Gadgetbridge does, or the watch waits for an answer, asks again a minute later,
 * and (seen on a fenix 8) stops answering anything else after the second round. A few it answers with "nothing", as
 * Gadgetbridge does when it has nothing to give ([replyTo]): the calendar, which the watch otherwise asks for every 30
 * seconds, and the web requests of Connect IQ widgets, which otherwise wait half a minute and give up.
 *
 * The names come from Gadgetbridge's `gdi_smart_proto.proto` and the service files next to it; only the first field of
 * the message is read, for the log. No content is ever logged.
 */
object GarminProtobuf {
    /** Gadgetbridge's ProtobufChunkStatus: the chunk was kept, or thrown away. */
    const val CHUNK_KEPT = 0
    const val CHUNK_DISCARDED = 1

    /** Gadgetbridge's ProtobufStatusCode values that this app uses. */
    const val NO_ERROR = 0
    const val UNKNOWN_REQUEST_ID = 100

    /** One chunk of a protobuf message from the watch: which request, where it starts, the whole size, and the bytes. */
    class Chunk(val requestId: Int, val offset: Int, val total: Int, val data: ByteArray) {
        /** True for the chunk that completes the message (a message that fits in one chunk is complete at once). */
        val last: Boolean get() = offset.toLong() + data.size >= total.toLong()
    }

    fun parse(message: GfdiMessage): Chunk {
        val r = message.reader()
        val requestId = r.short()
        val offset = r.int()
        val total = r.int()
        val length = r.int()
        val data = r.bytes(minOf(maxOf(length, 0), r.remaining))
        return Chunk(requestId, offset, total, data)
    }

    /**
     * The answer to a chunk, as Gadgetbridge sends it: RESPONSE to [type], ACK, the request id, the chunk's offset, then
     * "kept, no error" for a chunk that is not the last, and "discarded, unknown request" for the last one, because this
     * app answers no protobuf request. That tells the watch to stop waiting instead of asking again.
     */
    fun answer(type: Int, chunk: Chunk): GfdiMessage = GfdiMessage.response(type) {
        short(chunk.requestId)
        int(chunk.offset)
        if (chunk.last) {
            byte(CHUNK_DISCARDED)
            byte(UNKNOWN_REQUEST_ID)
        } else {
            byte(CHUNK_KEPT)
            byte(NO_ERROR)
        }
    }

    /** How every log line about a protobuf question starts; the log keeps one line per question that comes back. */
    const val LOG_PREFIX = "watch asked over protobuf: "

    /** Device status (Smart field 8): the battery question Gadgetbridge asks once the handshake is done. */
    private const val DEVICE_STATUS_SERVICE = 8
    private const val BATTERY_REQUEST = 2
    private const val BATTERY_RESPONSE = 3

    /** `Smart.device_status_service { remote_device_battery_status_request {} }`, byte for byte what Gadgetbridge sends. */
    val BATTERY_QUESTION: ByteArray = field(DEVICE_STATUS_SERVICE, field(BATTERY_REQUEST, ByteArray(0)))

    /** The battery question as a GFDI protobuf request, to send when the watch is ready. */
    fun batteryRequest(requestId: Int): GfdiMessage = GfdiMessage.build(GfdiType.PROTOBUF_REQUEST) {
        short(requestId)
        int(0)
        int(BATTERY_QUESTION.size)
        int(BATTERY_QUESTION.size)
        bytes(BATTERY_QUESTION)
    }

    /**
     * The charge in [bytes], the watch's answer to [batteryRequest], or null when it is not that answer or the level is
     * not 0..100 — a half-understood message is no reason to show a wrong number.
     */
    fun batteryLevel(bytes: ByteArray): Int? {
        val outer = Varints(bytes)
        val tag = outer.varint() ?: return null
        if (wireType(tag) != 2 || fieldNumber(tag) != DEVICE_STATUS_SERVICE) return null
        val service = outer.take(outer.varint()?.toInt() ?: return null) ?: return null
        val inner = Varints(service)
        val innerTag = inner.varint() ?: return null
        if (wireType(innerTag) != 2 || fieldNumber(innerTag) != BATTERY_RESPONSE) return null
        val body = inner.take(inner.varint()?.toInt() ?: return null) ?: return null
        val response = Varints(body)
        var level: Int? = null
        while (true) {
            val fieldTag = response.varint() ?: break
            when (wireType(fieldTag)) {
                0 -> {
                    val value = response.varint() ?: break
                    if (fieldNumber(fieldTag) == 2) level = value.toInt()
                }
                2 -> response.take(response.varint()?.toInt() ?: break) ?: break
                else -> break
            }
        }
        return level?.takeIf { it in 0..100 }
    }

    /** Confirms a chunk of an answer this app asked for, so the watch knows it arrived and does not ask again. */
    fun accepted(type: Int, chunk: Chunk): GfdiMessage = GfdiMessage.response(type) {
        short(chunk.requestId)
        int(chunk.offset)
        byte(CHUNK_KEPT)
        byte(NO_ERROR)
    }

    private fun fieldNumber(tag: Long): Int = (tag ushr 3).toInt()
    private fun wireType(tag: Long): Int = (tag and 0x07L).toInt()

    /** An answer this app gives: the bytes of the Smart message to send back, and what it says, for the log. */
    class Reply(val smart: ByteArray, val said: String)

    private const val CALENDAR_SERVICE = 1
    private const val CALENDAR_EVENTS_REQUEST = 1
    private const val CALENDAR_EVENTS_RESPONSE = 2
    private const val CALENDAR_OK = 1

    private const val CONNECT_IQ_HTTP_SERVICE = 2
    private const val CONNECT_IQ_HTTP_REQUEST = 1
    private const val CONNECT_IQ_HTTP_RESPONSE = 2
    private const val HTTP_TIMED_OUT = 300
    private const val RAW_RESOURCE_REQUEST = 5
    private const val RAW_RESOURCE_RESPONSE = 6
    private const val RAW_TIMED_OUT = 200

    private const val NO_NETWORK = "answered: no network (this app does not fetch web pages for Connect IQ)"

    /**
     * The answer to a whole question [message], or null when this app does not answer it (it is then told "unknown request").
     *
     * - calendar_events_request: "OK, no events", what Gadgetbridge sends with calendar sync off (gdi_calendar.proto).
     *   Told "unknown request" instead, a fenix 8 asked again every 30 seconds.
     * - raw_resource_request and connect_iq_http_request: "the network request timed out", what Gadgetbridge sends for an
     *   address it cannot fetch (gdi_http.proto). The widget gives up at once instead of after half a minute.
     */
    fun replyTo(message: ByteArray): Reply? {
        val reader = Varints(message)
        val tag = reader.varint() ?: return null
        if ((tag and 0x07L) != 2L) return null
        reader.varint() ?: return null // the length of the service message
        val inner = reader.varint() ?: return null
        val service = (tag ushr 3).toInt()
        val request = (inner ushr 3).toInt()
        return when {
            service == CALENDAR_SERVICE && request == CALENDAR_EVENTS_REQUEST ->
                Reply(field(CALENDAR_SERVICE, field(CALENDAR_EVENTS_RESPONSE, number(1, CALENDAR_OK))), "answered: no calendar events")
            service == CONNECT_IQ_HTTP_SERVICE && request == RAW_RESOURCE_REQUEST ->
                Reply(field(CONNECT_IQ_HTTP_SERVICE, field(RAW_RESOURCE_RESPONSE, number(1, RAW_TIMED_OUT))), NO_NETWORK)
            service == CONNECT_IQ_HTTP_SERVICE && request == CONNECT_IQ_HTTP_REQUEST ->
                Reply(field(CONNECT_IQ_HTTP_SERVICE, field(CONNECT_IQ_HTTP_RESPONSE, number(1, HTTP_TIMED_OUT))), NO_NETWORK)
            else -> null
        }
    }

    /** The answer [smart] to request [requestId], in one piece (they are all far below Gadgetbridge's 375-byte chunks). */
    fun response(requestId: Int, smart: ByteArray): GfdiMessage = GfdiMessage.build(GfdiType.PROTOBUF_RESPONSE) {
        short(requestId)
        int(0)
        int(smart.size)
        int(smart.size)
        bytes(smart)
    }

    /** Field [number] holding the message [inner] (wire type 2). */
    private fun field(number: Int, inner: ByteArray): ByteArray = varint((number.toLong() shl 3) or 2) + varint(inner.size.toLong()) + inner

    /** Field [number] holding the whole number [value] (wire type 0). */
    private fun number(number: Int, value: Int): ByteArray = varint(number.toLong() shl 3) + varint(value.toLong())

    private fun varint(value: Long): ByteArray {
        val out = ArrayList<Byte>()
        var v = value
        while (v >= 0x80) {
            out += ((v and 0x7F) or 0x80).toByte()
            v = v ushr 7
        }
        out += v.toByte()
        return out.toByteArray()
    }

    /** What a message is about, for the log: `core_service, get_location_request`, or field numbers for unknown ones. */
    fun describe(bytes: ByteArray): String {
        val reader = Varints(bytes)
        val tag = reader.varint() ?: return "an empty message"
        val field = (tag ushr 3).toInt()
        val service = SERVICES[field] ?: "service $field"
        if ((tag and 0x07L) != 2L) return service
        reader.varint() ?: return service // the length of the service message
        val inner = reader.varint() ?: return service
        val sub = (inner ushr 3).toInt()
        val request = REQUESTS[field]?.get(sub) ?: "field $sub"
        return "$service, $request"
    }

    private class Varints(private val bytes: ByteArray) {
        private var pos = 0

        fun varint(): Long? {
            var result = 0L
            var shift = 0
            while (pos < bytes.size && shift < 64) {
                val b = bytes[pos++].toInt() and 0xFF
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
            }
            return null
        }

        /** The next [count] bytes, or null when there are not that many. */
        fun take(count: Int): ByteArray? {
            if (count < 0 || pos + count > bytes.size) return null
            val out = bytes.copyOfRange(pos, pos + count)
            pos += count
            return out
        }
    }

    /** The services in Garmin's Smart message, by field number. */
    private val SERVICES: Map<Int, String> = mapOf(
        1 to "calendar_events_service", 2 to "connect_iq_http_service", 3 to "connect_iq_installed_apps_service",
        4 to "connect_iq_app_settings_service", 7 to "data_transfer_service", 8 to "device_status_service",
        9 to "incident_detection_service", 10 to "audio_prompts_service", 11 to "wifi_setup_service",
        12 to "find_my_watch_service", 13 to "core_service", 14 to "group_live_track_service",
        16 to "sms_notification_service", 18 to "instant_input_service", 21 to "live_track_service",
        22 to "explore_sync_service", 23 to "way_point_transfer_service", 24 to "device_message_service",
        27 to "credentials_service", 30 to "event_sharing", 31 to "generic_item_transfer_service",
        36 to "simple_setup_service", 42 to "realtime_settings_service", 43 to "file_access_service",
        46 to "deeplinking_service", 47 to "garmin_connect_service", 49 to "gncs_service",
        56 to "live_session_service", 61 to "heart_rate_service", 62 to "running_service",
        63 to "accessory_utilities_service", 67 to "connection_management_service", 69 to "live_sync_service",
        71 to "subscription_service",
    )

    /** The requests inside the services the watch is most likely to ask, by service field and then request field. */
    private val REQUESTS: Map<Int, Map<Int, String>> = mapOf(
        1 to mapOf(1 to "calendar_events_request", 3 to "calendar_request"),
        2 to mapOf(
            1 to "connect_iq_http_request", 3 to "connect_iq_image_request", 5 to "raw_resource_request",
            7 to "connect_iq_oauth_request", 9 to "connect_iq_oauth_complete_request", 11 to "open_webpage_notification",
        ),
        13 to mapOf(
            1 to "sync_request", 2 to "sync_response", 3 to "get_location_request",
            5 to "location_updated_set_enabled_request", 7 to "location_updated_notification",
            8 to "feature_capabilities_request", 10 to "garmin_guid_overwrite_request", 12 to "dual_pairing_request",
            14 to "connection_ready_notification", 15 to "check_pending_software_request", 17 to "time_sync_request",
        ),
        49 to mapOf(
            1 to "media_request", 3 to "blocked_app_hash_notification", 4 to "app_status_request",
        ),
        43 to mapOf(
            1 to "pull_item_request", 3 to "push_item_request", 5 to "transfer_status_request", 9 to "item_list_request",
            12 to "item_added_notification", 17 to "item_updated_notification", 20 to "resource_update_notification",
            22 to "sync_button_notification",
        ),
    )
}
