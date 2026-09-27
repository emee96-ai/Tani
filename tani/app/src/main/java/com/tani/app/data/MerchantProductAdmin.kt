package com.tani.app.data

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

suspend fun Repository.merchantProductVariants(productId: String): List<ProductVariant> = Supabase.get(
    "product_variants",
    "select=id,product_id,name,sku,price,stock,attributes,is_active&product_id=eq.$productId&order=is_active.desc,created_at.asc"
)

suspend fun Repository.setPrimaryProductImage(productId: String, imageId: String) {
    Supabase.patch<List<ProductImage>>(
        "product_images",
        "product_id=eq.$productId&is_primary=eq.true",
        buildJsonObject { put("is_primary", false) }.toString()
    )
    val updated = Supabase.patch<List<ProductImage>>(
        "product_images",
        "id=eq.$imageId&product_id=eq.$productId",
        buildJsonObject { put("is_primary", true) }.toString()
    )
    require(updated.isNotEmpty()) { "تعذر تعيين الصورة الرئيسية" }
}
