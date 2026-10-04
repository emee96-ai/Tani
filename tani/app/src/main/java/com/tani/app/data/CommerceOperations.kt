package com.tani.app.data

import com.tani.app.data.commerce.CartQuote
import com.tani.app.data.commerce.CheckoutQuantityPolicy
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

// Customer shopping: addresses, cart, checkout and order history.

private const val CUSTOMER_ORDER_HISTORY_LIMIT = 200

suspend fun Repository.recoverPendingCheckout(): String? {
    val uid = Supabase.userId ?: error("تسجيل الدخول مطلوب")
    val attempt = PendingCheckoutStore.pending(uid) ?: return null
    attempt.groupId?.let { return it }
    val group = Supabase.get<List<OrderGroup>>("order_groups",
        "select=*&customer_id=eq.$uid&idempotency_key=eq.${attempt.key}&limit=1").firstOrNull()
        ?: return null
    PendingCheckoutStore.resolve(uid, attempt.key, group.id)
    return group.id
}

suspend fun Repository.addresses(): List<Address> {
    val uid = Supabase.userId ?: return emptyList()
    return Supabase.get(
        "addresses",
        "select=*&user_id=eq.$uid&order=is_default.desc,created_at.desc"
    )
}

suspend fun Repository.addAddress(
    label: String,
    description: String,
    area: String?,
    landmark: String?,
    phone: String?,
    deliveryNotes: String?,
    isDefault: Boolean
): Address {
    val uid = Supabase.userId ?: error("تسجيل الدخول مطلوب")
    val cleanDescription = description.trim()
    require(cleanDescription.length in 2..500) { "أدخل عنواناً واضحاً" }
    val cleanPhone = phone?.trim()?.takeIf { it.isNotBlank() }
    if (cleanPhone != null) require(cleanPhone.length in 7..30) { "رقم الهاتف غير صحيح" }

    val result: List<Address> = Supabase.post(
        "addresses",
        buildJsonObject {
            put("user_id", uid)
            put("label", label.trim().ifBlank { "المنزل" })
            put("description", cleanDescription)
            area?.trim()?.takeIf { it.isNotBlank() }?.let { put("area", it) }
            landmark?.trim()?.takeIf { it.isNotBlank() }?.let { put("landmark", it) }
            cleanPhone?.let { put("phone", it) }
            deliveryNotes?.trim()?.takeIf { it.isNotBlank() }?.let { put("delivery_notes", it) }
            put("is_default", isDefault)
        }.toString()
    )
    Analytics.track("address_created", screen = "addresses")
    return result.firstOrNull() ?: error("تعذر حفظ العنوان")
}

suspend fun Repository.updateAddress(
    id: String,
    label: String,
    description: String,
    area: String?,
    landmark: String?,
    phone: String?,
    deliveryNotes: String?,
    isDefault: Boolean
): Address {
    val cleanDescription = description.trim()
    require(cleanDescription.length in 2..500) { "أدخل عنواناً واضحاً" }
    val result: List<Address> = Supabase.patch(
        "addresses",
        "id=eq.$id",
        buildJsonObject {
            put("label", label.trim().ifBlank { "المنزل" })
            put("description", cleanDescription)
            put("area", area?.trim().orEmpty())
            put("landmark", landmark?.trim().orEmpty())
            put("phone", phone?.trim().orEmpty())
            put("delivery_notes", deliveryNotes?.trim().orEmpty())
            put("is_default", isDefault)
            put("updated_at", java.time.Instant.now().toString())
        }.toString()
    )
    return result.firstOrNull() ?: error("تعذر تحديث العنوان")
}

suspend fun Repository.deleteAddress(id: String) {
    Supabase.delete("addresses", "id=eq.$id")
}

suspend fun Repository.remoteCart(): List<RemoteCartItem> = Supabase.get(
    "my_cart_items",
    "select=*&order=updated_at.desc"
)

