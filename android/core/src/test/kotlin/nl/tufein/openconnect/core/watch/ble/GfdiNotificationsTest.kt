// OpenConnect, SPDX-License-Identifier: AGPL-3.0-or-later
package nl.tufein.openconnect.core.watch.ble

import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The bytes of the notification messages. The expected layouts are written out by hand from Gadgetbridge's
 * NotificationUpdateMessage, NotificationDataMessage, NotificationControlMessage and the status messages, so a
 * mistake in the code cannot hide behind a test that uses the same code to build its answer.
 */
class GfdiNotificationsTest {
    private fun payloadHex(m: GfdiMessage) = m.payload.toHex()

    // ------------------------------------------------------------------------------ phone to watch

    @Test
    fun `an update says what changed, in which category, how many, which id and whether there are actions`() {
        val add = GfdiNotifications.update(NotificationUpdate.ADD, WatchNotificationCategory.SMS, count = 1, id = 0x01020304, hasActions = true)
        assertEquals(GfdiType.NOTIFICATION_UPDATE, add.type)
        // type ADD (0), flags FOREGROUND|DECLINE (0x12), category SMS (12 = 0x0c), count 1, id little endian, phone flags NEW_ACTIONS (2)
        assertEquals("00" + "12" + "0c" + "01" + "04030201" + "02", payloadHex(add))
    }

    @Test
    fun `modify and remove differ only in their first byte, and no actions means no flag`() {
        val modify = GfdiNotifications.update(NotificationUpdate.MODIFY, WatchNotificationCategory.EMAIL, 3, 7, hasActions = false)
        assertEquals("01" + "12" + "06" + "03" + "07000000" + "00", payloadHex(modify))
        val remove = GfdiNotifications.update(NotificationUpdate.REMOVE, WatchNotificationCategory.OTHER, 0, -1, hasActions = false)
        assertEquals("02" + "12" + "00" + "00" + "ffffffff" + "00", payloadHex(remove))
    }

    @Test
    fun `the categories keep Gadgetbridge's numbers`() {
        assertEquals(
            listOf(
                "OTHER", "INCOMING_CALL", "MISSED_CALL", "VOICEMAIL", "SOCIAL", "SCHEDULE", "EMAIL", "NEWS",
                "HEALTH_AND_FITNESS", "BUSINESS_AND_FINANCE", "LOCATION", "ENTERTAINMENT", "SMS",
            ),
            WatchNotificationCategory.entries.map { it.name },
        )
    }

    @Test
    fun `a message cut into chunks carries its total, the running checksum and where the chunk starts`() {
        val data = GfdiNotifications.data(total = 700, crc = 0xABCD, offset = 300, chunk = byteArrayOf(1, 2, 3))
        assertEquals(GfdiType.NOTIFICATION_DATA, data.type)
        assertEquals("bc02" + "cdab" + "2c01" + "010203", payloadHex(data))
    }

    // ------------------------------------------------------------------------------ the phone answers

    @Test
    fun `the answer to a subscription says whether notifications are on`() {
        val on = GfdiNotifications.subscriptionReply(requested = true, allowed = true)
        // RESPONSE to 5036 (0x13ac), ACK, ENABLED, the request echoed, 0
        assertEquals(GfdiType.RESPONSE, on.type)
        assertEquals("ac13" + "00" + "00" + "01" + "00", payloadHex(on))
        val off = GfdiNotifications.subscriptionReply(requested = true, allowed = false)
        assertEquals("ac13" + "00" + "01" + "01" + "00", payloadHex(off))
        val cancel = GfdiNotifications.subscriptionReply(requested = false, allowed = true)
        assertEquals("ac13" + "00" + "00" + "00" + "00", payloadHex(cancel))
    }

    @Test
    fun `a control message is acknowledged, in the failure case with Garmin's unknown-command code`() {
        assertEquals("aa13" + "00" + "00" + "00", payloadHex(GfdiNotifications.controlAck()))
        assertEquals("aa13" + "00" + "01" + "a0", payloadHex(GfdiNotifications.controlAck(ok = false)))
    }

    // ------------------------------------------------------------------------------ watch to phone

    private fun control(vararg bytes: Int) = GfdiMessage(GfdiType.NOTIFICATION_CONTROL, ByteArray(bytes.size) { bytes[it].toByte() })

