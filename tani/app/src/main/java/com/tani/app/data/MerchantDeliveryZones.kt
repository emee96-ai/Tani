package com.tani.app.data

import com.tani.app.data.commerce.CheckoutQuantityPolicy
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

data class MerchantDeliveryZoneUpdateInput(
    val area: String,
    val fee: Double,
    val estimatedMinutes: Int? = null,
    val isActive: Boolean = true
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

suspend fun Repository.checkoutWithDeliveryZones(
    addressId: String,
    phone: String,
    notes: String,
    items: List<CartItem>,
    idempotencyKey: String,
    deliveryZones: Map<String, MerchantDeliveryZone>,
    expectedGrandTotal: Double,
    quoteToken: String
): String {
    require(items.isNotEmpty()) { "السلة فارغة" }
    require(phone.trim().length in 7..30) { "رقم الهاتف غير صحيح" }
    require(idempotencyKey.length in 16..100) { "تعذر تجهيز الطلب. حاولي مرة أخرى" }
    require(expectedGrandTotal >= 0) { "إجمالي الطلب غير صحيح" }
    require(quoteToken.length == 32) { "انتهت صلاحية التسعير. أعيدي المحاولة" }

    val quantities = CheckoutQuantityPolicy.aggregate(items.map {
        CheckoutQuantityPolicy.LineKey(it.product.id, it.variant?.id) to it.quantity
    })
    val payload = buildJsonArray {
        quantities.forEach { (key, quantity) ->
            add(buildJsonObject {
                put("product_id", key.productId)
                key.variantId?.let { put("variant_id", it) }
                put("quantity", quantity)
            })
        }
    }
    val selections = buildJsonObject {
        deliveryZones.forEach { (sellerId, zone) ->
            require(zone.seller_id == sellerId && zone.is_active) { "اختيار منطقة التوصيل غير صالح" }
            put(sellerId, zone.id)
        }
    }

    val groupId: String = Supabase.post(
        "rpc/checkout_create_order_group_v4",
        buildJsonObject {
            put("p_address_id", addressId)
            put("p_phone", phone.trim())
            put("p_notes", notes.trim())
            put("p_items", payload)
            put("p_idempotency_key", idempotencyKey)
            put("p_delivery_zones", selections)
            put("p_expected_grand_total", expectedGrandTotal)
            put("p_quote_token", quoteToken)
        }.toString()
    )
    Analytics.track(
        "order_group_created",
        screen = "checkout",
        entityType = "order_group",
        entityId = groupId,
        metadata = buildJsonObject {
            put("merchant_count", items.map { it.product.seller_id }.distinct().size)
            put("item_count", items.sumOf { it.quantity })
            put("delivery_zone_count", deliveryZones.size)
        }
    )
    return groupId
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
                        put("is_active", true)
                    })
                }
            })
        }.toString()
    )
    Analytics.track("merchant_delivery_zones_updated", screen = "merchant_delivery")
    return result
}

suspend fun Repository.saveMerchantDeliveryConfiguration(
    zones: List<MerchantDeliveryZoneUpdateInput>,
    notes: String,
    deliveryEnabled: Boolean
): List<MerchantDeliveryZone> {
    require(zones.isNotEmpty()) { "أضيفي منطقة توصيل واحدة على الأقل" }
    require(zones.size <= 20) { "الحد الأقصى 20 منطقة توصيل" }
    require(notes.length <= 500) { "ملاحظات التوصيل طويلة جداً" }
    require(zones.all { it.area.trim().length in 2..100 && it.fee >= 0 }) { "راجعي مناطق ورسوم التوصيل" }
    require(zones.all { it.estimatedMinutes == null || it.estimatedMinutes in 1..1440 }) { "راجعي زمن الوصول" }
    require(zones.map { it.area.trim().lowercase() }.distinct().size == zones.size) { "لا تكرري نفس منطقة التوصيل" }
    require(!deliveryEnabled || zones.any { it.isActive }) { "فعّلي منطقة توصيل واحدة على الأقل" }

    val result: List<MerchantDeliveryZone> = Supabase.post(
        "rpc/save_my_delivery_configuration",
        buildJsonObject {
            put("p_zones", buildJsonArray {
                zones.forEachIndexed { index, zone ->
                    add(buildJsonObject {
                        put("area", zone.area.trim())
                        put("fee", zone.fee)
                        zone.estimatedMinutes?.let { put("estimated_minutes", it) }
                        put("sort_order", index)
                        put("is_active", zone.isActive)
                    })
                }
            })
            put("p_notes", notes.trim())
            put("p_is_active", deliveryEnabled)
        }.toString()
    )
    Analytics.track(
        "merchant_delivery_configuration_updated",
        screen = "merchant_delivery",
        metadata = buildJsonObject {
            put("zone_count", zones.size)
            put("active_zone_count", zones.count { it.isActive })
            put("delivery_enabled", deliveryEnabled)
        }
    )
    return result
}
