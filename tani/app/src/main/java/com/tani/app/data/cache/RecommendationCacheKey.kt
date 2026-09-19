package com.tani.app.data.cache

/**
 * Personalized recommendation entries are namespaced by user so stale data from
 * one account can never be reused by another account on the same device.
 */
object RecommendationCacheKey {
    internal const val USER_PREFIX = "home_recommendations:user:"
    const val LEGACY_KEY = "home_recommendations"

    fun forUser(userId: String): String {
        val clean = userId.trim()
        require(clean.isNotEmpty()) { "userId is required" }
        return USER_PREFIX + clean
    }

    fun belongsToRecommendations(key: String): Boolean =
        key == LEGACY_KEY || key.startsWith(USER_PREFIX)
}