suspend fun Repository.syncCart(items: List<CartItem>): String {
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
    return Supabase.post(
        "rpc/sync_my_cart_v2",
        buildJsonObject { put("p_items", payload) }.toString()
    )
}

suspend fun Repository.quoteCart(
    items: List<CartItem>,
    deliveryZones: Map<String, MerchantDeliveryZone>
): CartQuote {
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
            require(zone.seller_id == sellerId) { "اختيار منطقة التوصيل غير صالح" }
            put(sellerId, zone.id)
        }
    }

    return Supabase.post(
        "rpc/quote_cart_v3",
        buildJsonObject {
            put("p_items", payload)
            put("p_delivery_zones", selections)
        }.toString()
    )
}

suspend fun Repository.checkout(
    addressId: String,
    phone: String,
    notes: String,
    items: List<CartItem>,
    idempotencyKey: String
): String {
    require(items.isNotEmpty()) { "السلة فارغة" }
    require(phone.trim().length in 7..30) { "رقم الهاتف غير صحيح" }
    require(idempotencyKey.length in 16..100) { "تعذر تجهيز الطلب. حاول مرة أخرى" }

    val payload = buildJsonArray {
        items.forEach { item ->
            add(buildJsonObject {
                put("product_id", item.product.id)
                put("quantity", item.quantity)
            })
        }
    }

    val groupId: String = Supabase.post(
        "rpc/checkout_create_order_group",
        buildJsonObject {
            put("p_address_id", addressId)
            put("p_phone", phone.trim())
            put("p_notes", notes.trim())
            put("p_items", payload)
            put("p_idempotency_key", idempotencyKey)
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
        }
    )
    return groupId
}

suspend fun Repository.orderGroups(): List<OrderGroup> {
    val uid = Supabase.userId ?: return emptyList()
    return Supabase.get(
        "order_groups",
        "select=*&customer_id=eq.$uid&order=created_at.desc&limit=$CUSTOMER_ORDER_HISTORY_LIMIT"
    )
}

suspend fun Repository.ordersForGroup(groupId: String): List<Order> = Supabase.get(
    "orders",
    "select=*&order_group_id=eq.$groupId&order=created_at.asc"
)

suspend fun Repository.orderGroupDetails(groupId: String): OrderGroupDetails = coroutineScope {
    val groupDeferred = async {
        Supabase.get<List<OrderGroup>>(
            "order_groups",
            "select=*&id=eq.$groupId&limit=1"
        ).firstOrNull() ?: error("الطلب غير موجود")
    }
    val ordersDeferred = async { ordersForGroup(groupId) }

    val group = groupDeferred.await()
    val merchantOrders = ordersDeferred.await()
    val ids = merchantOrders.map { it.id }
    if (ids.isEmpty()) return@coroutineScope OrderGroupDetails(group, emptyList(), emptyMap(), emptyMap())
    val filter = ids.joinToString(",")

    val itemsDeferred = async {
        Supabase.get<List<OrderItem>>(
            "order_items",
            "select=*&order_id=in.($filter)&order=created_at.asc"
        )
    }
    val historyDeferred = async {
        Supabase.get<List<OrderStatusHistory>>(
            "order_status_history",
            "select=*&order_id=in.($filter)&order=created_at.asc"
        )
    }

    val items = itemsDeferred.await().groupBy { it.order_id }
    val history = historyDeferred.await().groupBy { it.order_id }
    OrderGroupDetails(group, merchantOrders, items, history)
}

suspend fun Repository.cancelOrderGroup(groupId: String, note: String = "إلغاء بواسطة العميل"): Int {
    val result: Int = Supabase.post(
        "rpc/customer_cancel_order_group",
        buildJsonObject {
            put("p_order_group_id", groupId)
            put("p_note", note)
        }.toString()
    )
    Analytics.track(
        "order_group_cancelled",
        screen = "order_details",
        entityType = "order_group",
        entityId = groupId
    )
    return result
}
