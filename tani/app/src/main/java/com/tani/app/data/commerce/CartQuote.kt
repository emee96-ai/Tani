package com.tani.app.data.commerce

import kotlinx.serialization.Serializable

@Serializable
data class CartQuote(
    val subtotal: Double,
    val delivery_total: Double,
    val discount_total: Double = 0.0,
    val grand_total: Double,
    val items: List<CartQuoteItem> = emptyList(),
    val deliveries: List<CartQuoteDelivery> = emptyList(),
    val warnings: List<String> = emptyList(),
    val quote_token: String
)

@Serializable
data class CartQuoteItem(
    val product_id: String,
    val variant_id: String? = null,
    val variant_name: String? = null,
    val seller_id: String,
    val name: String,
    val unit_price: Double,
    val quantity: Int,
    val stock: Int,
    val available: Boolean,
    val line_total: Double
)

@Serializable
data class CartQuoteDelivery(
    val seller_id: String,
    val store_name: String,
    val subtotal: Double,
    val fee: Double,
    val area: String? = null
)
