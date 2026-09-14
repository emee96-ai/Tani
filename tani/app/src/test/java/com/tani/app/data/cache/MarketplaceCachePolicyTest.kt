package com.tani.app.data.cache

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketplaceCachePolicyTest {
    @Test
    fun acceptsFreshCurrentVersionEntry() {
        assertTrue(
            MarketplaceCachePolicy.isFresh(
                version = MarketplaceCachePolicy.CURRENT_VERSION,
                savedAtEpochMs = 1_000L,
                nowEpochMs = 2_000L,
                maxAgeMs = 2_000L
            )
        )
    }

    @Test
    fun rejectsExpiredEntry() {
        assertFalse(
            MarketplaceCachePolicy.isFresh(
                version = MarketplaceCachePolicy.CURRENT_VERSION,
                savedAtEpochMs = 1_000L,
                nowEpochMs = 4_001L,
                maxAgeMs = 3_000L
            )
        )
    }

    @Test
    fun rejectsFutureOrDifferentVersionEntry() {
        assertFalse(
            MarketplaceCachePolicy.isFresh(
                version = MarketplaceCachePolicy.CURRENT_VERSION,
                savedAtEpochMs = 3_000L,
                nowEpochMs = 2_000L
            )
        )
        assertFalse(
            MarketplaceCachePolicy.isFresh(
                version = MarketplaceCachePolicy.CURRENT_VERSION + 1,
                savedAtEpochMs = 1_000L,
                nowEpochMs = 2_000L
            )
        )
    }
}