    @Test
    fun `a request for attributes lists them in order, each with the length the watch can show`() {
        val request = GfdiNotifications.parseControl(
            control(
                0x00, 0x2A, 0x00, 0x00, 0x00, // GET_NOTIFICATION_ATTRIBUTES for notification 42
                0x01, 0x40, 0x00, // TITLE, at most 64
                0x03, 0xF4, 0x01, // MESSAGE, at most 500
                0x04, // MESSAGE_SIZE
                0x05, // DATE
                0x7F, 0x20, 0x00, 0x01, // ACTIONS, at most 32, plus the extra byte Garmin adds
            ),
        )
        assertIs<NotificationControl.GetAttributes>(request)
        assertEquals(42, request.id)
        assertEquals(
            listOf(
                AttributeRequest(NotificationAttribute.TITLE, 64),
                AttributeRequest(NotificationAttribute.MESSAGE, 500),
                AttributeRequest(NotificationAttribute.MESSAGE_SIZE, 0),
                AttributeRequest(NotificationAttribute.DATE, 0),
                AttributeRequest(NotificationAttribute.ACTIONS, 32),
            ),
            request.attributes,
        )
    }

    @Test
    fun `an attribute nobody knows makes the request unreadable, because its length cannot be known`() {
        val request = GfdiNotifications.parseControl(control(0x00, 1, 0, 0, 0, 0x63, 0x01))
        assertIs<NotificationControl.Malformed>(request)
    }

    @Test
    fun `an action on a notification names the action and keeps the text the watch typed`() {
        val dismiss = GfdiNotifications.parseControl(control(0x80, 9, 0, 0, 0, 98))
        assertEquals(NotificationControl.PerformAction(9, 98, null), dismiss)
        val reply = GfdiNotifications.parseControl(control(0x80, 9, 0, 0, 0, 95, 'o'.code, 'k'.code, 0))
        assertEquals(NotificationControl.PerformAction(9, 95, "ok"), reply)
    }

    @Test
    fun `a request for an app's name carries the package, ended by a zero`() {
        val request = GfdiNotifications.parseControl(control(0x01, 'a'.code, '.'.code, 'b'.code, 0, 0x00))
        assertEquals(NotificationControl.GetAppAttributes("a.b", listOf(0)), request)
        // an app attribute other than the name is not one we can answer
        assertIs<NotificationControl.Malformed>(GfdiNotifications.parseControl(control(0x01, 'a'.code, 0, 0x07)))
    }

    @Test
    fun `the old accept-or-refuse action and unknown commands are told apart`() {
        assertEquals(NotificationControl.LegacyAction(5, accept = false), GfdiNotifications.parseControl(control(0x02, 5, 0, 0, 0, 1)))
        assertIs<NotificationControl.Malformed>(GfdiNotifications.parseControl(control(0x55, 1, 2, 3)))
        assertIs<NotificationControl.Malformed>(GfdiNotifications.parseControl(control()))
        assertIs<NotificationControl.Malformed>(GfdiNotifications.parseControl(control(0x00, 1, 2)))
    }

    @Test
    fun `a subscription asks to switch notifications on or off`() {
        assertTrue(GfdiNotifications.parseSubscription(GfdiMessage(GfdiType.NOTIFICATION_SUBSCRIPTION, byteArrayOf(1))))
        assertTrue(GfdiNotifications.parseSubscription(GfdiMessage(GfdiType.NOTIFICATION_SUBSCRIPTION, byteArrayOf(1, 0))))
        assertEquals(false, GfdiNotifications.parseSubscription(GfdiMessage(GfdiType.NOTIFICATION_SUBSCRIPTION, byteArrayOf(0, 0))))
        assertFailsWith<GfdiFormatException> { GfdiNotifications.parseSubscription(GfdiMessage(GfdiType.NOTIFICATION_SUBSCRIPTION, byteArrayOf())) }
    }

    // ------------------------------------------------------------------------------ attribute values

    private val notification = WatchNotification(
        id = 42, appId = "com.whatsapp", appName = "WhatsApp", title = "Jan", body = "Zie je morgen?",
        whenMillis = Instant.parse("2026-11-19T13:30:05Z").toEpochMilli(), category = WatchNotificationCategory.SMS,
        subtitle = "gezin",
    )
    private val amsterdam = ZoneId.of("Europe/Amsterdam")

