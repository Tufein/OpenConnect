package nl.tufein.openconnect.core

import java.io.IOException
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class RetryTest {
    private val now = Instant.parse("2026-11-14T12:00:00Z")

    private class Answer(val status: Int, val headers: Map<String, String> = emptyMap())

    private val waits = ArrayList<Long>()
    private val policy = RetryPolicy(listOf(1_500L, 5_000L), maxWaitMs = 30_000) { waits += it }

    /** Plays [script] in order: an [Answer] is returned, an [IOException] is thrown. Counts the attempts. */
    private fun play(p: RetryPolicy, vararg script: Any): Pair<Answer, Int> {
        var attempts = 0
        val answer = p.call(
            now = { now },
            send = {
                val next = script[minOf(attempts++, script.size - 1)]
                if (next is IOException) throw next
                next as Answer
            },
            status = { it.status },
            headers = { it.headers },
        )
        return answer to attempts
    }

    @Test
    fun `Retry-After is read as seconds or as an HTTP date, and nonsense is ignored`() {
        assertEquals(120, RetryAfter.seconds("120", now))
        assertEquals(7, RetryAfter.seconds(" 7 ", now))
        assertEquals(0, RetryAfter.seconds("-5", now))
        assertEquals(90, RetryAfter.seconds("Sat, 14 Nov 2026 12:01:30 GMT", now))
        assertEquals(0, RetryAfter.seconds("Sat, 14 Nov 2026 11:00:00 GMT", now), "a date in the past means: now")
        assertNull(RetryAfter.seconds("soon", now))
        assertNull(RetryAfter.seconds("", now))
        assertNull(RetryAfter.seconds(null, now))
    }

    @Test
    fun `the cool-down after a rate limit follows the server, with sane bounds`() {
        assertEquals(Duration.ofMinutes(30), RetryAfter.coolDown(null))
        assertEquals(Duration.ofMinutes(10), RetryAfter.coolDown(600))
        assertEquals(Duration.ofMinutes(1), RetryAfter.coolDown(0))
        assertEquals(Duration.ofHours(6), RetryAfter.coolDown(10 * 24 * 3600L))
    }

    @Test
    fun `a busy server is asked again after the first delay and the good answer is returned`() {
        val (answer, attempts) = play(policy, Answer(503), Answer(200))
        assertEquals(200, answer.status)
        assertEquals(2, attempts)
        assertEquals(listOf(1_500L), waits)
    }

    @Test
    fun `the delays grow and the last answer comes back when they are used up`() {
        val (answer, attempts) = play(policy, Answer(503), Answer(502), Answer(500))
        assertEquals(500, answer.status)
        assertEquals(3, attempts)
        assertEquals(listOf(1_500L, 5_000L), waits)
    }

    @Test
    fun `a dropped connection is retried, and thrown once the delays are used up`() {
        val (answer, attempts) = play(policy, IOException("reset"), Answer(200))
        assertEquals(200, answer.status)
        assertEquals(2, attempts)

        waits.clear()
        val failure = assertFailsWith<IOException> { play(policy, IOException("down")) }
        assertEquals("down", failure.message)
        assertEquals(listOf(1_500L, 5_000L), waits)
    }

    @Test
    fun `a rate limit waits as long as the server asks when that is longer than the delay`() {
        val (answer, _) = play(policy, Answer(429, mapOf("retry-after" to "4")), Answer(200))
        assertEquals(200, answer.status)
        assertEquals(listOf(4_000L), waits)

        waits.clear()
        play(policy, Answer(429, mapOf("retry-after" to "0")), Answer(200))
        assertEquals(listOf(1_500L), waits, "never faster than our own delay")
    }

    @Test
    fun `a wait longer than the limit is not waited for`() {
        val (answer, attempts) = play(policy, Answer(429, mapOf("retry-after" to "120")), Answer(200))
        assertEquals(429, answer.status)
        assertEquals(1, attempts)
        assertEquals(emptyList(), waits)
    }

    @Test
    fun `answers that asking again cannot change are returned at once`() {
        for (status in listOf(200, 204, 400, 401, 403, 404)) {
            waits.clear()
            val (answer, attempts) = play(policy, Answer(status), Answer(200))
            assertEquals(status, answer.status)
            assertEquals(1, attempts)
            assertEquals(emptyList(), waits)
        }
    }

    @Test
    fun `without a policy nothing is repeated`() {
        val (answer, attempts) = play(RetryPolicy.NONE, Answer(503), Answer(200))
        assertEquals(503, answer.status)
        assertEquals(1, attempts)
        assertFailsWith<IOException> { play(RetryPolicy.NONE, IOException("down")) }
    }
}
