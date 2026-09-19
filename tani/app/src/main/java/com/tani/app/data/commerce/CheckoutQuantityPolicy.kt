package com.tani.app.data.commerce

object CheckoutQuantityPolicy {
    const val MAX_PER_PRODUCT = 99

    data class LineKey(val productId: String, val variantId: String? = null)

    fun aggregate(items: List<Pair<LineKey, Int>>): Map<LineKey, Int> {
        require(items.isNotEmpty()) { "السلة فارغة" }
        val quantities = linkedMapOf<LineKey, Int>()
        items.forEach { (rawKey, quantity) ->
            val productId = rawKey.productId.trim()
            val variantId = rawKey.variantId?.trim()?.takeIf { it.isNotEmpty() }
            require(productId.isNotEmpty()) { "معرّف المنتج غير صحيح" }
            require(quantity >= 1) { "الكمية يجب أن تكون 1 على الأقل" }
            val key = LineKey(productId, variantId)
            val total = (quantities[key] ?: 0) + quantity
            require(total <= MAX_PER_PRODUCT) { "الحد الأقصى للخيار الواحد هو 99" }
            quantities[key] = total
        }
        return quantities
    }
}