    private fun value(attr: NotificationAttribute, max: Int = 0, n: WatchNotification = notification) =
        NotificationValues.of(attr, max, n, amsterdam, dismissLabel = "Dismiss")

    @Test
    fun `attributes take their text from the notification`() {
        assertEquals("Jan", String(value(NotificationAttribute.TITLE)))
        assertEquals("gezin", String(value(NotificationAttribute.SUBTITLE)))
        assertEquals("Zie je morgen?", String(value(NotificationAttribute.MESSAGE)))
        assertEquals("com.whatsapp", String(value(NotificationAttribute.APP_IDENTIFIER)))
        assertEquals("14", String(value(NotificationAttribute.MESSAGE_SIZE)), "the length of the whole text, as a number in text")
        assertEquals("", String(value(NotificationAttribute.NEGATIVE_ACTION_LABEL)))
        assertEquals("1", String(value(NotificationAttribute.ATTACHMENTS)), "what Gadgetbridge answers, whatever the notification holds")
    }

    @Test
    fun `the date is local time as yyyyMMddTHHmmss`() {
        // 13:30:05 UTC is 14:30:05 in Amsterdam in November
        assertEquals("20261119T143005", String(value(NotificationAttribute.DATE)))
    }

    @Test
    fun `a text longer than the watch can show is cut, and never in the middle of an emoji`() {
        assertEquals("Zie je", String(value(NotificationAttribute.MESSAGE, max = 6)))
        // U+1F600 is two UTF-16 characters; cutting at 2 would leave half of it
        val emoji = notification.copy(body = "a😀b")
        assertEquals("a", String(value(NotificationAttribute.MESSAGE, max = 2, n = emoji)))
        assertEquals("a😀", String(value(NotificationAttribute.MESSAGE, max = 3, n = emoji)))
        assertEquals("14", String(value(NotificationAttribute.MESSAGE_SIZE, max = 6)), "the size is the whole text, whatever was cut")
    }

    @Test
    fun `a notification that can be dismissed offers one action, laid out as Garmin lays it out`() {
        // count 1, DISMISS_NOTIFICATION (98), icon position LEFT (bit 2 = 4), length 7, "Dismiss"
        assertEquals("01" + "62" + "04" + "07" + "Dismiss".toByteArray().toHex(), value(NotificationAttribute.ACTIONS).toHex())
    }

    @Test
    fun `a notification without actions says so with four zeros`() {
        assertEquals("00000000", value(NotificationAttribute.ACTIONS, n = notification.copy(canDismiss = false)).toHex())
    }

    @Test
    fun `an action label with accents is counted in bytes`() {
        val label = NotificationValues.of(NotificationAttribute.ACTIONS, 0, notification, amsterdam, dismissLabel = "Verwijder é")
        // "Verwijder " is 10 bytes and "é" is two bytes in UTF-8, so the label is 12 bytes long
        assertEquals(12, label[3].toInt())
        assertEquals(4 + 12, label.size)
    }

    // ------------------------------------------------------------------------------ chunks

    @Test
    fun `a long answer is cut into chunks of at most 300 bytes with a running checksum`() {
        val data = ByteArray(700) { (it * 7).toByte() }
        val upload = NotificationUpload(data)
        val chunks = ArrayList<GfdiMessage>()
        while (!upload.finished) chunks += upload.next()
        assertEquals(3, chunks.size)
        var crc = 0
        var offset = 0
        for (m in chunks) {
            assertEquals(GfdiType.NOTIFICATION_DATA, m.type)
            val r = m.reader()
            assertEquals(700, r.short())
            val got = r.short()
            assertEquals(offset, r.short())
            val chunk = r.rest()
            crc = GarminCrc.compute(chunk, seed = crc)
            assertEquals(crc, got, "the checksum runs on from the chunk before")
            assertContentEquals(data.copyOfRange(offset, offset + chunk.size), chunk)
            offset += chunk.size
        }
        assertEquals(700, offset)
        assertEquals(listOf(300, 300, 100), chunks.map { it.payload.size - 6 })
    }

    @Test
    fun `an answer that fits in one chunk is one message, and an empty one is still a message`() {
        val one = NotificationUpload(ByteArray(10))
        assertEquals(10, one.next().payload.size - 6)
        assertTrue(one.finished)
        val none = NotificationUpload(ByteArray(0))
        assertEquals(0, none.next().payload.size - 6)
        assertTrue(none.finished)
    }
}
