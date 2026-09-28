package com.tani.app.data

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

suspend fun Repository.setMerchantStoreOpen(storeId: String, isOpen: Boolean): MerchantStore {
    require(storeId.isNotBlank()) { "معرّف المتجر غير صحيح" }
    val rows: List<MerchantStore> = Supabase.patch(
        "stores",
        "id=eq.$storeId",
        buildJsonObject {
            put("is_open", isOpen)
            put("updated_at", java.time.Instant.now().toString())
        }.toString()
    )
    val store = rows.firstOrNull() ?: error("تعذر تحديث حالة المتجر")
    Analytics.track(
        "merchant_store_open_state_updated",
        screen = "merchant_dashboard",
        entityType = "store",
        entityId = storeId,
        metadata = buildJsonObject { put("is_open", isOpen) }
    )
    return store
}
