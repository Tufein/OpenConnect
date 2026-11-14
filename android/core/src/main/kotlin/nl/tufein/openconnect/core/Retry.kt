package nl.tufein.openconnect.core

import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * What a client does about a hiccup before it gives up: how long it waits before each new try. Clients (Garmin, Oura)
 * take one of these; a test passes a recording [pause] instead of really waiting, and the default [NONE] never retries,
 * so existing behaviour only changes where the app asks for [standard].
 */
class RetryPolicy(
    /** The wait before the first retry, the second, and so on. Empty means: never retry. */
    val delaysMs: List<Long> = emptyList(),
    /**
     * A server asking for a longer wait than this (`Retry-After`) is not waited for: the caller gets the answer and a
     * later run tries again. Blocking a background job for minutes helps nobody.
     */
    val maxWaitMs: Long = 30_000,
    val pause: (Long) -> Unit = { Thread.sleep(it) },
) {
    companion object {
        val NONE = RetryPolicy()

        /** Two retries, after 1.5 and 5 seconds: enough for a dropped connection or a busy server, not enough to stall a sync. */
        fun standard() = RetryPolicy(listOf(1_500L, 5_000L))

        /** Answers worth asking again: too many requests, and the server-side errors that usually pass. */
        fun isTransient(status: Int): Boolean = status == 429 || status == 500 || status in 502..504
    }

    /**
     * Runs [send] and, when it throws an [IOException] or its answer is [isTransient], tries again after the next delay
     * (or after the server's own `Retry-After` when that is longer). The last answer is returned, or the last exception
     * thrown, once the delays are used up. 4xx answers other than 429 are never repeated: asking again changes nothing.
     */
    fun <R> call(now: () -> Instant, send: () -> R, status: (R) -> Int, headers: (R) -> Map<String, String>): R {
        var retries = 0
        while (true) {
            val answer = try {
                send()
            } catch (e: IOException) {
                if (retries >= delaysMs.size) throw e
                pause(delaysMs[retries++])
                continue
            }
            if (!isTransient(status(answer)) || retries >= delaysMs.size) return answer
            val asked = RetryAfter.seconds(headers(answer)["retry-after"], now())?.times(1000)
            if (asked != null && asked > maxWaitMs) return answer
            pause(maxOf(asked ?: 0L, delaysMs[retries++]))
        }
    }
}

/** The `Retry-After` header of a 429 or 503: a number of seconds, or the moment to try again as an HTTP date. */
object RetryAfter {
    fun seconds(value: String?, now: Instant): Long? {
        val text = value?.trim().orEmpty()
        if (text.isEmpty()) return null
        text.toLongOrNull()?.let { return it.coerceAtLeast(0) }
        return try {
            Duration.between(now, ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).seconds.coerceAtLeast(0)
        } catch (_: Exception) {
            null
        }
    }

    /** How long automatic syncs stay quiet after a rate limit: what the server asked for, else half an hour, never under a minute or over six hours. */
    fun coolDown(retryAfterS: Long?): Duration = Duration.ofSeconds((retryAfterS ?: 30 * 60L).coerceIn(60L, 6 * 3600L))
}
