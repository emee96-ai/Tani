package com.tani.app.data.cache

import android.content.Context
import com.tani.app.data.HomeFeed
import com.tani.app.data.ProductCard
import com.tani.app.data.StoreCard
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

@Serializable
private data class HomeFeedCacheEnvelope(
    val version: Int,
    val savedAtEpochMs: Long,
    val feed: HomeFeed
)

@Serializable
private data class StoresCacheEnvelope(
    val version: Int,
    val savedAtEpochMs: Long,
    val stores: List<StoreCard>
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
        nowEpochMs: Long = System.currentTimeMillis(),
        allowExpired: Boolean = false
    ): List<ProductCard> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        val envelope = runCatching {
            Supabase.json.decodeFromString<MarketplaceCacheEnvelope>(raw)
        }.getOrNull()

        if (envelope == null) {
            prefs.edit().remove(key).apply()
            return emptyList()
        }
        if (!allowExpired && !MarketplaceCachePolicy.isFresh(
                version = envelope.version,
                savedAtEpochMs = envelope.savedAtEpochMs,
                nowEpochMs = nowEpochMs,
                maxAgeMs = maxAgeMs
            )
        ) {
            return emptyList()
        }
        return envelope.products
    }

    fun saveHomeFeed(feed: HomeFeed, nowEpochMs: Long = System.currentTimeMillis()) {
        val envelope = HomeFeedCacheEnvelope(
            version = MarketplaceCachePolicy.CURRENT_VERSION,
            savedAtEpochMs = nowEpochMs,
            feed = feed
        )
        prefs.edit().putString(HOME_FEED_KEY, Supabase.json.encodeToString(envelope)).apply()
    }

    fun loadHomeFeed(
        nowEpochMs: Long = System.currentTimeMillis(),
        allowExpired: Boolean = false
    ): HomeFeed? {
        val raw = prefs.getString(HOME_FEED_KEY, null) ?: return null
        val envelope = runCatching {
            Supabase.json.decodeFromString<HomeFeedCacheEnvelope>(raw)
        }.getOrNull()
        if (envelope == null) {
            prefs.edit().remove(HOME_FEED_KEY).apply()
            return null
        }
        if (!allowExpired && !MarketplaceCachePolicy.isFresh(
                version = envelope.version,
                savedAtEpochMs = envelope.savedAtEpochMs,
                nowEpochMs = nowEpochMs,
                maxAgeMs = MarketplaceCachePolicy.HOME_TTL_MS
            )
        ) {
            return null
        }
        return envelope.feed
    }

    fun saveStores(stores: List<StoreCard>, nowEpochMs: Long = System.currentTimeMillis()) {
        val envelope = StoresCacheEnvelope(
            version = MarketplaceCachePolicy.CURRENT_VERSION,
            savedAtEpochMs = nowEpochMs,
            stores = stores
        )
        prefs.edit().putString(STORES_KEY, Supabase.json.encodeToString(envelope)).apply()
    }

    fun loadStores(
        maxAgeMs: Long = MarketplaceCachePolicy.DEFAULT_TTL_MS,
        nowEpochMs: Long = System.currentTimeMillis(),
        allowExpired: Boolean = false
    ): List<StoreCard> {
        val raw = prefs.getString(STORES_KEY, null) ?: return emptyList()
        val envelope = runCatching {
            Supabase.json.decodeFromString<StoresCacheEnvelope>(raw)
        }.getOrNull()
        if (envelope == null) {
            prefs.edit().remove(STORES_KEY).apply()
            return emptyList()
        }
        if (!allowExpired && !MarketplaceCachePolicy.isFresh(
                version = envelope.version,
                savedAtEpochMs = envelope.savedAtEpochMs,
                nowEpochMs = nowEpochMs,
                maxAgeMs = maxAgeMs
            )
        ) return emptyList()
        return envelope.stores
    }

    private companion object {
        const val HOME_FEED_KEY = "home_feed"
        const val STORES_KEY = "all_stores"
    }
}
