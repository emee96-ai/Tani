package com.tani.app.data

import com.tani.app.data.commerce.CartQuote
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class Repository {

    suspend fun login(email: String, password: String) {
        val cleanEmail = validateEmail(email)
        require(password.isNotBlank()) { "كلمة المرور مطلوبة" }

        val response: AuthResponse = Supabase.authPost(
            "token?grant_type=password",
            buildJsonObject {
                put("email", cleanEmail)
                put("password", password)
            }.toString()
        )

        val accessToken = response.access_token ?: error("تعذر تسجيل الدخول")
        val refreshToken = response.refresh_token ?: error("تعذر إنشاء جلسة تسجيل دخول آمنة")
        Supabase.saveSession(accessToken, refreshToken, response.user?.id)
        Analytics.track("login_success", screen = "auth")
    }

    suspend fun signup(
        email: String,
        password: String,
        confirmPassword: String,
        name: String,
        phone: String
    ): Boolean {
        val cleanName = name.trim()
        val cleanPhone = normalizePhone(phone)
        val cleanEmail = validateEmail(email)

        require(cleanName.length >= 2) { "أدخلي الاسم الكامل أو اسماً واضحاً" }
        require(cleanPhone.length in 9..15) { "رقم الهاتف غير صحيح" }
        validateNewPassword(password, confirmPassword)

        val body = buildJsonObject {
            put("email", cleanEmail)
            put("password", password)
            put("data", buildJsonObject {
                put("name", cleanName)
                put("phone", cleanPhone)
            })
        }

        val response: AuthResponse = Supabase.authPost("signup", body.toString())
        val uid = response.user?.id ?: error("تعذر إنشاء الحساب")

        // public.handle_new_user() في schema.sql ينشئ profile تلقائياً.
        // لا ننشئه مرة ثانية من التطبيق حتى لا يحدث تعارض بالمفتاح الأساسي.
        val accessToken = response.access_token
        val refreshToken = response.refresh_token
        if (accessToken != null && refreshToken != null) {
            Supabase.saveSession(accessToken, refreshToken, uid)
        }
        Analytics.track("signup_success", screen = "auth")

        // true = توجد جلسة كاملة قابلة للتجديد، false = تأكيد البريد مطلوب قبل الدخول.
        return accessToken != null && refreshToken != null
    }

    suspend fun requestPasswordReset(email: String) {
        val cleanEmail = validateEmail(email)
        val redirect = java.net.URLEncoder.encode(
            Supabase.PASSWORD_RESET_REDIRECT,
            Charsets.UTF_8.name()
        )

        Supabase.authPost<JsonElement>(
            "recover?redirect_to=$redirect",
            buildJsonObject { put("email", cleanEmail) }.toString()
        )
        Analytics.track("password_reset_requested", screen = "auth")
    }

    suspend fun resetPassword(
        recoveryToken: String,
        recoveryRefreshToken: String,
        password: String,
        confirmPassword: String
    ) {
        require(recoveryToken.isNotBlank()) { "رابط استعادة كلمة المرور غير صالح" }
        require(recoveryRefreshToken.isNotBlank()) { "رابط الاستعادة لا يحتوي جلسة كاملة. أطلبي رابطاً جديداً" }
        validateNewPassword(password, confirmPassword)

        val user: AuthUser = Supabase.authPut(
            "user",
            buildJsonObject { put("password", password) }.toString(),
            recoveryToken
        )

        // جلسة recovery تحتوي access + refresh token. نحفظ الاثنين حتى تبقى
        // الجلسة قابلة للتجديد بعد تغيير كلمة المرور.
        Supabase.saveSession(recoveryToken, recoveryRefreshToken, user.id)
        Analytics.track("password_reset_completed", screen = "auth")
    }

    suspend fun logout() {
        Analytics.track("logout", screen = "profile")
        Cart.clear()
        Supabase.signOut()
    }

    private fun validateEmail(email: String): String {
        val clean = email.trim().lowercase()
        val pattern = Regex("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", RegexOption.IGNORE_CASE)
        require(clean.isNotBlank()) { "البريد الإلكتروني مطلوب" }
        require(pattern.matches(clean)) { "البريد الإلكتروني غير صحيح" }
        return clean
    }

    private fun normalizePhone(phone: String): String {
        val clean = phone.trim().replace(Regex("[\\s()-]"), "")
        require(Regex("^\\+?[0-9]+$").matches(clean)) { "رقم الهاتف غير صحيح" }
        return clean
    }

    private fun validateNewPassword(password: String, confirmPassword: String) {
        require(password.length >= 8) { "كلمة المرور يجب أن تكون 8 أحرف على الأقل" }
        require(password.any { it.isLetter() } && password.any { it.isDigit() }) {
            "كلمة المرور يجب أن تحتوي على حرف ورقم على الأقل"
        }
        require(password == confirmPassword) { "كلمتا المرور غير متطابقتين" }
    }

    suspend fun accountProfile(): AccountProfile {
        val uid = Supabase.userId ?: error("تسجيل الدخول مطلوب")

        val profile = Supabase.get<List<UserProfile>>(
            "profiles",
            "select=id,name,phone,role,avatar_url,is_active&id=eq.$uid&limit=1"
        ).firstOrNull() ?: error("تعذر العثور على الملف الشخصي")

        require(profile.is_active) { "هذا الحساب غير نشط" }

        // Supabase.get() يجدد الجلسة عند الحاجة، لذلك نقرأ التوكن بعده.
        val access = Supabase.token ?: error("تسجيل الدخول مطلوب")
        val authUser: AuthUser = Supabase.authGet("user", access)
        return AccountProfile(
            profile = profile,
            email = authUser.email.orEmpty()
        )
    }

    suspend fun updateProfile(
        name: String,
        phone: String,
        avatarUrl: String? = null
    ): UserProfile {
        val uid = Supabase.userId ?: error("تسجيل الدخول مطلوب")
        val cleanName = name.trim()
        val cleanPhone = normalizePhone(phone)

        require(cleanName.length in 2..100) { "الاسم يجب أن يكون بين حرفين و100 حرف" }
        require(cleanPhone.length in 7..30) { "رقم الهاتف غير صحيح" }

        val body = buildJsonObject {
            put("name", cleanName)
            put("phone", cleanPhone)
            if (!avatarUrl.isNullOrBlank()) put("avatar_url", avatarUrl)
            put("updated_at", java.time.Instant.now().toString())
        }

        val updated = Supabase.patch<List<UserProfile>>(
            "profiles",
            "id=eq.$uid",
            body.toString()
        ).firstOrNull() ?: error("تعذر حفظ الملف الشخصي")
        Analytics.track("profile_updated", screen = "profile")
        return updated
    }

    suspend fun uploadAvatar(bytes: ByteArray): String {
        val url = Supabase.uploadAvatar(bytes)
        Analytics.track("avatar_uploaded", screen = "profile")
        return url
    }


    suspend fun categories(): List<Category> = Supabase.get(
        "categories",
        "select=id,name,image,slug,sort_order,is_active&is_active=eq.true&order=sort_order.asc,name.asc"
    )

    suspend fun marketplaceProducts(
        search: String? = null,
        categoryId: String? = null,
        minPrice: Double? = null,
        maxPrice: Double? = null,
        inStockOnly: Boolean = false,
        sort: ProductSort = ProductSort.NEWEST,
        limit: Int = 50
    ): List<ProductCard> {
        val query = mutableListOf(
            "select=*",
            "order=${sort.query}",
            "limit=${limit.coerceIn(1, 100)}"
        )

        categoryId?.takeIf { it.isNotBlank() }?.let { query += "category_id=eq.$it" }
        minPrice?.let { query += "price=gte.$it" }
        maxPrice?.let { query += "price=lte.$it" }
        if (inStockOnly) query += "stock=gt.0"

        sanitizeSearch(search)?.let { term ->
            val pattern = urlEncode("*$term*")
            query += "or=(name.ilike.$pattern,description.ilike.$pattern,store_name.ilike.$pattern,category_name.ilike.$pattern)"
        }

        val result: List<ProductCard> = Supabase.get(
            "marketplace_product_cards",
            query.joinToString("&")
        )
        return result
    }

    suspend fun productCard(productId: String): ProductCard = Supabase.get<List<ProductCard>>(
        "marketplace_product_cards",
        "select=*&id=eq.$productId&limit=1"
    ).firstOrNull() ?: error("المنتج غير متاح حالياً")

    suspend fun productImages(productId: String): List<ProductImage> = Supabase.get(
        "product_images",
        "select=id,product_id,storage_path,sort_order,is_primary&product_id=eq.$productId&order=is_primary.desc,sort_order.asc"
    )

    suspend fun productVariants(productId: String): List<ProductVariant> = Supabase.get(
        "product_variants",
        "select=id,product_id,name,sku,price,stock,is_active&product_id=eq.$productId&is_active=eq.true&order=created_at.asc"
    )

    suspend fun productReviews(productId: String, limit: Int = 20): List<Review> = Supabase.get(
        "reviews",
        "select=id,product_id,customer_id,rating,comment,created_at&product_id=eq.$productId&order=created_at.desc&limit=${limit.coerceIn(1,50)}"
    )

    suspend fun stores(search: String? = null, limit: Int = 50): List<StoreCard> {
        val query = mutableListOf(
            "select=*",
            "order=created_at.desc",
            "limit=${limit.coerceIn(1,100)}"
        )
        sanitizeSearch(search)?.let { term ->
            val pattern = urlEncode("*$term*")
            query += "or=(name.ilike.$pattern,description.ilike.$pattern,city.ilike.$pattern,area.ilike.$pattern)"
        }
        return Supabase.get("marketplace_store_cards", query.joinToString("&"))
    }

    suspend fun store(storeId: String): StoreCard = Supabase.get<List<StoreCard>>(
        "marketplace_store_cards",
        "select=*&id=eq.$storeId&limit=1"
    ).firstOrNull() ?: error("المتجر غير متاح حالياً")

    suspend fun storeBySeller(sellerId: String): StoreCard? = Supabase.get<List<StoreCard>>(
        "marketplace_store_cards",
        "select=*&seller_id=eq.$sellerId&limit=1"
    ).firstOrNull()

    suspend fun storeProducts(sellerId: String, limit: Int = 50): List<ProductCard> = Supabase.get(
        "marketplace_product_cards",
        "select=*&seller_id=eq.$sellerId&order=created_at.desc&limit=${limit.coerceIn(1,100)}"
    )

    suspend fun productDetails(productId: String): ProductDetails {
        val product = productCard(productId)
        val images = productImages(productId)
        val variants = productVariants(productId)
        val reviews = productReviews(productId)
        val related = product.category_id?.let { categoryId ->
            Supabase.get<List<ProductCard>>(
                "marketplace_product_cards",
                "select=*&category_id=eq.$categoryId&id=neq.$productId&order=average_rating.desc,created_at.desc&limit=6"
            )
        }.orEmpty()
        val store = storeBySeller(product.seller_id)
        Analytics.track("product_view", screen = "product_details", entityType = "product", entityId = productId)
        return ProductDetails(product, images, variants, reviews, related, store)
    }

    suspend fun featuredProducts(limit: Int = 6): List<ProductCard> {
        val placements: List<FeaturedPlacement> = Supabase.get(
            "featured_placements",
            "select=id,product_id,seller_id,placement,starts_at,ends_at,is_active&is_active=eq.true&product_id=not.is.null&order=created_at.desc&limit=20"
        )
        val now = java.time.Instant.now()
        val ids = placements.filter { placement ->
            val startsOk = placement.starts_at?.let { value ->
                runCatching { !java.time.Instant.parse(value).isAfter(now) }.getOrDefault(true)
            } ?: true
            val endsOk = placement.ends_at?.let { value ->
                runCatching { java.time.Instant.parse(value).isAfter(now) }.getOrDefault(true)
            } ?: true
            startsOk && endsOk
        }.mapNotNull { it.product_id }.distinct().take(limit.coerceIn(1, 20))
        if (ids.isEmpty()) return emptyList()
        val filter = ids.joinToString(",")
        return Supabase.get(
            "marketplace_product_cards",
            "select=*&id=in.($filter)&limit=${limit.coerceIn(1,20)}"
        )
    }

    suspend fun homeFeed(): HomeFeed = coroutineScope {
        val categories = async { categories() }
        val featured = async { featuredProducts(limit = 6) }
        val newest = async { marketplaceProducts(sort = ProductSort.NEWEST, limit = 6) }
        val popular = async { marketplaceProducts(sort = ProductSort.RATING, limit = 6) }
        val stores = async { stores(limit = 5) }
        HomeFeed(
            categories = categories.await(),
            featuredProducts = featured.await(),
            newestProducts = newest.await(),
            popularProducts = popular.await(),
            stores = stores.await()
        )
    }

    suspend fun searchMarketplace(search: String): Pair<List<ProductCard>, List<StoreCard>> {
        val clean = sanitizeSearch(search) ?: return emptyList<ProductCard>() to emptyList()
        Analytics.track(
            "search",
            screen = "search",
            metadata = buildJsonObject { put("query_length", clean.length) }
        )
        return marketplaceProducts(search = clean, limit = 30) to stores(search = clean, limit = 20)
    }

    fun productImageUrl(value: String?): String? {
        val clean = value?.trim().orEmpty()
        if (clean.isBlank()) return null
        if (clean.startsWith("http://") || clean.startsWith("https://")) return clean
        return "${Supabase.URL}/storage/v1/object/public/product-images/${clean.trimStart('/')}"
    }

    fun storeImageUrl(value: String?): String? {
        val clean = value?.trim().orEmpty()
        if (clean.isBlank()) return null
        if (clean.startsWith("http://") || clean.startsWith("https://")) return clean
        return "${Supabase.URL}/storage/v1/object/public/store-assets/${clean.trimStart('/')}"
    }

    private fun sanitizeSearch(value: String?): String? = value
        ?.trim()
        ?.replace(Regex("[,()\\*%]"), " ")
        ?.replace(Regex("\\s+"), " ")
        ?.take(80)
        ?.takeIf { it.length >= 2 }

    private fun urlEncode(value: String): String = java.net.URLEncoder.encode(
        value,
        Charsets.UTF_8.name()
    )

    suspend fun products(): List<Product> {
        val result: List<Product> = Supabase.get(
            "products",
            "select=*&is_active=eq.true&order=created_at.desc"
        )
        Analytics.track("product_list_loaded", screen = "products", metadata = buildJsonObject { put("count", result.size) })
        return result
    }

    suspend fun becomeSeller(storeName: String) {
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

    suspend fun seller(): Seller? {
        val uid = Supabase.userId ?: return null

        return Supabase.get<List<Seller>>(
            "sellers",
            "select=*&user_id=eq.$uid&limit=1"
        ).firstOrNull()
    }

    suspend fun sellerProducts(id: String): List<Product> =
        Supabase.get(
            "products",
            "select=*&seller_id=eq.$id&order=created_at.desc"
        )

    suspend fun addProduct(
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

    suspend fun addresses(): List<Address> {
        val uid = Supabase.userId ?: return emptyList()
        return Supabase.get(
            "addresses",
            "select=*&user_id=eq.$uid&order=is_default.desc,created_at.desc"
        )
    }

    suspend fun addAddress(
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
        require(cleanDescription.length in 2..500) { "أدخلي عنواناً واضحاً" }
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

    suspend fun updateAddress(
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
        require(cleanDescription.length in 2..500) { "أدخلي عنواناً واضحاً" }
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

    suspend fun deleteAddress(id: String) {
        Supabase.delete("addresses", "id=eq.$id")
    }

    suspend fun remoteCart(): List<RemoteCartItem> = Supabase.get(
        "my_cart_items",
        "select=*&order=updated_at.desc"
    )

    suspend fun syncCart(items: List<CartItem>): String {
        val payload = buildJsonArray {
            items.forEach { item ->
                add(buildJsonObject {
                    put("product_id", item.product.id)
                    put("quantity", item.quantity)
                })
            }
        }
        return Supabase.post(
            "rpc/sync_my_cart",
            buildJsonObject { put("p_items", payload) }.toString()
        )
    }

    suspend fun quoteCart(items: List<CartItem>): CartQuote {
        require(items.isNotEmpty()) { "السلة فارغة" }
        val payload = buildJsonArray {
            items.forEach { item ->
                add(buildJsonObject {
                    put("product_id", item.product.id)
                    put("quantity", item.quantity.coerceIn(1, 99))
                })
            }
        }

        return Supabase.post(
            "rpc/quote_cart",
            buildJsonObject { put("p_items", payload) }.toString()
        )
    }

    suspend fun checkout(
        addressId: String,
        phone: String,
        notes: String,
        items: List<CartItem>,
        idempotencyKey: String
    ): String {
        require(items.isNotEmpty()) { "السلة فارغة" }
        require(phone.trim().length in 7..30) { "رقم الهاتف غير صحيح" }
        require(idempotencyKey.length in 16..100) { "تعذر تجهيز الطلب. حاولي مرة أخرى" }

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

    suspend fun orderGroups(): List<OrderGroup> {
        val uid = Supabase.userId ?: return emptyList()
        return Supabase.get(
            "order_groups",
            "select=*&customer_id=eq.$uid&order=created_at.desc"
        )
    }

    suspend fun ordersForGroup(groupId: String): List<Order> = Supabase.get(
        "orders",
        "select=*&order_group_id=eq.$groupId&order=created_at.asc"
    )

    suspend fun orders(): List<Order> {
        val uid = Supabase.userId ?: return emptyList()
        return Supabase.get(
            "orders",
            "select=*&customer_id=eq.$uid&order=created_at.desc"
        )
    }

    suspend fun orderGroupDetails(groupId: String): OrderGroupDetails = coroutineScope {
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

    suspend fun cancelOrderGroup(groupId: String, note: String = "إلغاء بواسطة العميل"): Int {
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

    suspend fun sellerOrderItems(sellerId: String): List<OrderItem> =
        Supabase.get(
            "order_items",
            "select=*&seller_id=eq.$sellerId&order=created_at.desc"
        )


    suspend fun merchantProfile(): MerchantProfile? {
        val uid = Supabase.userId ?: return null
        return Supabase.get<List<MerchantProfile>>(
            "merchant_profiles",
            "select=*&user_id=eq.$uid&limit=1"
        ).firstOrNull()
    }

    suspend fun merchantIdentityDocuments(): List<MerchantIdentityDocument> {
        val uid = Supabase.userId ?: return emptyList()
        return Supabase.get(
            "merchant_identity_documents",
            "select=*&user_id=eq.$uid&order=created_at.desc"
        )
    }

    suspend fun requestMerchantPhoneVerification(phone: String) {
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

    suspend fun verifyMerchantPhone(phone: String, code: String) {
        val cleanPhone = normalizePhone(phone)
        val cleanCode = code.trim()
        require(Regex("^[0-9]{6}$").matches(cleanCode)) { "أدخلي رمز التحقق المكوّن من 6 أرقام" }
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

    suspend fun uploadMerchantIdentity(bytes: ByteArray, mimeType: String): String {
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

    suspend fun submitMerchantApplication(
        businessName: String,
        description: String,
        phone: String,
        whatsapp: String,
        categoryId: String,
        storeName: String,
        storeDescription: String,
        city: String,
        area: String,
        deliveryArea: String,
        deliveryFee: Double,
        estimatedMinutes: Int?,
        identityPath: String,
        documentType: String,
        acceptPolicies: Boolean
    ): String {
        require(businessName.trim().length in 2..120) { "اسم النشاط مطلوب" }
        require(storeName.trim().length in 2..120) { "اسم المتجر مطلوب" }
        require(deliveryFee >= 0) { "رسوم التوصيل غير صحيحة" }
        require(identityPath.isNotBlank()) { "ارفعي مستند الهوية أولاً" }
        require(acceptPolicies) { "يجب الموافقة على سياسات التاجر" }
        val merchantId: String = Supabase.post(
            "rpc/submit_merchant_application",
            buildJsonObject {
                put("p_business_name", businessName.trim())
                put("p_description", description.trim())
                put("p_phone", normalizePhone(phone))
                put("p_whatsapp", whatsapp.trim())
                put("p_category_id", categoryId)
                put("p_store_name", storeName.trim())
                put("p_store_description", storeDescription.trim())
                put("p_city", city.trim())
                put("p_area", area.trim())
                put("p_delivery_area", deliveryArea.trim())
                put("p_delivery_fee", deliveryFee)
                estimatedMinutes?.let { put("p_estimated_minutes", it) }
                put("p_identity_path", identityPath)
                put("p_document_type", documentType)
                put("p_accept_policies", acceptPolicies)
                put("p_policy_version", "2026-09")
            }.toString()
        )
        Analytics.track("merchant_application_submitted", screen = "merchant_onboarding", entityType = "merchant", entityId = merchantId)
        return merchantId
    }

    suspend fun merchantStore(): MerchantStore? {
        val profile = merchantProfile() ?: return null
        return Supabase.get<List<MerchantStore>>(
            "stores",
            "select=*&merchant_id=eq.${profile.id}&limit=1"
        ).firstOrNull()
    }

    suspend fun updateMerchantStore(
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

    suspend fun uploadStoreAsset(bytes: ByteArray, kind: String): String {
        val uid = Supabase.userId ?: error("تسجيل الدخول مطلوب")
        require(kind in setOf("logo", "cover")) { "نوع الصورة غير صحيح" }
        require(bytes.isNotEmpty() && bytes.size <= 5 * 1024 * 1024) { "الصورة يجب ألا تتجاوز 5 ميجابايت" }
        val path = "$uid/$kind.jpg"
        Supabase.uploadObject("store-assets", path, bytes, "image/jpeg", upsert = true)
        return path
    }

    suspend fun merchantDeliverySettings(sellerId: String): DeliverySettings? =
        Supabase.get<List<DeliverySettings>>(
            "delivery_settings",
            "select=*&seller_id=eq.$sellerId&limit=1"
        ).firstOrNull()

    suspend fun saveMerchantDeliverySettings(
        sellerId: String,
        fee: Double,
        area: String,
        estimatedMinutes: Int?,
        notes: String,
        active: Boolean
    ): DeliverySettings {
        require(fee >= 0) { "رسوم التوصيل غير صحيحة" }
        require(area.trim().length in 2..300) { "حددي منطقة التوصيل" }
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

    suspend fun createMerchantProduct(
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

    suspend fun updateMerchantProduct(
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
        return rows.firstOrNull() ?: error("تعذر تحديث المنتج")
    }

    suspend fun deactivateMerchantProduct(productId: String, active: Boolean): Product {
        val rows: List<Product> = Supabase.patch(
            "products",
            "id=eq.$productId",
            buildJsonObject {
                put("is_active", active)
                put("updated_at", java.time.Instant.now().toString())
            }.toString()
        )
        return rows.firstOrNull() ?: error("تعذر تحديث حالة المنتج")
    }


    suspend fun deleteMerchantProduct(productId: String) {
        val paths: List<String> = Supabase.post(
            "rpc/merchant_delete_product",
            buildJsonObject { put("p_product_id", productId) }.toString()
        )
        paths.forEach { path -> runCatching { Supabase.deleteStorageObject("product-images", path) } }
        Analytics.track("merchant_product_deleted", screen = "merchant_products", entityType = "product", entityId = productId)
    }

    suspend fun uploadProductImage(bytes: ByteArray, productId: String): String {
        val uid = Supabase.userId ?: error("تسجيل الدخول مطلوب")
        require(bytes.isNotEmpty() && bytes.size <= 5 * 1024 * 1024) { "الصورة يجب ألا تتجاوز 5 ميجابايت" }
        val path = "$uid/$productId/${System.currentTimeMillis()}.jpg"
        Supabase.uploadObject("product-images", path, bytes, "image/jpeg", upsert = false)
        return path
    }

    suspend fun attachProductImage(productId: String, storagePath: String, primary: Boolean = false): ProductImage {
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

    suspend fun deleteProductImage(image: ProductImage) {
        Supabase.delete("product_images", "id=eq.${image.id}")
        runCatching { Supabase.deleteStorageObject("product-images", image.storage_path) }
    }

    suspend fun addProductVariant(
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

    suspend fun updateProductVariant(
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

    suspend fun deleteProductVariant(variantId: String) {
        Supabase.delete("product_variants", "id=eq.$variantId")
    }

    suspend fun merchantOrders(sellerId: String): List<Order> = Supabase.get(
        "orders",
        "select=*&seller_id=eq.$sellerId&order=created_at.desc"
    )

    suspend fun merchantOrderItems(orderId: String): List<OrderItem> = Supabase.get(
        "order_items",
        "select=*&order_id=eq.$orderId&order=created_at.asc"
    )

    suspend fun transitionMerchantOrder(orderId: String, status: String, note: String = ""): String {
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

    suspend fun merchantDashboardSummary(): MerchantDashboardSummary = Supabase.post(
        "rpc/merchant_dashboard_summary",
        "{}"
    )

    suspend fun softDeleteAccount(): Boolean {
        val result: Boolean = Supabase.post("rpc/soft_delete_my_account", "{}")
        if (result) {
            Analytics.track("account_soft_deleted", screen = "profile")
            Cart.clear()
            Supabase.clearSession()
        }
        return result
    }

}
