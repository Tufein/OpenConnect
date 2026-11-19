// OpenConnect, SPDX-License-Identifier: AGPL-3.0-or-later
package nl.tufein.openconnect.notify

import android.app.Notification
import android.content.Context
import android.os.Bundle
import android.os.Process
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What Android's notification becomes before the rules look at it. Android's own ranking (importance, Do Not Disturb) needs a phone. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StatusBarMapperTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun sbn(
        pkg: String = "com.whatsapp",
        key: Boolean = true,
        flags: Int = 0,
        category: String? = null,
        whenMillis: Long = 0,
        postTime: Long = 1_700_000_000_000,
        extras: Bundle.() -> Unit = {},
    ): StatusBarNotification {
        val notification = Notification.Builder(context, "channel").setSmallIcon(android.R.drawable.ic_dialog_info).build()
        notification.flags = flags
        notification.category = category
        notification.`when` = whenMillis
        notification.extras.apply(extras)
        @Suppress("DEPRECATION")
        return StatusBarNotification(pkg, pkg, 7, "tag", 1000, 1, 0, notification, Process.myUserHandle(), postTime)
    }

    @Test
    fun `title, text, sub text and big text are read`() {
        val n = StatusBarMapper.map(
            sbn {
                putCharSequence(Notification.EXTRA_TITLE, "Jan")
                putCharSequence(Notification.EXTRA_TEXT, "Zie je morgen?")
                putCharSequence(Notification.EXTRA_BIG_TEXT, "Zie je morgen? Dan kom ik langs.")
                putCharSequence(Notification.EXTRA_SUB_TEXT, "Familie")
            },
            appName = "WhatsApp",
        )
        assertEquals("com.whatsapp", n.packageName)
        assertEquals("WhatsApp", n.appName)
        assertEquals("Jan", n.title)
        assertEquals("Zie je morgen?", n.text)
        assertEquals("Zie je morgen? Dan kom ik langs.", n.bigText)
        assertEquals("Familie", n.subText)
        assertEquals(sbn().key, n.key)
    }

    @Test
    fun `a chat that keeps its lines as messages gives the newest one with its sender`() {
        val older = Bundle().apply { putCharSequence("text", "Hoi"); putCharSequence("sender", "Jan") }
        val newest = Bundle().apply { putCharSequence("text", "Zie je morgen?"); putCharSequence("sender", "Jan") }
        val n = StatusBarMapper.map(sbn { putParcelableArray(Notification.EXTRA_MESSAGES, arrayOf(older, newest)) }, "WhatsApp")
        assertEquals("Jan: Zie je morgen?", n.text)
    }

    @Test
    fun `an inbox style notification gives its newest line`() {
        val n = StatusBarMapper.map(sbn { putCharSequenceArray(Notification.EXTRA_TEXT_LINES, arrayOf("Eerste", "Tweede")) }, "Mail")
        assertEquals("Tweede", n.text)
    }

    @Test
    fun `without any text the text is empty, not made up`() {
        val n = StatusBarMapper.map(sbn(), "WhatsApp")
        assertNull(n.title)
        assertNull(n.text)
    }

    @Test
    fun `the time the app gave wins over the time it was posted`() {
        assertEquals(1_600_000_000_000, StatusBarMapper.map(sbn(whenMillis = 1_600_000_000_000), "x").postTime)
        assertEquals(1_700_000_000_000, StatusBarMapper.map(sbn(whenMillis = 0), "x").postTime)
    }

    @Test
    fun `the flags that say stay on the phone, running or a stack are read`() {
        assertTrue(StatusBarMapper.map(sbn(flags = Notification.FLAG_ONGOING_EVENT), "x").ongoing)
        assertTrue(StatusBarMapper.map(sbn(flags = Notification.FLAG_FOREGROUND_SERVICE), "x").ongoing)
        assertTrue(StatusBarMapper.map(sbn(flags = Notification.FLAG_GROUP_SUMMARY), "x").groupSummary)
        assertTrue(StatusBarMapper.map(sbn(flags = Notification.FLAG_LOCAL_ONLY), "x").localOnly)
        val plain = StatusBarMapper.map(sbn(), "x")
        assertFalse(plain.ongoing || plain.groupSummary || plain.localOnly)
    }

    @Test
    fun `the category and whether the person can swipe it away come along`() {
        val n = StatusBarMapper.map(sbn(category = Notification.CATEGORY_MESSAGE), "x")
        assertEquals("msg", n.category)
        assertTrue(n.clearable)
        assertFalse(StatusBarMapper.map(sbn(flags = Notification.FLAG_NO_CLEAR or Notification.FLAG_ONGOING_EVENT), "x").clearable)
    }

    @Test
    fun `without a ranking nothing is called quiet or silenced`() {
        val n = StatusBarMapper.map(sbn(), "x", ranking = null)
        assertFalse(n.lowImportance)
        assertFalse(n.silencedByDoNotDisturb)
    }
}
