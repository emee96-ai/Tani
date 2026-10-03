package com.tani.app.util

import kotlinx.coroutines.CancellationException

/** Cancellation belongs to the lifecycle, not the screen's error handler. */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (error: Throwable) {
    if (error is CancellationException) throw error
    Result.failure(error)
}
