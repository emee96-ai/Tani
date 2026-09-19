package com.tani.app.data.cache

internal object MarketplaceCachePolicy {
    // Version 2 is the Room-backed payload format introduced in phase 6.
    const val CURRENT_VERSION = 2
    const val DEFAULT_TTL_MS = 6 * 60 * 60 * 1000L
    const val HOME_TTL_MS = 10 * 60 * 1000L

    fun isFresh(
        version: Int,
        savedAtEpochMs: Long,
        nowEpochMs: Long,
        maxAgeMs: Long = DEFAULT_TTL_MS
    ): Boolean = isUsable(
        version = version,
        savedAtEpochMs = savedAtEpochMs,
        nowEpochMs = nowEpochMs,
        maxAgeMs = maxAgeMs,
        allowExpired = false
    )

    fun isUsable(
        version: Int,
        savedAtEpochMs: Long,
        nowEpochMs: Long,
        maxAgeMs: Long = DEFAULT_TTL_MS,
        allowExpired: Boolean
    ): Boolean {
        if (version != CURRENT_VERSION) return false
        val ageMs = nowEpochMs - savedAtEpochMs
        if (ageMs < 0) return false
        if (allowExpired) return true
        if (maxAgeMs <= 0) return false
        return ageMs <= maxAgeMs
    }
}
