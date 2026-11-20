package nl.tufein.openconnect.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CancellationTest {
    @Test
    fun aValueComesBackAsSuccess() {
        assertEquals(7, runCatchingUnlessCancelled { 7 }.getOrNull())
    }

    @Test
    fun anOrdinaryFailureBecomesAFailure() {
        val result = runCatchingUnlessCancelled<Int> { throw IOException("down") }
        assertIs<IOException>(result.exceptionOrNull())
    }

    @Test
    fun aCancellationIsThrownOnNotSwallowed() {
        assertFailsWith<CancellationException> { runCatchingUnlessCancelled<Int> { throw CancellationException("stopped") } }
        // the plain runCatching this replaces would have turned it into a quiet failure
        assertIs<CancellationException>(runCatching<Int> { throw CancellationException("stopped") }.exceptionOrNull())
    }

    @Test
    fun aCancelledCoroutineStaysCancelled() = runBlocking {
        val started = CompletableDeferred<Unit>()
        var carriedOn = false
        val job = async {
            runCatchingUnlessCancelled {
                started.complete(Unit)
                awaitCancellation()
            }
            carriedOn = true // must not be reached: the cancellation went through
        }
        started.await()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertEquals(false, carriedOn)
    }
}
