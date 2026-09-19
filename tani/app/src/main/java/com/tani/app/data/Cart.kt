package com.tani.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

/**
 * سلة محلية خفيفة تعمل حتى مع ضعف الشبكة.
 * الخادم يعيد التحقق من السعر والمخزون ورسوم التوصيل عند Checkout،
 * لذلك هذه القيم للعرض فقط وليست مصدراً موثوقاً لإنشاء الطلب.
 */
object Cart {
    private const val PREFS_NAME = "tani_cart"
    private const val ITEMS_KEY = "items"

    private val map = linkedMapOf<String, CartItem>()
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        restore()
    }

    @Synchronized
    fun all(): List<CartItem> = map.values.toList()

    @Synchronized
    fun add(product: Product, variant: ProductVariant? = null): Boolean {
        if (!product.is_active || (product.has_variants && variant == null)) return false
        if (variant != null && (variant.product_id != product.id || !variant.is_active)) return false
        val stock = variant?.stock ?: product.stock
        if (stock <= 0) return false
        val key = key(product.id, variant?.id)
        val current = map[key]
        val next = ((current?.quantity ?: 0) + 1).coerceAtMost(stock.coerceAtMost(99))
        if (current?.quantity == next) return false
        map[key] = CartItem(product, next, variant)
        persist()
        return true
    }

    @Synchronized
    fun increase(itemKey: String): Boolean {
        val current = map[itemKey] ?: return false
        val max = current.availableStock.coerceAtMost(99)
        if (current.quantity >= max) return false
        map[itemKey] = current.copy(quantity = current.quantity + 1)
        persist()
        return true
    }

    @Synchronized
    fun decrease(itemKey: String) {
        val current = map[itemKey] ?: return
        if (current.quantity <= 1) map.remove(itemKey)
        else map[itemKey] = current.copy(quantity = current.quantity - 1)
        persist()
    }

    @Synchronized
    fun remove(itemKey: String) {
        map.remove(itemKey)
        persist()
    }

    @Synchronized
    fun replace(items: List<CartItem>) {
        map.clear()
        items.forEach { item ->
            if (item.product.is_active && item.availableStock > 0 && item.quantity > 0) {
                map[item.key] = item.copy(
                    quantity = item.quantity.coerceAtMost(item.availableStock.coerceAtMost(99))
                )
            }
        }
        persist()
    }

    @Synchronized
    fun clear() {
        map.clear()
        persist()
    }

    @Synchronized
    fun groupedBySeller(): LinkedHashMap<String, List<CartItem>> {
        val result = linkedMapOf<String, MutableList<CartItem>>()
        map.values.forEach { item ->
            result.getOrPut(item.product.seller_id) { mutableListOf() }.add(item)
        }
        return LinkedHashMap(result.mapValues { it.value.toList() })
    }

    @Synchronized
    fun subtotal(): Double = map.values.sumOf { it.unitPrice * it.quantity }

    @Synchronized
    fun deliveryTotal(): Double = map.values
        .groupBy { it.product.seller_id }
        .values
        .sumOf { group -> group.firstOrNull()?.product?.delivery_fee ?: 0.0 }

    @Synchronized
    fun grandTotal(): Double = subtotal() + deliveryTotal()

    @Synchronized
    fun itemCount(): Int = map.values.sumOf { it.quantity }

    @Synchronized
    fun quantityFor(productId: String, variantId: String?): Int =
        map[key(productId, variantId)]?.quantity ?: 0

    private fun restore() {
        val raw = prefs?.getString(ITEMS_KEY, null) ?: return
        runCatching {
            Supabase.json.decodeFromString<List<CartItem>>(raw)
        }.onSuccess { items ->
            map.clear()
            items.forEach { item -> map[item.key] = item }
        }.onFailure {
            prefs?.edit()?.remove(ITEMS_KEY)?.apply()
        }
    }

    private fun persist() {
        val storage = prefs ?: return
        runCatching { Supabase.json.encodeToString(map.values.toList()) }
            .onSuccess { storage.edit().putString(ITEMS_KEY, it).apply() }
    }

    private fun key(productId: String, variantId: String?): String =
        "$productId:${variantId ?: "base"}"
}
