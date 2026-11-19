// OpenConnect, SPDX-License-Identifier: AGPL-3.0-or-later
package nl.tufein.openconnect.core.watch.ble

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
}
