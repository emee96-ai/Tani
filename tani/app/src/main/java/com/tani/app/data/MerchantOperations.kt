package com.tani.app.data

import com.tani.app.util.runCatchingCancellable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

// Merchant onboarding, store, delivery, products and orders.

private const val MERCHANT_FINISHED_ORDERS_LIMIT = 200
private const val FINAL_ORDER_STATUSES = "delivered,cancelled,rejected,failed"

suspend fun Repository.becomeSeller(storeName: String) {
    val uid = Supabase.userId ?: error("تسجيل الدخول مطلوب")
    require(storeName.isNotBlank()) { "اسم المتجر مطلوب" }

    if (seller() != null) {
        error("لديك طلب متجر بالفعل")
    }

    Supabase.post<JsonElement>(
        "sellers",
        buildJsonObject {
            put("user_id", uid)
            put("store_name", storeName.trim())
            put("verification_status", "pending")
        }.toString()
    )
}

suspend fun Repository.seller(): Seller? {
    val uid = Supabase.userId ?: return null

    return Supabase.get<List<Seller>>(
        "sellers",
        "select=*&user_id=eq.$uid&limit=1"
    ).firstOrNull()
}

suspend fun Repository.sellerProducts(id: String): List<Product> =
    Supabase.get(
        "products",
        "select=*&seller_id=eq.$id&order=created_at.desc"
    )

suspend fun Repository.addProduct(
    sellerId: String,
    name: String,
    description: String,
    price: Double,
    stock: Int
) {
    require(name.isNotBlank()) { "اسم المنتج مطلوب" }
    require(price > 0) { "السعر يجب أن يكون أكبر من صفر" }
    require(stock >= 0) { "المخزون غير صحيح" }

    Supabase.post<JsonElement>(
        "products",
        buildJsonObject {
            put("seller_id", sellerId)
            put("name", name.trim())
            put("description", description.trim())
            put("price", price)
            put("stock", stock)
            put("is_active", true)
        }.toString()
    )
    Analytics.track("merchant_product_created", screen = "seller")
}

suspend fun Repository.merchantProfile(): MerchantProfile? {
    val uid = Supabase.userId ?: return null
    return Supabase.get<List<MerchantProfile>>(
        "merchant_profiles",
        "select=*&user_id=eq.$uid&limit=1"
    ).firstOrNull()
}

suspend fun Repository.merchantIdentityDocuments(): List<MerchantIdentityDocument> {
    val uid = Supabase.userId ?: return emptyList()
    return Supabase.get(
        "merchant_identity_documents",
        "select=*&user_id=eq.$uid&order=created_at.desc"
    )
}

suspend fun Repository.requestMerchantPhoneVerification(phone: String) {
    val cleanPhone = normalizePhone(phone)
    require(cleanPhone.length in 7..30) { "رقم الهاتف غير صحيح" }
    val access = Supabase.token ?: error("تسجيل الدخول مطلوب")
    Supabase.authPut<AuthUser>(
        "user",
        buildJsonObject { put("phone", cleanPhone) }.toString(),
        access
    )
    Analytics.track("merchant_phone_verification_requested", screen = "merchant_onboarding")
}

suspend fun Repository.verifyMerchantPhone(phone: String, code: String) {
    val cleanPhone = normalizePhone(phone)
    val cleanCode = code.trim()
    require(Regex("^[0-9]{6}$").matches(cleanCode)) { "أدخل رمز التحقق المكوّن من 6 أرقام" }
    val access = Supabase.token ?: error("تسجيل الدخول مطلوب")
    val response: AuthResponse = Supabase.authPostAuthorized(
        "verify",
        buildJsonObject {
            put("type", "phone_change")
            put("phone", cleanPhone)
            put("token", cleanCode)
        }.toString(),
        access
    )
    if (!response.access_token.isNullOrBlank() && !response.refresh_token.isNullOrBlank()) {
        Supabase.saveSession(response.access_token, response.refresh_token, response.user?.id)
    }
    val currentAccess = Supabase.token ?: access
    val user: AuthUser = Supabase.authGet("user", currentAccess)
    require(user.phone?.replace(Regex("[^0-9]"), "") == cleanPhone.replace(Regex("[^0-9]"), "")) {
        "لم يكتمل التحقق من رقم الهاتف"
    }
    Analytics.track("merchant_phone_verified", screen = "merchant_onboarding")
}

