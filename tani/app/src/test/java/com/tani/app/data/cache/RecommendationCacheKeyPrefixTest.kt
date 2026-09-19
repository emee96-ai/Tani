package com.tani.app.data.cache

import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationCacheKeyPrefixTest {
    @Test
    fun `room cleanup prefix matches user recommendation keys`() {
        val key = RecommendationCacheKey.forUser("user-123")
        assertTrue(key.startsWith(RecommendationCacheKey.USER_PREFIX))
    }
}
