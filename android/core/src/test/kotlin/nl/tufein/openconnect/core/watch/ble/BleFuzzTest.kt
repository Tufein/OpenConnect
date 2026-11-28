// OpenConnect, SPDX-License-Identifier: AGPL-3.0-or-later
package nl.tufein.openconnect.core.watch.ble

import nl.tufein.openconnect.core.watch.HeartRateMeasurement
import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Everything a watch sends arrives on Android's Bluetooth thread, and an exception there takes the whole app down. A
 * watch with new firmware, a half-received packet or a second app on the same watch can send anything, so the readers
 * must answer every byte sequence with a result, null, or their own [GfdiFormatException]; and a live session must
 * survive any packet without throwing out of [GarminBleSession.onNotification]. Fixed seeds, so a failure repeats.
 */
class BleFuzzTest {
    private val scheduler = ExecutorScheduler()
    private val watches = ArrayList<FakeGarminWatch>()
    private val problems = LinkedHashMap<String, String>()

    @AfterTest
    fun stop() {
        watches.forEach { it.shutdown() }
        scheduler.shutdown()
    }

    private inline fun probe(label: String, allowed: (Throwable) -> Boolean = { false }, block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            if (!allowed(e)) {
                val at = e.stackTrace.firstOrNull { it.className.startsWith("nl.tufein") && "Fuzz" !in it.className }
                problems.putIfAbsent("$label ${e::class.simpleName} $at", "$label: ${e::class.qualifiedName}: ${e.message} at $at")
            }
        }
    }

    private fun assertClean() = assertTrue(problems.isEmpty(), "${problems.size} kinds of unexpected exceptions:\n" + problems.values.joinToString("\n"))

    private val types: List<Int> = GfdiType.KNOWN.toList() + listOf(0, 1, 4999, 5067, 5100, 0x7FFF, 0x8000 or 24, 0xFFFF)

    private fun randomPayload(rnd: Random): ByteArray = rnd.nextBytes(
        when (rnd.nextInt(4)) {
            0 -> 0
            1 -> rnd.nextInt(1, 8)
            2 -> rnd.nextInt(8, 40)
            else -> rnd.nextInt(40, 400)
        },
    )

    @Test
    fun `the message readers refuse rubbish with their own error`() {
        val rnd = Random(11)
        val own = { e: Throwable -> e is GfdiFormatException }
        repeat(20_000) {
            val raw = randomPayload(rnd)
            probe("GfdiMessage.parse", own) { GfdiMessage.parse(raw) }
            probe("Cobs.decodeBody") { Cobs.decodeBody(raw) }
            probe("MultiLink.parseManagement") { MultiLink.parseManagement(byteArrayOf(0) + raw) }
            probe("HeartRateMeasurement.parse") { HeartRateMeasurement.parse(raw) }
            probe("GarminProtobuf.batteryLevel") { GarminProtobuf.batteryLevel(raw) }
            probe("GarminProtobuf.replyTo") { GarminProtobuf.replyTo(raw) }
            probe("GarminProtobuf.describe") { GarminProtobuf.describe(raw) }
            probe("GarminDirectoryEntry.parseDirectory", own) { GarminDirectoryEntry.parseDirectory(raw.copyOf(raw.size / 16 * 16)) }
            val message = GfdiMessage(types[rnd.nextInt(types.size)], raw)
            probe("GarminDeviceInfo.parse", own) { GarminDeviceInfo.parse(message).let { it.softwareVersionText; it.nameOr("x"); it.recognised } }
            probe("GarminProtobuf.parse", own) { GarminProtobuf.parse(message).last }
            probe("GfdiNotifications.parseControl", own) { GfdiNotifications.parseControl(message) }
            probe("GfdiNotifications.parseSubscription", own) { GfdiNotifications.parseSubscription(message) }
            probe("GfdiMessage round trip", own) { GfdiMessage.parse(message.encode()) }
        }
        val stream = CobsStream(maxBuffered = 4096)
        repeat(5_000) { probe("CobsStream.feed") { stream.feed(randomPayload(rnd)) } }
        assertClean()
    }

    private fun session(notifications: Boolean): GarminBleSession {
        val watch = FakeGarminWatch(files = emptyMap(), reliable = false, notifications = notifications)
        watches += watch
        val session = GarminBleSession(
            watch.pipe, PhoneIdentity("phone", "Google", "Pixel"), scheduler, maxWrite = 20, useMlr = false,
            now = { Instant.parse("2026-11-28T09:00:00Z") }, zone = { ZoneId.of("Europe/Brussels") },
            notifications = if (notifications) NotificationOptions() else null, heartRate = true,
        )
        watch.phone = session
        session.start()
        session.awaitReady(5_000)
        watch.drain()
        return session
    }

    private fun fuzzSession(session: GarminBleSession, rnd: Random, label: String) {
        // The fake watch runs without MLR, so GFDI travels on plain handle 1 (see FakeGarminWatch).
        repeat(4_000) {
            val packet = when (rnd.nextInt(5)) {
                // a well-formed GFDI message of any type with a random payload: reaches every handler
                0, 1 -> byteArrayOf(1) + Cobs.encode(GfdiMessage(types[rnd.nextInt(types.size)], randomPayload(rnd)).encode())
                // a management packet for our client id, of any type
                2 -> byteArrayOf(0, rnd.nextInt(256).toByte()) + java.nio.ByteBuffer.allocate(8).order(java.nio.ByteOrder.LITTLE_ENDIAN).putLong(MultiLink.CLIENT_ID).array() + randomPayload(rnd).take(6)
                // rubbish on the GFDI handle or any other handle
                3 -> byteArrayOf(1) + randomPayload(rnd)
                else -> byteArrayOf(rnd.nextInt(256).toByte()) + randomPayload(rnd)
            }
            probe(label) { session.onNotification(packet) }
        }
    }

    @Test
    fun `a sync session survives any packet the watch could send`() {
        fuzzSession(session(notifications = false), Random(12), "sync session")
        assertClean()
    }

    @Test
    fun `a notification session survives any packet the watch could send`() {
        fuzzSession(session(notifications = true), Random(13), "notification session")
        assertClean()
    }
}