suspend fun Repository.uploadMerchantIdentity(bytes: ByteArray, mimeType: String): String {
    val uid = Supabase.userId ?: error("تسجيل الدخول مطلوب")
    require(bytes.isNotEmpty()) { "ملف الهوية غير صالح" }
    require(bytes.size <= 8 * 1024 * 1024) { "ملف الهوية يجب ألا يتجاوز 8 ميجابايت" }
    val ext = when (mimeType.lowercase()) {
        "application/pdf" -> "pdf"
        "image/png" -> "png"
        "image/webp" -> "webp"
        else -> "jpg"
    }
    val safeMime = when (ext) {
        "pdf" -> "application/pdf"
        "png" -> "image/png"
        "webp" -> "image/webp"
        else -> "image/jpeg"
    }
    val path = "$uid/identity_${System.currentTimeMillis()}.$ext"
    Supabase.uploadObject("merchant-private", path, bytes, safeMime, upsert = false)
    Analytics.track("merchant_identity_uploaded", screen = "merchant_onboarding")
    return path
}

suspend fun Repository.submitMerchantApplication(
    businessName: String,
    description: String,
    phone: String,
    whatsapp: String,
    categoryId: String?,
    requestedCategory: String,
    storeName: String,
    storeDescription: String,
    city: String,
    area: String,
    deliveryZones: List<MerchantDeliveryZoneInput>,
    identityPath: String,
    documentType: String,
    acceptPolicies: Boolean
): String {
    require(businessName.trim().length in 2..120) { "اسم النشاط مطلوب" }
    require(storeName.trim().length in 2..120) { "اسم المتجر مطلوب" }
    require(categoryId != null || requestedCategory.trim().length in 2..80) { "اختر فئة أو اكتب الفئة المطلوبة" }
    require(deliveryZones.isNotEmpty()) { "أضف منطقة توصيل واحدة على الأقل" }
    require(deliveryZones.all { it.area.trim().length in 2..100 && it.fee >= 0 }) { "راجع مناطق ورسوم التوصيل" }
    require(deliveryZones.all { it.estimated_minutes == null || it.estimated_minutes in 1..1440 }) { "راجع زمن الوصول" }
    require(identityPath.isNotBlank()) { "ارفع مستند الهوية أولاً" }
    require(acceptPolicies) { "يجب الموافقة على سياسات التاجر" }
    val merchantId: String = Supabase.post(
        "rpc/submit_merchant_application",
        buildJsonObject {
            put("p_business_name", businessName.trim())
            put("p_description", description.trim())
            put("p_phone", normalizePhone(phone))
            put("p_whatsapp", whatsapp.trim())
            put("p_category_id", categoryId)
            put("p_requested_category", requestedCategory.trim())
            put("p_store_name", storeName.trim())
            put("p_store_description", storeDescription.trim())
            put("p_city", city.trim())
            put("p_area", area.trim())
            val firstZone = deliveryZones.first()
            put("p_delivery_area", firstZone.area.trim())
            put("p_delivery_fee", firstZone.fee)
            put("p_estimated_minutes", firstZone.estimated_minutes)
            put("p_delivery_zones", buildJsonArray {
                deliveryZones.forEach { zone ->
                    add(buildJsonObject {
                        put("area", zone.area.trim())
                        put("fee", zone.fee)
                        zone.estimated_minutes?.let { put("estimated_minutes", it) }
                    })
                }
            })
            put("p_identity_path", identityPath)
            put("p_document_type", documentType)
            put("p_accept_policies", acceptPolicies)
            put("p_policy_version", "2026-09")
        }.toString()
    )
    Analytics.track("merchant_application_submitted", screen = "merchant_onboarding", entityType = "merchant", entityId = merchantId)
    return merchantId
}

