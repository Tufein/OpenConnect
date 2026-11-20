// OpenConnect, SPDX-License-Identifier: AGPL-3.0-or-later
package nl.tufein.openconnect.core.watch.ble

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The watch's protobuf questions: answered the way Gadgetbridge answers what it does not handle, and named for the log. */
class GarminProtobufTest {
    private fun request(requestId: Int, offset: Int, total: Int, data: ByteArray) =
        GfdiMessage.build(GfdiType.PROTOBUF_REQUEST) { short(requestId); int(offset); int(total); int(data.size); bytes(data) }

    /** The answer as numbers: original type, status, request id, offset, chunk status, status code. */
    private fun fields(answer: GfdiMessage): List<Int> {
        val r = answer.reader()
        return listOf(r.short(), r.byte(), r.short(), r.int(), r.byte(), r.byte())
    }

    @Test
    fun `a whole request is answered discarded, unknown request, so the watch stops waiting`() {
        val chunk = GarminProtobuf.parse(request(12, 0, 3, byteArrayOf(8, 1, 0)))
        assertTrue(chunk.last)
        val answer = GarminProtobuf.answer(GfdiType.PROTOBUF_REQUEST, chunk)
        assertEquals(GfdiType.RESPONSE, answer.type)
        assertEquals(listOf(GfdiType.PROTOBUF_REQUEST, GfdiStatus.ACK, 12, 0, GarminProtobuf.CHUNK_DISCARDED, GarminProtobuf.UNKNOWN_REQUEST_ID), fields(answer))
        assertEquals(11, answer.payload.size, "Gadgetbridge's ProtobufStatusMessage: 2 + 1 + 2 + 4 + 1 + 1 bytes")
    }

    @Test
    fun `a request in parts is kept until the last part, which is discarded`() {
        val first = GarminProtobuf.parse(request(7, 0, 10, ByteArray(6)))
        assertFalse(first.last)
        assertEquals(listOf(GfdiType.PROTOBUF_REQUEST, GfdiStatus.ACK, 7, 0, GarminProtobuf.CHUNK_KEPT, GarminProtobuf.NO_ERROR), fields(GarminProtobuf.answer(GfdiType.PROTOBUF_REQUEST, first)))
        val second = GarminProtobuf.parse(request(7, 6, 10, ByteArray(4)))
        assertTrue(second.last)
        assertEquals(listOf(GfdiType.PROTOBUF_REQUEST, GfdiStatus.ACK, 7, 6, GarminProtobuf.CHUNK_DISCARDED, GarminProtobuf.UNKNOWN_REQUEST_ID), fields(GarminProtobuf.answer(GfdiType.PROTOBUF_REQUEST, second)))
    }

    @Test
    fun `the log names the service and the request, never more`() {
        // Smart { core_service (13) { get_location_request (3) { } } }: tag 13<<3|2 = 0x6a, length 2, tag 3<<3|2 = 0x1a, length 0
        assertEquals("core_service, get_location_request", GarminProtobuf.describe(byteArrayOf(0x6a, 2, 0x1a, 0)))
        // gncs_service (49): tag 49<<3|2 = 394 = varint 0x8a 0x03; app_status_request (4): 0x22
        assertEquals("gncs_service, app_status_request", GarminProtobuf.describe(byteArrayOf(0x8a.toByte(), 3, 2, 0x22, 0)))
        // connect_iq_http_service (2), connect_iq_http_request (1)
        assertEquals("connect_iq_http_service, connect_iq_http_request", GarminProtobuf.describe(byteArrayOf(0x12, 3, 0x0a, 1, 0)))
        // a service this app has no name for, with a request it does not know
        assertEquals("service 90, field 5", GarminProtobuf.describe(byteArrayOf(0xd2.toByte(), 5, 2, 0x2a, 0)))
        // a field that is not a message, and nothing at all
        assertEquals("calendar_events_service", GarminProtobuf.describe(byteArrayOf(8, 1)))
        assertEquals("an empty message", GarminProtobuf.describe(ByteArray(0)))
        // a cut-off message is named as far as it goes
        assertEquals("core_service", GarminProtobuf.describe(byteArrayOf(0x6a)))
    }

