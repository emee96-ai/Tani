package com.tani.admin

import kotlinx.coroutines.CancellationException

inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (error: Throwable) {
    if (error is CancellationException) throw error
    Result.failure(error)
}