suspend fun Repository.merchantStore(): MerchantStore? {
    val profile = merchantProfile() ?: return null
    return Supabase.get<List<MerchantStore>>(
        "stores",
        "select=*&merchant_id=eq.${profile.id}&limit=1"
    ).firstOrNull()
}

suspend fun Repository.updateMerchantStore(
    storeId: String,
    name: String,
    description: String,
    city: String,
    area: String,
    contactPhone: String,
    whatsapp: String,
    isOpen: Boolean,
    isActive: Boolean,
    logoUrl: String? = null,
    coverUrl: String? = null
): MerchantStore {
    require(name.trim().length in 2..120) { "اسم المتجر مطلوب" }
    require(city.trim().length in 2..100) { "المدينة مطلوبة" }
    val result: List<MerchantStore> = Supabase.patch(
        "stores",
        "id=eq.$storeId",
        buildJsonObject {
            put("name", name.trim())
            put("description", description.trim())
            put("city", city.trim())
            put("area", area.trim())
            put("contact_phone", contactPhone.trim())
            put("whatsapp", whatsapp.trim())
            put("is_open", isOpen)
            put("is_active", isActive)
            logoUrl?.let { put("logo_url", it) }
            coverUrl?.let { put("cover_url", it) }
            put("updated_at", java.time.Instant.now().toString())
        }.toString()
    )
    Analytics.track("merchant_store_updated", screen = "merchant_store", entityType = "store", entityId = storeId)
    return result.firstOrNull() ?: error("تعذر تحديث المتجر")
}

suspend fun Repository.uploadStoreAsset(bytes: ByteArray, kind: String): String {
    val uid = Supabase.userId ?: error("تسجيل الدخول مطلوب")
    require(kind in setOf("logo", "cover")) { "نوع الصورة غير صحيح" }
    require(bytes.isNotEmpty() && bytes.size <= 5 * 1024 * 1024) { "الصورة يجب ألا تتجاوز 5 ميجابايت" }
    val path = "$uid/$kind.jpg"
    Supabase.uploadObject("store-assets", path, bytes, "image/jpeg", upsert = true)
    return path
}

suspend fun Repository.merchantDeliverySettings(sellerId: String): DeliverySettings? =
    Supabase.get<List<DeliverySettings>>(
        "delivery_settings",
        "select=*&seller_id=eq.$sellerId&limit=1"
    ).firstOrNull()

suspend fun Repository.saveMerchantDeliverySettings(
    sellerId: String,
    fee: Double,
    area: String,
    estimatedMinutes: Int?,
    notes: String,
    active: Boolean
): DeliverySettings {
    require(fee >= 0) { "رسوم التوصيل غير صحيحة" }
    require(area.trim().length in 2..300) { "حدد منطقة التوصيل" }
    if (estimatedMinutes != null) require(estimatedMinutes in 1..1440) { "زمن التوصيل غير صحيح" }
    val existing = merchantDeliverySettings(sellerId)
    val body = buildJsonObject {
        put("base_fee", fee)
        put("delivery_area", area.trim())
        estimatedMinutes?.let { put("estimated_minutes", it) }
        put("notes", notes.trim())
        put("is_active", active)
        put("updated_at", java.time.Instant.now().toString())
    }.toString()
    val result: List<DeliverySettings> = if (existing == null) {
        Supabase.post(
            "delivery_settings",
            buildJsonObject {
                put("seller_id", sellerId)
                put("base_fee", fee)
                put("delivery_area", area.trim())
                estimatedMinutes?.let { put("estimated_minutes", it) }
                put("notes", notes.trim())
                put("is_active", active)
            }.toString()
        )
    } else {
        Supabase.patch("delivery_settings", "id=eq.${existing.id}", body)
    }
    Analytics.track("merchant_delivery_updated", screen = "merchant_delivery")
    return result.firstOrNull() ?: error("تعذر حفظ إعدادات التوصيل")
}

