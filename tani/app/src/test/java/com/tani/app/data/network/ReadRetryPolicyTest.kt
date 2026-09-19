package com.tani.app.data.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadRetryPolicyTest {
    @Test
    fun retriesOnlyTransientHttpStatusesWithinBudget() {
        listOf(408, 429, 500, 502, 503, 504).forEach { status ->
            assertTrue("$status should retry", ReadRetryPolicy.shouldRetryStatus(status, attempt = 1))
        }
        listOf(200, 400, 401, 403, 404, 409, 422).forEach { status ->
            assertFalse("$status should not retry", ReadRetryPolicy.shouldRetryStatus(status, attempt = 1))
        }
    }

    @Test
    fun stopsAfterThreeTotalAttempts() {
        assertTrue(ReadRetryPolicy.shouldRetryFailure(attempt = 1))
        assertTrue(ReadRetryPolicy.shouldRetryFailure(attempt = 2))
        assertFalse(ReadRetryPolicy.shouldRetryFailure(attempt = 3))
        assertFalse(ReadRetryPolicy.shouldRetryStatus(503, attempt = 3))
    }

    @Test
    fun usesShortBoundedExponentialDelay() {
        assertEquals(300L, ReadRetryPolicy.delayMillis(1))
        assertEquals(600L, ReadRetryPolicy.delayMillis(2))
        assertEquals(1_200L, ReadRetryPolicy.delayMillis(3))
        assertEquals(2_400L, ReadRetryPolicy.delayMillis(4))
        assertEquals(2_400L, ReadRetryPolicy.delayMillis(8))
    }
}
