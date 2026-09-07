package com.tani.app.data.repository

import com.tani.app.data.Supabase
import com.tani.app.data.monetization.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class MonetizationRepository {
    suspend fun plans(): List<SubscriptionPlan> = Supabase.get(
        "subscription_plans",
        "select=*&is_active=eq.true&audience=in.(merchant,all)&order=sort_order.asc,price.asc"
    )

    suspend fun mySubscriptions(): List<Subscription> = Supabase.get(
        "subscriptions",
        "select=*&order=created_at.desc&limit=30"
    )

    suspend fun myEntitlements(): JsonObject = Supabase.post("rpc/my_merchant_entitlements", "{}")

    suspend fun subscriptionRequests(): List<SubscriptionRequest> = Supabase.get(
        "subscription_requests", "select=*&order=created_at.desc&limit=30"
    )

    suspend fun requestSubscription(sellerId: String, planId: String, note: String): SubscriptionRequest {
        val uid = Supabase.userId ?: error("تسجيل الدخول مطلوب")
        val rows: List<SubscriptionRequest> = Supabase.post(
            "subscription_requests",
            buildJsonObject {
                put("user_id", uid); put("seller_id", sellerId); put("plan_id", planId)
                put("status", "pending"); put("note", note.trim())
            }.toString()
        )
        return rows.firstOrNull() ?: error("تعذر إرسال طلب الاشتراك")
    }

    suspend fun featuredRequests(sellerId: String): List<FeaturedRequest> = Supabase.get(
        "featured_requests",
        "select=*&seller_id=eq.$sellerId&order=created_at.desc&limit=50"
    )

    suspend fun requestFeatured(sellerId: String, productId: String?, placement: String, note: String): FeaturedRequest {
        val rows: List<FeaturedRequest> = Supabase.post(
            "featured_requests",
            buildJsonObject {
                put("seller_id", sellerId); productId?.let { put("product_id", it) }
                put("placement", placement); put("merchant_note", note.trim()); put("status", "pending")
            }.toString()
        )
        return rows.firstOrNull() ?: error("تعذر إرسال طلب الظهور المميز")
    }

    suspend fun adCampaigns(sellerId: String): List<AdCampaign> = Supabase.get(
        "merchant_ad_campaigns",
        "select=*&seller_id=eq.$sellerId&order=created_at.desc&limit=50"
    )

    suspend fun createAdCampaign(sellerId: String, name: String, placement: String, productId: String?, budget: Double?): AdCampaign {
        require(name.trim().length in 2..120) { "اسم الحملة مطلوب" }
        if (budget != null) require(budget >= 0) { "الميزانية غير صحيحة" }
        val rows: List<AdCampaign> = Supabase.post(
            "merchant_ad_campaigns",
            buildJsonObject {
                put("seller_id", sellerId); put("name", name.trim()); put("placement", placement)
                productId?.let { put("product_id", it) }; budget?.let { put("budget", it) }; put("status", "pending")
            }.toString()
        )
        return rows.firstOrNull() ?: error("تعذر إنشاء الحملة")
    }

    suspend fun paymentMethods(): List<PaymentMethod> = Supabase.get(
        "payment_methods",
        "select=*&is_active=eq.true&order=sort_order.asc"
    )
}
