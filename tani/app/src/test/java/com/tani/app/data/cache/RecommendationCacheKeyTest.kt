package com.tani.app.data.cache

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationCacheKeyTest {

    @Test
    fun `recommendation keys are isolated per user`() {
        val first = RecommendationCacheKey.forUser("user-a")
        val second = RecommendationCacheKey.forUser("user-b")

        assertNotEquals(first, second)
        assertTrue(RecommendationCacheKey.belongsToRecommendations(first))
        assertTrue(RecommendationCacheKey.belongsToRecommendations(second))
    }

    @Test
    fun `legacy global recommendation key is recognized for cleanup`() {
        assertTrue(
            RecommendationCacheKey.belongsToRecommendations(
                RecommendationCacheKey.LEGACY_KEY
            )
        )
        assertFalse(RecommendationCacheKey.belongsToRecommendations("catalog_preview"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank user cannot create recommendation cache key`() {
        RecommendationCacheKey.forUser("   ")
    }
}
