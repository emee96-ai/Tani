package com.tani.app.data.scale

import kotlinx.serialization.Serializable

@Serializable
data class MarketCity(
    val id: String,
    val code: String,
    val name: String,
    val state_name: String? = null,
    val is_active: Boolean = false,
    val sort_order: Int = 0,
    val launch_notes: String? = null
)

@Serializable
data class DeliveryProvider(
    val code: String,
    val display_name: String,
    val provider_type: String,
    val is_active: Boolean = false,
    val supported_cities: List<String> = emptyList()
)

@Serializable
data class InventoryHealth(
    val seller_id: String,
    val out_of_stock: Int = 0,
    val low_stock: Int = 0,
    val inactive: Int = 0
)

@Serializable
data class WeeklyKpis(
    val start: String? = null,
    val end: String? = null,
    val orders: Int = 0,
    val completed_orders: Int = 0,
    val cancelled_orders: Int = 0,
    val gmv: Double = 0.0,
    val active_customers: Int = 0,
    val active_merchants: Int = 0,
    val repeat_customers: Int = 0,
    val product_views: Int = 0,
    val searches: Int = 0,
    val complaints: Int = 0,
    val app_errors: Int = 0
)
