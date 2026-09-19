package com.tani.app.data.network

import kotlin.math.min

/**
 * Retry policy used only for idempotent reads.
 *
 * Writes (POST/PATCH/DELETE) are deliberately excluded because retrying a write
 * after a connection drop can duplicate side effects unless that endpoint has
 * an explicit idempotency contract.
 */
object ReadRetryPolicy {
    const val MAX_ATTEMPTS = 3

    private val retryableStatusCodes = setOf(408, 429, 500, 502, 503, 504)

    fun shouldRetryStatus(statusCode: Int, attempt: Int): Boolean =
        attempt < MAX_ATTEMPTS && statusCode in retryableStatusCodes

    fun shouldRetryFailure(attempt: Int): Boolean = attempt < MAX_ATTEMPTS

    fun delayMillis(attempt: Int): Long {
        val safeAttempt = attempt.coerceAtLeast(1)
        val exponential = 300L * (1L shl (safeAttempt - 1).coerceAtMost(3))
        return min(exponential, 2_400L)
    }
}
