package com.tani.app.data.repository

import com.tani.app.data.Supabase
import com.tani.app.data.trust.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class TrustRepository {
    suspend fun merchantTrust(sellerId: String): MerchantTrustScore? = Supabase.get<List<MerchantTrustScore>>(
        "merchant_trust_scores",
        "select=*&seller_id=eq.$sellerId&limit=1"
    ).firstOrNull()

    suspend fun tickets(): List<SupportTicket> = Supabase.get(
        "support_tickets",
        "select=*&order=created_at.desc&limit=50"
    )

    suspend fun createTicket(subject: String, description: String, priority: String = "normal"): SupportTicket {
        val uid = Supabase.userId ?: error("تسجيل الدخول مطلوب")
        require(subject.trim().length in 3..140) { "عنوان التذكرة قصير جداً" }
        require(description.trim().length in 5..2000) { "اكتبي تفاصيل المشكلة" }
        val rows: List<SupportTicket> = Supabase.post(
            "support_tickets",
            buildJsonObject {
                put("user_id", uid); put("subject", subject.trim()); put("description", description.trim())
                put("priority", priority); put("status", "open")
            }.toString()
        )
        return rows.firstOrNull() ?: error("تعذر إنشاء التذكرة")
    }

    suspend fun ticketMessages(ticketId: String): List<TicketMessage> = Supabase.get(
        "ticket_messages",
        "select=*&ticket_id=eq.$ticketId&order=created_at.asc&limit=100"
    )

    suspend fun sendTicketMessage(ticketId: String, body: String): TicketMessage {
        val uid = Supabase.userId ?: error("تسجيل الدخول مطلوب")
        require(body.trim().length in 1..2000) { "الرسالة غير صالحة" }
        val rows: List<TicketMessage> = Supabase.post(
            "ticket_messages",
            buildJsonObject { put("ticket_id", ticketId); put("sender_id", uid); put("body", body.trim()) }.toString()
        )
        return rows.firstOrNull() ?: error("تعذر إرسال الرسالة")
    }

    suspend fun complaints(): List<Complaint> = Supabase.get(
        "complaints",
        "select=*&order=created_at.desc&limit=50"
    )

    suspend fun createComplaint(
        subject: String,
        description: String,
        category: String,
        orderId: String? = null,
        sellerId: String? = null
    ): Complaint {
        val uid = Supabase.userId ?: error("تسجيل الدخول مطلوب")
        require(subject.trim().length in 3..140) { "عنوان الشكوى قصير جداً" }
        require(description.trim().length in 5..2000) { "اكتبي تفاصيل الشكوى" }
        val rows: List<Complaint> = Supabase.post(
            "complaints",
            buildJsonObject {
                put("user_id", uid); put("subject", subject.trim()); put("description", description.trim())
                put("category", category); put("priority", "normal"); put("status", "open")
                orderId?.let { put("order_id", it) }; sellerId?.let { put("seller_id", it) }
            }.toString()
        )
        return rows.firstOrNull() ?: error("تعذر إنشاء الشكوى")
    }

    suspend fun submitDeliveredOrderReviews(
        orderId: String,
        sellerId: String,
        productIds: List<String>,
        rating: Int,
        comment: String
    ) {
        val uid = Supabase.userId ?: error("تسجيل الدخول مطلوب")
        require(rating in 1..5) { "التقييم يجب أن يكون من 1 إلى 5" }
        val clean = comment.trim().take(1000)
        if (productIds.isNotEmpty()) {
            productIds.distinct().forEach { productId ->
                runCatching {
                    Supabase.post<List<kotlinx.serialization.json.JsonObject>>(
                        "reviews",
                        buildJsonObject {
                            put("product_id", productId); put("customer_id", uid); put("order_id", orderId)
                            put("rating", rating); put("comment", clean); put("status", "published")
                        }.toString()
                    )
                }
            }
        }
        runCatching {
            Supabase.post<List<MerchantReview>>(
                "merchant_reviews",
                buildJsonObject {
                    put("order_id", orderId); put("seller_id", sellerId); put("customer_id", uid)
                    put("rating", rating); put("comment", clean); put("status", "published")
                }.toString()
            )
        }.getOrThrow()
        runCatching {
            Supabase.post<List<kotlinx.serialization.json.JsonObject>>(
                "order_reviews",
                buildJsonObject { put("order_id", orderId); put("customer_id", uid); put("rating", rating); put("comment", clean) }.toString()
            )
        }.getOrThrow()
    }
}
