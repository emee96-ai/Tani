package com.tani.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class MerchantDeliveryZone(
    val id: String,
    val seller_id: String,
    val area_name: String,
    val fee: Double,
    val estimated_minutes: Int? = null,
    val sort_order: Int = 0,
    val is_active: Boolean = true
)

@Serializable
data class MerchantDeliveryQuote(
    val seller_id: String,
    val provider: String? = null,
    val fee: Double = 0.0,
    val estimated_minutes: Int? = null,
    val delivery_area: String? = null,
    val city: String? = null,
    val area: String? = null,
    val available: Boolean = false
)

suspend fun Repository.merchantDeliveryZones(sellerId: String): List<MerchantDeliveryZone> = Supabase.get(
    "delivery_zones",
    "select=id,seller_id,area_name,fee,estimated_minutes,sort_order,is_active&seller_id=eq.$sellerId&order=sort_order.asc,area_name.asc"
)

suspend fun Repository.deliveryQuote(sellerId: String, area: String): MerchantDeliveryQuote {
    require(area.trim().isNotBlank()) { "اختاري منطقة التوصيل" }
    return Supabase.post(
        "rpc/delivery_quote",
        buildJsonObject {
            put("p_seller_id", sellerId)
            put("p_area", area.trim())
        }.toString()
    )
}

suspend fun Repository.replaceMerchantDeliveryZones(
    zones: List<MerchantDeliveryZoneInput>
): List<MerchantDeliveryZone> {
    require(zones.isNotEmpty()) { "أضيفي منطقة توصيل واحدة على الأقل" }
    require(zones.size <= 20) { "الحد الأقصى 20 منطقة توصيل" }
    require(zones.all { it.area.trim().length in 2..100 && it.fee >= 0 }) { "راجعي مناطق ورسوم التوصيل" }
    require(zones.all { it.estimated_minutes == null || it.estimated_minutes in 1..1440 }) { "راجعي زمن الوصول" }
    require(zones.map { it.area.trim().lowercase() }.distinct().size == zones.size) { "لا تكرري نفس منطقة التوصيل" }

    val result: List<MerchantDeliveryZone> = Supabase.post(
        "rpc/replace_my_delivery_zones",
        buildJsonObject {
            put("p_zones", buildJsonArray {
                zones.forEachIndexed { index, zone ->
                    add(buildJsonObject {
                        put("area", zone.area.trim())
                        put("fee", zone.fee)
                        zone.estimated_minutes?.let { put("estimated_minutes", it) }
                        put("sort_order", index)
                    })
                }
            })
        }.toString()
    )
    Analytics.track("merchant_delivery_zones_updated", screen = "merchant_delivery")
    return result
}
