package com.tani.app.data.cache

internal object MarketplaceCachePolicy {
    const val CURRENT_VERSION = 1
    const val DEFAULT_TTL_MS = 6 * 60 * 60 * 1000L
    const val HOME_TTL_MS = 10 * 60 * 1000L

    fun isFresh(
        version: Int,
        savedAtEpochMs: Long,
        nowEpochMs: Long,
        maxAgeMs: Long = DEFAULT_TTL_MS
    ): Boolean {
        if (version != CURRENT_VERSION || maxAgeMs <= 0) return false
        val ageMs = nowEpochMs - savedAtEpochMs
        return ageMs in 0..maxAgeMs
    }
}
