// OpenConnect, SPDX-License-Identifier: AGPL-3.0-or-later
package nl.tufein.openconnect.core.watch

import nl.tufein.openconnect.core.watch.ble.WatchNotificationCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Which phone notifications go to the watch, and what the watch is shown. */
class NotificationRulesTest {
    private val chosen = ForwardSettings(enabled = true, apps = setOf("com.whatsapp", "com.google.android.gm"), hiddenBody = "New notification")

    private fun message(
        title: String? = "Jan",
        text: String? = "Zie je morgen?",
        pkg: String = "com.whatsapp",
        category: String? = "msg",
        build: PhoneNotification.() -> PhoneNotification = { this },
    ) = PhoneNotification(
        key = "0|$pkg|1|null|10001", packageName = pkg, appName = "WhatsApp", title = title, text = text,
        postTime = 1_700_000_000_000, category = category,
    ).build()

    private fun decide(n: PhoneNotification, settings: ForwardSettings = chosen, screenOn: Boolean = false) =
        NotificationRules.decide(n, settings, screenOn, ownPackage = "nl.tufein.openconnect")

    private fun sent(n: PhoneNotification, settings: ForwardSettings = chosen) =
        assertIs<ForwardDecision.Send>(decide(n, settings)).notification

    private fun skipped(n: PhoneNotification, settings: ForwardSettings = chosen, screenOn: Boolean = false) =
        assertIs<ForwardDecision.Skip>(decide(n, settings, screenOn)).reason

    @Test
    fun `a message from a chosen app goes to the watch with its title, text, app and time`() {
        val n = sent(message())
        assertEquals("Jan", n.title)
        assertEquals("Zie je morgen?", n.body)
        assertEquals("WhatsApp", n.appName)
        assertEquals("com.whatsapp", n.appId)
        assertEquals(1_700_000_000_000, n.whenMillis)
        assertEquals(WatchNotificationCategory.SMS, n.category)
        assertEquals(NotificationRules.idOf(message().key), n.id)
    }

    @Test
    fun `nothing is sent while the switch is off or for an app that was not chosen`() {
        assertEquals(SkipReason.OFF, skipped(message(), chosen.copy(enabled = false)))
        assertEquals(SkipReason.APP_NOT_CHOSEN, skipped(message(pkg = "com.example.game")))
        assertEquals(SkipReason.APP_NOT_CHOSEN, skipped(message(), chosen.copy(apps = emptySet())))
    }

    @Test
    fun `the app's own notifications never go to the watch, even when it is on the list`() {
        val own = message(pkg = "nl.tufein.openconnect")
        assertEquals(SkipReason.OWN_APP, skipped(own, chosen.copy(apps = chosen.apps + "nl.tufein.openconnect")))
    }

    @Test
    fun `things that are not messages are skipped, each for its own reason`() {
        assertEquals(SkipReason.ONGOING, skipped(message { copy(ongoing = true) }))
        assertEquals(SkipReason.GROUP_SUMMARY, skipped(message { copy(groupSummary = true) }))
        assertEquals(SkipReason.LOCAL_ONLY, skipped(message { copy(localOnly = true) }))
        assertEquals(SkipReason.LOW_IMPORTANCE, skipped(message { copy(lowImportance = true) }))
        assertEquals(SkipReason.DO_NOT_DISTURB, skipped(message { copy(silencedByDoNotDisturb = true) }))
        assertEquals(SkipReason.CALL, skipped(message(category = "call")))
    }

    @Test
    fun `a notification without any text is skipped`() {
        assertEquals(SkipReason.EMPTY, skipped(message(title = null, text = null)))
        assertEquals(SkipReason.EMPTY, skipped(message(title = "  ", text = "\n")))
    }

    @Test
    fun `with the screen on, the option to wait for a dark screen holds the notification back`() {
        val dark = chosen.copy(onlyWhenScreenOff = true)
        assertEquals(SkipReason.SCREEN_ON, skipped(message(), dark, screenOn = true))
        assertIs<ForwardDecision.Send>(decide(message(), dark, screenOn = false))
        assertIs<ForwardDecision.Send>(decide(message(), chosen, screenOn = true))
    }

