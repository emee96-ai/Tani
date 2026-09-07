package com.tani.app.data.trust

import kotlinx.serialization.Serializable

@Serializable
data class MerchantTrustScore(
    val seller_id: String,
    val trust_level: String = "verified",
    val trust_score: Double = 50.0,
    val completed_orders: Int = 0,
    val cancelled_orders: Int = 0,
    val review_count: Int = 0,
    val average_rating: Double? = null,
    val open_complaints: Int = 0,
    val completion_rate: Double = 0.0,
    val updated_at: String? = null
)

@Serializable
data class SupportTicket(
    val id: String,
    val user_id: String,
    val subject: String,
    val description: String,
    val status: String = "open",
    val priority: String = "normal",
    val assigned_to: String? = null,
    val created_at: String? = null,
    val updated_at: String? = null
)

@Serializable
data class TicketMessage(
    val id: String,
    val ticket_id: String,
    val sender_id: String,
    val body: String,
    val created_at: String? = null
)

@Serializable
data class Complaint(
    val id: String,
    val user_id: String,
    val order_id: String? = null,
    val seller_id: String? = null,
    val subject: String,
    val description: String,
    val status: String = "open",
    val priority: String = "normal",
    val category: String = "other",
    val resolution: String? = null,
    val resolved_at: String? = null,
    val created_at: String? = null,
    val updated_at: String? = null
)

@Serializable
data class MerchantReview(
    val id: String,
    val order_id: String,
    val seller_id: String,
    val customer_id: String,
    val rating: Int,
    val comment: String = "",
    val status: String = "published",
    val created_at: String? = null
)
