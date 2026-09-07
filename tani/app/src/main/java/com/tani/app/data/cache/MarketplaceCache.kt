package com.tani.app.data.cache

import android.content.Context
import com.tani.app.data.ProductCard
import com.tani.app.data.Supabase
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

class MarketplaceCache(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("tani_market_cache", Context.MODE_PRIVATE)

    fun save(key: String, products: List<ProductCard>) {
        prefs.edit().putString(key, Supabase.json.encodeToString(products)).apply()
    }

    fun load(key: String): List<ProductCard> = runCatching {
        val raw = prefs.getString(key, null) ?: return@runCatching emptyList()
        Supabase.json.decodeFromString<List<ProductCard>>(raw)
    }.getOrDefault(emptyList())
}