suspend fun Repository.createMerchantProduct(
    sellerId: String,
    categoryId: String?,
    name: String,
    description: String,
    price: Double,
    stock: Int,
    active: Boolean = true
): Product {
    require(name.trim().length in 2..160) { "اسم المنتج مطلوب" }
    require(price >= 0) { "السعر غير صحيح" }
    require(stock >= 0) { "المخزون غير صحيح" }
    val rows: List<Product> = Supabase.post(
        "products",
        buildJsonObject {
            put("seller_id", sellerId)
            categoryId?.let { put("category_id", it) }
            put("name", name.trim())
            put("description", description.trim())
            put("price", price)
            put("stock", stock)
            put("is_active", active)
        }.toString()
    )
    val product = rows.firstOrNull() ?: error("تعذر إضافة المنتج")
    Analytics.track("merchant_product_created", screen = "merchant_products", entityType = "product", entityId = product.id)
    return product
}

suspend fun Repository.updateMerchantProduct(
    productId: String,
    categoryId: String?,
    name: String,
    description: String,
    price: Double,
    stock: Int,
    active: Boolean
): Product {
    require(name.trim().length in 2..160) { "اسم المنتج مطلوب" }
    require(price >= 0 && stock >= 0) { "السعر أو المخزون غير صحيح" }
    val rows: List<Product> = Supabase.patch(
        "products",
        "id=eq.$productId",
        buildJsonObject {
            categoryId?.let { put("category_id", it) }
            put("name", name.trim())
            put("description", description.trim())
            put("price", price)
            put("stock", stock)
            put("is_active", active)
            put("updated_at", java.time.Instant.now().toString())
        }.toString()
    )
    val row = rows.firstOrNull() ?: error("تعذر تحديث المنتج")
    check(!active || row.is_active) { "حُفظت التعديلات، لكن المنتج موقوف من الإدارة ولن يظهر للعملاء. تواصل مع الدعم لمعرفة السبب" }
    return row
}

suspend fun Repository.deactivateMerchantProduct(productId: String, active: Boolean): Product {
    val rows: List<Product> = Supabase.patch(
        "products",
        "id=eq.$productId",
        buildJsonObject {
            put("is_active", active)
            put("updated_at", java.time.Instant.now().toString())
        }.toString()
    )
    val row = rows.firstOrNull() ?: error("تعذر تحديث حالة المنتج")
    // الخادم يُبقي المنتج الذي أوقفته الإدارة مخفياً مهما طلب التاجر.
    check(!active || row.is_active) { "هذا المنتج موقوف من الإدارة ولا يمكن إظهاره. تواصل مع الدعم لمعرفة السبب" }
    return row
}

suspend fun Repository.deleteMerchantProduct(productId: String) {
    val paths: List<String> = Supabase.post(
        "rpc/merchant_delete_product",
        buildJsonObject { put("p_product_id", productId) }.toString()
    )
    paths.forEach { path -> runCatchingCancellable { Supabase.deleteStorageObject("product-images", path) } }
    Analytics.track("merchant_product_deleted", screen = "merchant_products", entityType = "product", entityId = productId)
}

suspend fun Repository.uploadProductImage(bytes: ByteArray, productId: String): String {
    val uid = Supabase.userId ?: error("تسجيل الدخول مطلوب")
    require(bytes.isNotEmpty() && bytes.size <= 5 * 1024 * 1024) { "الصورة يجب ألا تتجاوز 5 ميجابايت" }
    val path = "$uid/$productId/${System.currentTimeMillis()}.jpg"
    Supabase.uploadObject("product-images", path, bytes, "image/jpeg", upsert = false)
    return path
}

