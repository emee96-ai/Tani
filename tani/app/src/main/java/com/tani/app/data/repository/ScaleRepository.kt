package com.tani.app.data.repository

import com.tani.app.data.ProductCard
import com.tani.app.data.Supabase
import com.tani.app.data.scale.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ScaleRepository {
    suspend fun rankedSearch(query: String, categoryId: String? = null, city: String = "كوستي", limit: Int = 30): List<ProductCard> = Supabase.post(
        "rpc/search_marketplace_ranked",
        buildJsonObject {
            put("p_query", query.trim()); categoryId?.let { put("p_category_id", it) }
            put("p_city", city); put("p_limit", limit.coerceIn(1, 60)); put("p_offset", 0)
        }.toString()
    )

    suspend fun recommendations(limit: Int = 12): List<ProductCard> = Supabase.post(
        "rpc/recommended_products",
        buildJsonObject { put("p_limit", limit.coerceIn(1, 30)) }.toString()
    )

    suspend fun activeCities(): List<MarketCity> = Supabase.get(
        "market_cities",
        "select=*&is_active=eq.true&order=sort_order.asc,name.asc"
    )

    suspend fun deliveryProviders(): List<DeliveryProvider> = Supabase.get(
        "delivery_providers",
        "select=code,display_name,provider_type,is_active,supported_cities&is_active=eq.true&order=display_name.asc"
    )

    suspend fun inventoryHealth(): InventoryHealth = Supabase.post("rpc/merchant_inventory_health", "{}")

    suspend fun weeklyKpis(): WeeklyKpis = Supabase.post("rpc/weekly_marketplace_kpis", "{}")
}