    @Test
    fun `a length that claims more than the message holds is cut to what is there`() {
        val lying = GfdiMessage.build(GfdiType.PROTOBUF_REQUEST) { short(1); int(0); int(500); int(500); bytes(byteArrayOf(0x6a, 0)) }
        val chunk = GarminProtobuf.parse(lying)
        assertEquals(2, chunk.data.size)
        assertFalse(chunk.last)
    }

    private fun hex(bytes: ByteArray) = bytes.joinToString(" ") { "%02x".format(it.toInt() and 0xFF) }

    @Test
    fun `the calendar question gets an empty calendar, as Gadgetbridge answers with calendar sync off`() {
        // Smart { calendar_events_service (1) { calendar_events_request (1) { start_date (1) = 5 } } }
        val reply = GarminProtobuf.replyTo(byteArrayOf(0x0a, 4, 0x0a, 2, 0x08, 5))
        assertNotNull(reply)
        // Smart { calendar_events_service (1) { calendar_events_response (2) { status (1) = OK (1) } } }
        assertEquals("0a 04 12 02 08 01", hex(reply.smart))
        assertEquals("answered: no calendar events", reply.said)
    }

    @Test
    fun `a Connect IQ web question is told the network timed out, so the widget stops waiting`() {
        // connect_iq_http_service (2) { raw_resource_request (5) { url (1) = "a" } }
        val raw = GarminProtobuf.replyTo(byteArrayOf(0x12, 5, 0x2a, 3, 0x0a, 1, 0x61))
        assertNotNull(raw)
        // raw_resource_response (6) { status (1) = NETWORK_REQUEST_TIMED_OUT (200) }
        assertEquals("12 05 32 03 08 c8 01", hex(raw.smart))
        // connect_iq_http_service (2) { connect_iq_http_request (1) { url (1) = "a" } }
        val web = GarminProtobuf.replyTo(byteArrayOf(0x12, 5, 0x0a, 3, 0x0a, 1, 0x61))
        assertNotNull(web)
        // connect_iq_http_response (2) { status (1) = NETWORK_REQUEST_TIMED_OUT (300) }
        assertEquals("12 05 12 03 08 ac 02", hex(web.smart))
        assertEquals("answered: no network (this app does not fetch web pages for Connect IQ)", web.said)
    }

    @Test
    fun `everything else is not answered here`() {
        assertNull(GarminProtobuf.replyTo(byteArrayOf(0x6a, 2, 0x1a, 0)), "core_service, get_location_request")
        assertNull(GarminProtobuf.replyTo(byteArrayOf(0x0a, 2, 0x1a, 0)), "calendar_request, the newer kind, is not answered")
        assertNull(GarminProtobuf.replyTo(byteArrayOf(0x12, 2, 0x1a, 0)), "a Connect IQ image request")
        assertNull(GarminProtobuf.replyTo(byteArrayOf(8, 1)), "a service that is not a message")
        assertNull(GarminProtobuf.replyTo(ByteArray(0)))
        assertNull(GarminProtobuf.replyTo(byteArrayOf(0x0a)), "cut off")
    }

    @Test
    fun `an answer is a protobuf response with the request's number, in one piece`() {
        val smart = byteArrayOf(0x0a, 4, 0x12, 2, 0x08, 1)
        val message = GarminProtobuf.response(77, smart)
        assertEquals(GfdiType.PROTOBUF_RESPONSE, message.type)
        val r = message.reader()
        assertEquals(listOf(77, 0, 6, 6), listOf(r.short(), r.int(), r.int(), r.int()))
        assertEquals(hex(smart), hex(r.rest()))
    }

    @Test
    fun `the watch telling of a new file is named`() {
        // file_access_service (43): tag 43<<3|2 = 346 = varint 0xda 0x02; item_added_notification (12): 0x62
        assertEquals("file_access_service, item_added_notification", GarminProtobuf.describe(byteArrayOf(0xda.toByte(), 2, 2, 0x62, 0)))
    }
}