suspend fun Repository.attachProductImage(productId: String, storagePath: String, primary: Boolean = false): ProductImage {
    if (primary) {
        Supabase.patch<List<ProductImage>>(
            "product_images",
            "product_id=eq.$productId&is_primary=eq.true",
            buildJsonObject { put("is_primary", false) }.toString()
        )
    }
    val rows: List<ProductImage> = Supabase.post(
        "product_images",
        buildJsonObject {
            put("product_id", productId)
            put("storage_path", storagePath)
            put("sort_order", 0)
            put("is_primary", primary)
        }.toString()
    )
    return rows.firstOrNull() ?: error("تعذر حفظ صورة المنتج")
}

suspend fun Repository.deleteProductImage(image: ProductImage) {
    Supabase.delete("product_images", "id=eq.${image.id}")
    runCatchingCancellable { Supabase.deleteStorageObject("product-images", image.storage_path) }
}

suspend fun Repository.addProductVariant(
    productId: String,
    name: String,
    sku: String,
    price: Double?,
    stock: Int
): ProductVariant {
    require(name.trim().isNotBlank()) { "اسم الخيار مطلوب" }
    require(stock >= 0) { "المخزون غير صحيح" }
    val rows: List<ProductVariant> = Supabase.post(
        "product_variants",
        buildJsonObject {
            put("product_id", productId)
            put("name", name.trim())
            put("sku", sku.trim())
            price?.let { put("price", it) }
            put("stock", stock)
            put("is_active", true)
        }.toString()
    )
    return rows.firstOrNull() ?: error("تعذر إضافة الخيار")
}

suspend fun Repository.updateProductVariant(
    variantId: String,
    name: String,
    sku: String,
    price: Double?,
    stock: Int,
    active: Boolean
): ProductVariant {
    val rows: List<ProductVariant> = Supabase.patch(
        "product_variants",
        "id=eq.$variantId",
        buildJsonObject {
            put("name", name.trim())
            put("sku", sku.trim())
            price?.let { put("price", it) }
            put("stock", stock)
            put("is_active", active)
            put("updated_at", java.time.Instant.now().toString())
        }.toString()
    )
    return rows.firstOrNull() ?: error("تعذر تحديث الخيار")
}

suspend fun Repository.deleteProductVariant(variantId: String) {
    Supabase.delete("product_variants", "id=eq.$variantId")
}

/** كل الطلبات المفتوحة مهما كان عددها، وآخر الطلبات المنتهية فقط حتى لا يكبر التحميل مع الوقت. */
suspend fun Repository.merchantOrders(sellerId: String): List<Order> = coroutineScope {
    val open = async {
        Supabase.get<List<Order>>(
            "orders",
            "select=*&seller_id=eq.$sellerId&status=not.in.($FINAL_ORDER_STATUSES)&order=created_at.desc"
        )
    }
    val finished = async {
        Supabase.get<List<Order>>(
            "orders",
            "select=*&seller_id=eq.$sellerId&status=in.($FINAL_ORDER_STATUSES)" +
                "&order=created_at.desc&limit=$MERCHANT_FINISHED_ORDERS_LIMIT"
        )
    }
    open.await() + finished.await()
}

suspend fun Repository.merchantOrderItems(orderId: String): List<OrderItem> = Supabase.get(
    "order_items",
    "select=*&order_id=eq.$orderId&order=created_at.asc"
)

suspend fun Repository.transitionMerchantOrder(orderId: String, status: String, note: String = ""): String {
    val result: String = Supabase.post(
        "rpc/transition_order_status",
        buildJsonObject {
            put("p_order_id", orderId)
            put("p_to_status", status)
            put("p_note", note.trim())
        }.toString()
    )
    Analytics.track("merchant_order_status_changed", screen = "merchant_orders", entityType = "order", entityId = orderId,
        metadata = buildJsonObject { put("status", status) })
    return result
}

suspend fun Repository.merchantDashboardSummary(): MerchantDashboardSummary = Supabase.post(
    "rpc/merchant_dashboard_summary",
    "{}"
)
