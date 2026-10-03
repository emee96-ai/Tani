package com.tani.app.data.repository

import com.tani.app.util.runCatchingCancellable

import com.tani.app.data.Supabase
import com.tani.app.data.trust.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URLEncoder

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

    suspend fun ticket(ticketId: String): SupportTicket = Supabase.get<List<SupportTicket>>(
        "support_tickets", "select=*&id=eq.$ticketId&limit=1"
    ).firstOrNull() ?: error("التذكرة غير متاحة")

    suspend fun ticketMessages(ticketId: String, before: TicketMessage? = null): List<TicketMessage> = Supabase.get(
        "ticket_messages",
        "select=*&ticket_id=eq.$ticketId&order=created_at.desc,id.desc&limit=100" +
            (before?.created_at?.let { time ->
                val encoded = URLEncoder.encode(time, "UTF-8")
                "&or=(created_at.lt.$encoded,and(created_at.eq.$encoded,id.lt.${before.id}))"
            } ?: "")
    )

    suspend fun sendTicketMessage(ticketId: String, body: String): TicketMessage {
        check(Supabase.userId != null) { "تسجيل الدخول مطلوب" }
        require(body.trim().length in 1..2000) { "الرسالة غير صالحة" }
        return Supabase.post(
            "rpc/send_support_ticket_message",
            buildJsonObject { put("p_ticket_id", ticketId); put("p_body", body.trim()) }.toString()
        )
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
        rating: Int,
        comment: String
    ) {
        check(Supabase.userId != null) { "تسجيل الدخول مطلوب" }
        require(rating in 1..5) { "التقييم يجب أن يكون من 1 إلى 5" }
        require(comment.trim().length <= 1000) { "التعليق طويل جداً" }
        Supabase.post<Boolean>("rpc/submit_delivered_order_reviews", buildJsonObject {
            put("p_order_id", orderId); put("p_rating", rating); put("p_comment", comment.trim())
        }.toString())
    }
}
