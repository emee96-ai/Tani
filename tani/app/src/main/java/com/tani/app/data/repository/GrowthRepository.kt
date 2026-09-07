package com.tani.app.data.repository

import com.tani.app.data.ProductCard
import com.tani.app.data.Supabase
import com.tani.app.data.growth.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class GrowthRepository {
    suspend fun favoriteIds(): Set<String> {
        val uid = Supabase.userId ?: return emptySet()
        return Supabase.get<List<Favorite>>("favorites", "select=*&user_id=eq.$uid&order=created_at.desc")
            .map { it.product_id }.toSet()
    }

    suspend fun isFavorite(productId: String): Boolean = favoriteIds().contains(productId)

    suspend fun setFavorite(productId: String, favorite: Boolean) {
        val uid = Supabase.userId ?: error("تسجيل الدخول مطلوب")
        if (favorite) {
            if (!isFavorite(productId)) {
                Supabase.post<List<Favorite>>(
                    "favorites",
                    buildJsonObject { put("user_id", uid); put("product_id", productId) }.toString()
                )
            }
        } else {
            Supabase.delete("favorites", "user_id=eq.$uid&product_id=eq.$productId")
        }
    }

    suspend fun favoriteProducts(): List<ProductCard> {
        val ids = favoriteIds().toList()
        if (ids.isEmpty()) return emptyList()
        return Supabase.get(
            "marketplace_product_cards",
            "select=*&id=in.(${ids.joinToString(",")})&order=created_at.desc&limit=100"
        )
    }

    suspend fun notifications(): List<NotificationItem> {
        val uid = Supabase.userId ?: error("تسجيل الدخول مطلوب")
        return Supabase.get("notifications", "select=*&user_id=eq.$uid&order=created_at.desc&limit=100")
    }

    suspend fun markNotificationRead(id: String) {
        Supabase.patch<List<NotificationItem>>(
            "notifications",
            "id=eq.$id",
            buildJsonObject { put("read_at", java.time.Instant.now().toString()) }.toString()
        )
    }

    suspend fun notificationPreferences(): NotificationPreferences {
        val uid = Supabase.userId ?: error("تسجيل الدخول مطلوب")
        val row = Supabase.get<List<NotificationPreferences>>(
            "notification_preferences",
            "select=*&user_id=eq.$uid&limit=1"
        ).firstOrNull()
        if (row != null) return row
        return Supabase.post<List<NotificationPreferences>>(
            "notification_preferences",
            buildJsonObject { put("user_id", uid) }.toString()
        ).first()
    }

    suspend fun saveNotificationPreferences(value: NotificationPreferences): NotificationPreferences {
        val uid = Supabase.userId ?: error("تسجيل الدخول مطلوب")
        val rows: List<NotificationPreferences> = Supabase.patch(
            "notification_preferences",
            "user_id=eq.$uid",
            buildJsonObject {
                put("push_enabled", value.push_enabled); put("order_updates", value.order_updates)
                put("promotions", value.promotions); put("messages", value.messages)
                put("updated_at", java.time.Instant.now().toString())
            }.toString()
        )
        return rows.firstOrNull() ?: notificationPreferences()
    }

    suspend fun referralCode(): String = Supabase.post("rpc/my_referral_code", "{}")

    suspend fun applyReferralCode(code: String): Boolean = Supabase.post(
        "rpc/apply_referral_code",
        buildJsonObject { put("p_code", code.trim()) }.toString()
    )

    suspend fun referrals(): List<Referral> = Supabase.get("referrals", "select=*&order=created_at.desc&limit=100")

    suspend fun banners(): List<Banner> = Supabase.get("banners", "select=*&order=sort_order.asc,created_at.desc&limit=20")

    suspend fun merchantMetrics(sellerId: String): MerchantMetrics? = Supabase.get<List<MerchantMetrics>>(
        "merchant_metrics",
        "select=*&seller_id=eq.$sellerId&limit=1"
    ).firstOrNull()
}
