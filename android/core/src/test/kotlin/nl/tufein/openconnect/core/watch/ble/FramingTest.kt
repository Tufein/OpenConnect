// OpenConnect, SPDX-License-Identifier: AGPL-3.0-or-later
package nl.tufein.openconnect.core.watch.ble

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

fun hex(s: String): ByteArray = s.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

class FramingTest {
    // A DEVICE_INFORMATION message from a real Instinct 2S, as captured by Gadgetbridge (CobsCoDecTest), raw and on the air.
    private val instinct = hex("2C00A0139600310F684C1BCA840508020B496E7374696E637420325308496E7374696E6374023253000004B8")
    private val instinctCobs = hex("00022C04A0139623310F684C1BCA840508020B496E7374696E637420325308496E7374696E6374023253010304B800")

    @Test
    fun `cobs encoding matches what Gadgetbridge sends to watches`() {
        assertContentEquals(instinctCobs, Cobs.encode(instinct))
        // a message that ends in a zero gets Garmin's extra 0x01 before the closing zero
        val trailingZero = hex("022b058813a013029623ffffffffffffa71fffff046c61726a07756e6b6e6f776e0758512d4343373201f9cf00")
        val encoded = Cobs.encode(trailingZero)
        assertEquals("002d", encoded.copyOfRange(0, 2).toHex())
        assertEquals("0100", encoded.copyOfRange(encoded.size - 2, encoded.size).toHex())
    }

    @Test
    fun `a frame split over three bluetooth packets comes out whole`() {
        val stream = CobsStream()
        assertTrue(stream.feed(hex("00022C04A0139623310F684C1BCA840508020B")).isEmpty())
        assertTrue(stream.feed(hex("496E7374696E637420325308496E7374696E63")).isEmpty())
        val out = stream.feed(hex("74023253010304B800"))
        assertEquals(1, out.size)
        assertContentEquals(instinct, out.single())
    }

    @Test
    fun `random messages survive encoding, any split and back to back frames`() {
        val random = Random(7)
        repeat(200) {
            val messages = List(random.nextInt(1, 4)) {
                // plenty of zeros and long runs without one, the two hard cases for COBS
                ByteArray(random.nextInt(1, 700)) { i -> if (random.nextInt(5) == 0) 0 else (i % 251 + 1).toByte() }
            }
            val wire = messages.map { Cobs.encode(it) }.reduce { a, b -> a + b }
            val stream = CobsStream()
            val got = ArrayList<ByteArray>()
            var pos = 0
            while (pos < wire.size) {
                val end = minOf(wire.size, pos + random.nextInt(1, 40))
                got += stream.feed(wire.copyOfRange(pos, end))
                pos = end
            }
            assertEquals(messages.size, got.size)
            messages.zip(got).forEach { (a, b) -> assertContentEquals(a, b) }
        }
    }

    @Test
    fun `a lost packet costs one message, not the stream`() {
        val first = Cobs.encode(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
        val second = Cobs.encode(byteArrayOf(9, 0, 10))
        val stream = CobsStream()
        // the start of the first frame never arrived
        assertTrue(stream.feed(first.copyOfRange(4, first.size)).isEmpty())
        val out = stream.feed(second)
        assertEquals(listOf(byteArrayOf(9, 0, 10).toList()), out.map { it.toList() })
    }

    @Test
    fun `the checksum of a real watch message checks out`() {
        val body = instinct.copyOfRange(0, instinct.size - 2)
        val crc = (instinct[instinct.size - 1].toInt() and 0xFF shl 8) or (instinct[instinct.size - 2].toInt() and 0xFF)
        assertEquals(crc, GarminCrc.compute(body))
        // continuing a checksum over two pieces equals one checksum over the whole
        val a = body.copyOfRange(0, 10)
        val b = body.copyOfRange(10, body.size)
        assertEquals(crc, GarminCrc.compute(b, seed = GarminCrc.compute(a)))
    }
}
