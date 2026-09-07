package com.tani.app.data.growth

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class Favorite(val id: String, val user_id: String, val product_id: String, val created_at: String? = null)

@Serializable
data class NotificationItem(
    val id: String,
    val user_id: String,
    val type: String,
    val title: String,
    val body: String,
    val data: JsonObject = JsonObject(emptyMap()),
    val read_at: String? = null,
    val created_at: String? = null
)

@Serializable
data class NotificationPreferences(
    val user_id: String,
    val push_enabled: Boolean = true,
    val order_updates: Boolean = true,
    val promotions: Boolean = true,
    val messages: Boolean = true,
    val updated_at: String? = null
)

@Serializable
data class Referral(val id: String, val referrer_id: String, val referred_id: String, val code: String? = null, val status: String = "pending", val created_at: String? = null)

@Serializable
data class Banner(
    val id: String,
    val title: String,
    val image_url: String,
    val action_type: String? = null,
    val action_value: String? = null,
    val starts_at: String? = null,
    val ends_at: String? = null,
    val sort_order: Int = 0,
    val is_active: Boolean = true,
    val created_at: String? = null
)

@Serializable
data class MerchantMetrics(
    val id: String,
    val seller_id: String,
    val total_orders: Int = 0,
    val completed_orders: Int = 0,
    val cancelled_orders: Int = 0,
    val total_sales: Double = 0.0,
    val average_rating: Double? = null,
    val product_views: Int = 0,
    val repeat_customers: Int = 0,
    val conversion_rate: Double = 0.0,
    val cancellation_rate: Double = 0.0,
    val avg_response_minutes: Double? = null,
    val updated_at: String? = null
)
