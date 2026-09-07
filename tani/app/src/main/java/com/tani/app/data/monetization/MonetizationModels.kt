package com.tani.app.data.monetization

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class SubscriptionPlan(
    val id: String,
    val name: String,
    val description: String = "",
    val price: Double = 0.0,
    val duration_days: Int,
    val features: JsonObject = JsonObject(emptyMap()),
    val is_active: Boolean = true,
    val slug: String? = null,
    val audience: String = "merchant",
    val sort_order: Int = 0
)

@Serializable
data class Subscription(
    val id: String,
    val user_id: String,
    val plan_id: String,
    val status: String,
    val starts_at: String,
    val ends_at: String? = null,
    val seller_id: String? = null,
    val source: String = "admin",
    val auto_renew: Boolean = false
)

@Serializable
data class SubscriptionRequest(
    val id: String,
    val user_id: String,
    val seller_id: String,
    val plan_id: String,
    val status: String = "pending",
    val note: String? = null,
    val admin_note: String? = null,
    val created_at: String? = null,
    val reviewed_at: String? = null
)

@Serializable
data class FeaturedRequest(
    val id: String,
    val seller_id: String,
    val product_id: String? = null,
    val placement: String,
    val requested_starts_at: String? = null,
    val requested_ends_at: String? = null,
    val status: String = "pending",
    val merchant_note: String? = null,
    val admin_note: String? = null,
    val created_at: String? = null
)

@Serializable
data class AdCampaign(
    val id: String,
    val seller_id: String,
    val name: String,
    val placement: String,
    val product_id: String? = null,
    val budget: Double? = null,
    val starts_at: String? = null,
    val ends_at: String? = null,
    val status: String = "draft",
    val impressions: Long = 0,
    val clicks: Long = 0,
    val created_at: String? = null
)

@Serializable
data class PaymentMethod(
    val code: String,
    val display_name: String,
    val provider: String? = null,
    val is_online: Boolean = false,
    val is_active: Boolean = false,
    val sort_order: Int = 0,
    val public_config: JsonObject = JsonObject(emptyMap())
)
