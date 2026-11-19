// OpenConnect, SPDX-License-Identifier: AGPL-3.0-or-later
package nl.tufein.openconnect.core.watch.ble

/**
 * The watch's protobuf questions (GFDI 5043, Garmin's "Smart" message), as far as this app deals with them: it does not
 * answer any of them, but it must say so the way Gadgetbridge does, or the watch waits for an answer, asks again a minute
 * later, and (seen on a fenix 8) stops answering anything else after the second round.
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
    )
}
