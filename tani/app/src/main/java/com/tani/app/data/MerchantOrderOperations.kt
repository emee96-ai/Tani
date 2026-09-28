package com.tani.app.data

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

suspend fun Repository.merchantOrderHistory(orderId: String): List<OrderStatusHistory> =
    Supabase.get(
        "order_status_history",
        "select=*&order_id=eq.$orderId&order=created_at.asc"
    )

suspend fun Repository.transitionMerchantOrderOperational(
    orderId: String,
    status: String,
    note: String = "",
    estimatedMinutes: Int? = null
): String {
    require(note.length <= 450) { "الملاحظة طويلة جداً" }
    if (status in setOf("rejected", "cancelled", "failed")) {
        require(note.trim().length >= 3) { "اكتبي سبباً واضحاً" }
    }
    if (status == "out_for_delivery") {
        require(estimatedMinutes != null && estimatedMinutes in 1..1440) {
            "حددي زمن الوصول المتوقع بالدقائق"
        }
    } else {
        require(estimatedMinutes == null) { "زمن الوصول يستخدم فقط عند خروج الطلب للتوصيل" }
    }

    val result: String = Supabase.post(
        "rpc/merchant_transition_order_status",
        buildJsonObject {
            put("p_order_id", orderId)
            put("p_to_status", status)
            put("p_note", note.trim())
            estimatedMinutes?.let { put("p_estimated_minutes", it) }
        }.toString()
    )
    Analytics.track(
        "merchant_order_status_changed",
        screen = "merchant_orders",
        entityType = "order",
        entityId = orderId,
        metadata = buildJsonObject {
            put("status", status)
            estimatedMinutes?.let { put("estimated_minutes", it) }
        }
    )
    return result
}
