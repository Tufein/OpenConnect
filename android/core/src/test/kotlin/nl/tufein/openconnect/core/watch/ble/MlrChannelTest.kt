// OpenConnect, SPDX-License-Identifier: AGPL-3.0-or-later
package nl.tufein.openconnect.core.watch.ble

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A clock moved by hand: tasks run when [advance] passes their time. */
class ManualScheduler : Scheduler {
    private class Job(val at: Long, val task: () -> Unit) {
        var cancelled = false
    }

    var now = 0L
        private set
    private val jobs = ArrayList<Job>()

    override fun schedule(delayMs: Long, task: () -> Unit): Scheduler.Cancellable {
        val job = Job(now + delayMs, task)
        jobs += job
        return Scheduler.Cancellable { job.cancelled = true }
    }

    fun advance(ms: Long) {
        val until = now + ms
        while (true) {
            val next = jobs.filter { !it.cancelled && it.at <= until }.minByOrNull { it.at } ?: break
            jobs.remove(next)
            now = next.at
            next.task()
        }
        now = until
    }
}

class MlrChannelTest {
    private val scheduler = ManualScheduler()
    private val sent = ArrayList<ByteArray>()
    private val received = ArrayList<ByteArray>()

    private fun channel(handle: Int = 0x81, max: Int = 20) =
        MlrChannel(handle, max, { sent += it }, { received += it }, scheduler)

    private fun ack(p: ByteArray) = ((p[0].toInt() and 0x0F) shl 2) or ((p[1].toInt() and 0xFF) shr 6)
    private fun seq(p: ByteArray) = p[1].toInt() and 0x3F

    @Test
    fun `the header packs handle, acknowledgement and sequence`() {
        assertEquals("80" + "00", MlrChannel.header(0, 0, 0).toHex())
        // handle 1, ack 45 (10 1101), seq 9: 1 001 1011  01 001001
        assertEquals("9b" + "49", MlrChannel.header(1, 45, 9).toHex())
        assertEquals(1, MlrChannel.handleOf(MlrChannel.header(0x81, 3, 4)))
    }

    @Test
    fun `a long message is cut into numbered packets that fit one write`() {
        val c = channel(max = 20)
        c.send(ByteArray(50) { it.toByte() })
        assertEquals(listOf(20, 20, 16), sent.map { it.size })
        assertEquals(listOf(0, 1, 2), sent.map { seq(it) })
        assertContentEquals(ByteArray(50) { it.toByte() }, sent.map { it.copyOfRange(2, it.size) }.reduce { a, b -> a + b })
    }

    @Test
    fun `packets in order are delivered and acknowledged shortly after`() {
        val c = channel()
        c.onPacket(MlrChannel.header(1, 0, 0) + byteArrayOf(1, 2))
        c.onPacket(MlrChannel.header(1, 0, 1) + byteArrayOf(3))
        assertEquals(listOf(listOf<Byte>(1, 2), listOf<Byte>(3)), received.map { it.toList() })
        assertTrue(sent.isEmpty(), "the acknowledgement waits a moment to cover more packets")
        scheduler.advance(25)
        assertEquals(1, sent.size)
        assertEquals(2, sent.single().size, "an acknowledgement has no data")
        assertEquals(2, ack(sent.single()))
    }

    @Test
    fun `five unacknowledged packets are acknowledged at once`() {
        val c = channel()
        repeat(5) { c.onPacket(MlrChannel.header(1, 0, it) + byteArrayOf(it.toByte())) }
        assertEquals(1, sent.size)
        assertEquals(5, ack(sent.single()))
    }

    @Test
    fun `a packet out of order is dropped and the expected number is repeated`() {
        val c = channel()
        c.onPacket(MlrChannel.header(1, 0, 1) + byteArrayOf(9))
        assertTrue(received.isEmpty())
        assertEquals(0, ack(sent.single()))
    }

    @Test
    fun `packets for another handle are ignored`() {
        val c = channel(handle = 0x81)
        c.onPacket(MlrChannel.header(2, 0, 0) + byteArrayOf(9))
        assertTrue(received.isEmpty())
    }

    @Test
    fun `what is not acknowledged is sent again, slower each time`() {
        val c = channel()
        c.send(byteArrayOf(1, 2, 3))
        assertEquals(1, sent.size)
        scheduler.advance(999)
        assertEquals(1, sent.size)
        scheduler.advance(1)
        assertEquals(2, sent.size)
        assertContentEquals(sent[0], sent[1])
        scheduler.advance(1999)
        assertEquals(2, sent.size)
        scheduler.advance(1)
        assertEquals(3, sent.size)
        // the watch acknowledges: no more resends
        c.onPacket(MlrChannel.header(1, 1, 0))
        scheduler.advance(60_000)
        assertEquals(3, sent.size)
    }

    @Test
    fun `no more than 32 packets are in flight`() {
        val c = channel(max = 3)
        c.send(ByteArray(40) { 7 })
        assertEquals(32, sent.size)
        // acknowledging ten lets ten more go
        c.onPacket(MlrChannel.header(1, 10, 0))
        assertEquals(40, sent.size)
    }

    @Test
    fun `nothing is sent after close`() {
        val c = channel()
        c.close()
        c.send(byteArrayOf(1))
        scheduler.advance(10_000)
        assertTrue(sent.isEmpty())
    }
}
