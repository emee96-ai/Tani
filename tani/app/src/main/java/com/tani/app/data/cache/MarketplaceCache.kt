package com.tani.app.data.cache

import android.content.Context
import com.tani.app.data.HomeFeed
import com.tani.app.data.ProductCard
import com.tani.app.data.StoreCard
import com.tani.app.data.Supabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

@Serializable
private data class LegacyMarketplaceCacheEnvelope(
    val version: Int,
    val savedAtEpochMs: Long,
    val products: List<ProductCard>
)

@Serializable
private data class LegacyHomeFeedCacheEnvelope(
    val version: Int,
    val savedAtEpochMs: Long,
    val feed: HomeFeed
)

@Serializable
private data class LegacyStoresCacheEnvelope(
    val version: Int,
    val savedAtEpochMs: Long,
    val stores: List<StoreCard>
)

/**
 * Persistent marketplace cache backed by Room.
 *
 * The old SharedPreferences JSON cache is read only as a one-time migration source,
 * then removed key-by-key after a successful Room write.
 */
class MarketplaceCache(context: Context) {
    private val appContext = context.applicationContext
    private val dao = MarketplaceCacheDatabase.get(appContext).cacheDao()
    private val legacyPrefs = appContext.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)

    suspend fun save(
        key: String,
        products: List<ProductCard>,
        nowEpochMs: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        dao.upsert(
            MarketplaceCacheEntry(
                key = key,
                version = MarketplaceCachePolicy.CURRENT_VERSION,
                savedAtEpochMs = nowEpochMs,
                payload = Supabase.json.encodeToString(products)
            )
        )
        legacyPrefs.edit().remove(key).apply()
    }

    suspend fun load(
        key: String,
        maxAgeMs: Long = MarketplaceCachePolicy.DEFAULT_TTL_MS,
        nowEpochMs: Long = System.currentTimeMillis(),
        allowExpired: Boolean = false
    ): List<ProductCard> = withContext(Dispatchers.IO) {
        val entry = dao.get(key) ?: migrateLegacyProducts(key) ?: return@withContext emptyList()
        if (!isUsable(entry, nowEpochMs, maxAgeMs, allowExpired)) return@withContext emptyList()

        decodeOrDelete<List<ProductCard>>(entry)
            ?: emptyList()
    }

    suspend fun saveRecommendations(
        userId: String,
        products: List<ProductCard>,
        nowEpochMs: Long = System.currentTimeMillis()
    ) {
        save(RecommendationCacheKey.forUser(userId), products, nowEpochMs)
        withContext(Dispatchers.IO) {
            dao.delete(RecommendationCacheKey.LEGACY_KEY)
            legacyPrefs.edit().remove(RecommendationCacheKey.LEGACY_KEY).apply()
        }
    }

    suspend fun loadRecommendations(
        userId: String,
        maxAgeMs: Long = MarketplaceCachePolicy.DEFAULT_TTL_MS,
        nowEpochMs: Long = System.currentTimeMillis(),
        allowExpired: Boolean = false
    ): List<ProductCard> = load(
        key = RecommendationCacheKey.forUser(userId),
        maxAgeMs = maxAgeMs,
        nowEpochMs = nowEpochMs,
        allowExpired = allowExpired
    )

    suspend fun clearRecommendations(userId: String? = null) = withContext(Dispatchers.IO) {
        if (userId.isNullOrBlank()) {
            dao.deleteByPrefix(RecommendationCacheKey.USER_PREFIX)
        } else {
            dao.delete(RecommendationCacheKey.forUser(userId))
        }
        dao.delete(RecommendationCacheKey.LEGACY_KEY)

        val legacyKeys = if (userId.isNullOrBlank()) {
            legacyPrefs.all.keys.filter(RecommendationCacheKey::belongsToRecommendations)
        } else {
            listOf(RecommendationCacheKey.forUser(userId), RecommendationCacheKey.LEGACY_KEY)
        }
        if (legacyKeys.isNotEmpty()) {
            val editor = legacyPrefs.edit()
            legacyKeys.forEach(editor::remove)
            editor.apply()
        }
    }

    suspend fun saveHomeFeed(
        feed: HomeFeed,
        nowEpochMs: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        dao.upsert(
            MarketplaceCacheEntry(
                key = HOME_FEED_KEY,
                version = MarketplaceCachePolicy.CURRENT_VERSION,
                savedAtEpochMs = nowEpochMs,
                payload = Supabase.json.encodeToString(feed)
            )
        )
        legacyPrefs.edit().remove(HOME_FEED_KEY).apply()
    }

    suspend fun loadHomeFeed(
        nowEpochMs: Long = System.currentTimeMillis(),
        allowExpired: Boolean = false
    ): HomeFeed? = withContext(Dispatchers.IO) {
        val entry = dao.get(HOME_FEED_KEY) ?: migrateLegacyHomeFeed() ?: return@withContext null
        if (!isUsable(
                entry,
                nowEpochMs,
                MarketplaceCachePolicy.HOME_TTL_MS,
                allowExpired
            )
        ) return@withContext null

        decodeOrDelete<HomeFeed>(entry)
    }

    suspend fun saveStores(
        stores: List<StoreCard>,
        nowEpochMs: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        dao.upsert(
            MarketplaceCacheEntry(
                key = STORES_KEY,
                version = MarketplaceCachePolicy.CURRENT_VERSION,
                savedAtEpochMs = nowEpochMs,
                payload = Supabase.json.encodeToString(stores)
            )
        )
        legacyPrefs.edit().remove(STORES_KEY).apply()
    }

    suspend fun loadStores(
        maxAgeMs: Long = MarketplaceCachePolicy.DEFAULT_TTL_MS,
        nowEpochMs: Long = System.currentTimeMillis(),
        allowExpired: Boolean = false
    ): List<StoreCard> = withContext(Dispatchers.IO) {
        val entry = dao.get(STORES_KEY) ?: migrateLegacyStores() ?: return@withContext emptyList()
        if (!isUsable(entry, nowEpochMs, maxAgeMs, allowExpired)) return@withContext emptyList()

        decodeOrDelete<List<StoreCard>>(entry)
            ?: emptyList()
    }

    private fun isUsable(
        entry: MarketplaceCacheEntry,
        nowEpochMs: Long,
        maxAgeMs: Long,
        allowExpired: Boolean
    ): Boolean = MarketplaceCachePolicy.isUsable(
        version = entry.version,
        savedAtEpochMs = entry.savedAtEpochMs,
        nowEpochMs = nowEpochMs,
        maxAgeMs = maxAgeMs,
        allowExpired = allowExpired
    )

    private suspend inline fun <reified T> decodeOrDelete(entry: MarketplaceCacheEntry): T? {
        val decoded = runCatching {
            Supabase.json.decodeFromString<T>(entry.payload)
        }.getOrNull()
        if (decoded == null) dao.delete(entry.key)
        return decoded
    }

    private suspend fun migrateLegacyProducts(key: String): MarketplaceCacheEntry? {
        val raw = legacyPrefs.getString(key, null) ?: return null
        val envelope = runCatching {
            Supabase.json.decodeFromString<LegacyMarketplaceCacheEnvelope>(raw)
        }.getOrNull()
        if (envelope == null) {
            legacyPrefs.edit().remove(key).apply()
            return null
        }

        val entry = MarketplaceCacheEntry(
            key = key,
            version = MarketplaceCachePolicy.CURRENT_VERSION,
            savedAtEpochMs = envelope.savedAtEpochMs,
            payload = Supabase.json.encodeToString(envelope.products)
        )
        dao.upsert(entry)
        legacyPrefs.edit().remove(key).apply()
        return entry
    }

    private suspend fun migrateLegacyHomeFeed(): MarketplaceCacheEntry? {
        val raw = legacyPrefs.getString(HOME_FEED_KEY, null) ?: return null
        val envelope = runCatching {
            Supabase.json.decodeFromString<LegacyHomeFeedCacheEnvelope>(raw)
        }.getOrNull()
        if (envelope == null) {
            legacyPrefs.edit().remove(HOME_FEED_KEY).apply()
            return null
        }

        val entry = MarketplaceCacheEntry(
            key = HOME_FEED_KEY,
            version = MarketplaceCachePolicy.CURRENT_VERSION,
            savedAtEpochMs = envelope.savedAtEpochMs,
            payload = Supabase.json.encodeToString(envelope.feed)
        )
        dao.upsert(entry)
        legacyPrefs.edit().remove(HOME_FEED_KEY).apply()
        return entry
    }

    private suspend fun migrateLegacyStores(): MarketplaceCacheEntry? {
        val raw = legacyPrefs.getString(STORES_KEY, null) ?: return null
        val envelope = runCatching {
            Supabase.json.decodeFromString<LegacyStoresCacheEnvelope>(raw)
        }.getOrNull()
        if (envelope == null) {
            legacyPrefs.edit().remove(STORES_KEY).apply()
            return null
        }

        val entry = MarketplaceCacheEntry(
            key = STORES_KEY,
            version = MarketplaceCachePolicy.CURRENT_VERSION,
            savedAtEpochMs = envelope.savedAtEpochMs,
            payload = Supabase.json.encodeToString(envelope.stores)
        )
        dao.upsert(entry)
        legacyPrefs.edit().remove(STORES_KEY).apply()
        return entry
    }

    private companion object {
        const val LEGACY_PREFS = "tani_market_cache"
        const val HOME_FEED_KEY = "home_feed"
        const val STORES_KEY = "all_stores"
    }
}