    @Test
    fun `the longer of text and big text is the body`() {
        assertEquals("Hallo, dit is het lange verhaal", sent(message(text = "Hallo", build = { copy(bigText = "Hallo, dit is het lange verhaal") })).body)
        assertEquals("Hallo", sent(message(text = "Hallo", build = { copy(bigText = "Hi") })).body)
        assertEquals("Alleen groot", sent(message(text = null, build = { copy(bigText = "Alleen groot") })).body)
    }

    @Test
    fun `a notification with a body but no title takes the app's name as title`() {
        val n = sent(message(title = null))
        assertEquals("WhatsApp", n.title)
    }

    @Test
    fun `the sub text goes along as subtitle`() {
        assertEquals("gezin", sent(message { copy(subText = "gezin") }).subtitle)
    }

    @Test
    fun `hiding the content leaves only the app's name and a fixed line`() {
        val n = sent(message { copy(subText = "gezin") }, chosen.copy(hideContent = true))
        assertEquals("WhatsApp", n.title)
        assertEquals("New notification", n.body)
        assertEquals("", n.subtitle)
    }

    @Test
    fun `text is tidied for a small screen and cut at a sane length`() {
        val n = sent(message(title = "  Jan\u0007 \n", text = "regel een\r\nregel twee\u0000\tklaar") )
        assertEquals("Jan", n.title)
        assertEquals("regel een\nregel twee klaar", n.body)
        val long = sent(message(title = "t".repeat(500), text = "b".repeat(5000)))
        assertEquals(100, long.title.length)
        assertEquals(1000, long.body.length)
    }

    @Test
    fun `cutting never splits an emoji`() {
        val body = "a".repeat(999) + "😀"
        val n = sent(message(text = body))
        assertEquals("a".repeat(999), n.body)
    }

    @Test
    fun `the category follows Android's category`() {
        fun categoryOf(c: String?) = sent(message(category = c)).category
        assertEquals(WatchNotificationCategory.EMAIL, categoryOf("email"))
        assertEquals(WatchNotificationCategory.SMS, categoryOf("msg"))
        assertEquals(WatchNotificationCategory.SOCIAL, categoryOf("social"))
        assertEquals(WatchNotificationCategory.SCHEDULE, categoryOf("event"))
        assertEquals(WatchNotificationCategory.SCHEDULE, categoryOf("reminder"))
        assertEquals(WatchNotificationCategory.SCHEDULE, categoryOf("alarm"))
        assertEquals(WatchNotificationCategory.LOCATION, categoryOf("navigation"))
        assertEquals(WatchNotificationCategory.OTHER, categoryOf("promo"))
        assertEquals(WatchNotificationCategory.OTHER, categoryOf(null))
    }

    @Test
    fun `only a notification the person can swipe away can be dismissed from the watch`() {
        assertTrue(sent(message()).canDismiss)
        assertFalse(sent(message { copy(clearable = false) }).canDismiss)
    }

    @Test
    fun `the id is the same for the same notification and different for another`() {
        val a = NotificationRules.idOf("0|com.whatsapp|1|null|10001")
        assertEquals(a, NotificationRules.idOf("0|com.whatsapp|1|null|10001"))
        assertNotEquals(a, NotificationRules.idOf("0|com.whatsapp|2|null|10001"))
    }

    @Test
    fun `the same words posted again for one notification are not sent twice, a change is`() {
        val filter = RepeatFilter()
        val first = sent(message())
        assertTrue(filter.isNew(first))
        assertFalse(filter.isNew(first), "an app that re-posts the same text")
        assertTrue(filter.isNew(sent(message(text = "Toch niet"))), "the text changed")
        filter.forget(first.id)
        assertTrue(filter.isNew(sent(message(text = "Toch niet"))), "after it was removed, the same words are new again")
    }
}
