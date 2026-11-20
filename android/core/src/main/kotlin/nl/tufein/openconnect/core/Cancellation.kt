package nl.tufein.openconnect.core

import kotlin.coroutines.cancellation.CancellationException

/**
 * [runCatching] for code that runs inside a coroutine: a failure becomes a [Result], but the coroutine's own
 * cancellation is thrown on, because swallowing it leaves a cancelled job carrying on as if nothing happened (a stopped
 * background job that goes on to write a result nobody waits for, a screen that was left still reporting an error).
 *
 * Plain `runCatching` and `catch (e: Exception)` both catch the cancellation, since it is an ordinary exception.
 */
inline fun <T> runCatchingUnlessCancelled(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e)
}
