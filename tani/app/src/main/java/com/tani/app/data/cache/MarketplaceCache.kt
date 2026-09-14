package com.tani.app.data.cache

import android.content.Context
import com.tani.app.data.ProductCard
import com.tani.app.data.Supabase
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

@Serializable
private data class MarketplaceCacheEnvelope(
    val version: Int,
    val savedAtEpochMs: Long,
    val products: List<ProductCard>
)

class MarketplaceCache(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("tani_market_cache", Context.MODE_PRIVATE)

    fun save(key: String, products: List<ProductCard>, nowEpochMs: Long = System.currentTimeMillis()) {
        val envelope = MarketplaceCacheEnvelope(
            version = MarketplaceCachePolicy.CURRENT_VERSION,
            savedAtEpochMs = nowEpochMs,
            products = products
        )
        prefs.edit().putString(key, Supabase.json.encodeToString(envelope)).apply()
    }

    fun load(
        key: String,
        maxAgeMs: Long = MarketplaceCachePolicy.DEFAULT_TTL_MS,
        nowEpochMs: Long = System.currentTimeMillis()
    ): List<ProductCard> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        val envelope = runCatching {
            Supabase.json.decodeFromString<MarketplaceCacheEnvelope>(raw)
        }.getOrNull()

        if (envelope == null || !MarketplaceCachePolicy.isFresh(
                version = envelope.version,
                savedAtEpochMs = envelope.savedAtEpochMs,
                nowEpochMs = nowEpochMs,
                maxAgeMs = maxAgeMs
            )
        ) {
            prefs.edit().remove(key).apply()
            return emptyList()
        }
        return envelope.products
    }
}
