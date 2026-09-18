package com.tani.app.data.commerce

object CheckoutQuantityPolicy {
    const val MAX_PER_PRODUCT = 99

    fun aggregate(items: List<Pair<String, Int>>): Map<String, Int> {
        require(items.isNotEmpty()) { "السلة فارغة" }
        val quantities = linkedMapOf<String, Int>()
        items.forEach { (rawProductId, quantity) ->
            val productId = rawProductId.trim()
            require(productId.isNotEmpty()) { "معرّف المنتج غير صحيح" }
            require(quantity >= 1) { "الكمية يجب أن تكون 1 على الأقل" }
            val total = (quantities[productId] ?: 0) + quantity
            require(total <= MAX_PER_PRODUCT) { "الحد الأقصى للمنتج الواحد هو 99" }
            quantities[productId] = total
        }
        return quantities
    }
}
