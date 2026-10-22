package com.tani.app.data

import com.tani.app.util.runCatchingCancellable
import com.tani.app.data.catalog.CatalogPage
import com.tani.app.data.catalog.CatalogPagination
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

// Browsing: categories, products, stores and the home feed.

suspend fun Repository.categories(): List<Category> = Supabase.get(
    "categories",
    "select=id,name,image,slug,sort_order,is_active&is_active=eq.true&order=sort_order.asc,name.asc"
)

suspend fun Repository.marketplaceProducts(
    search: String? = null,
    categoryId: String? = null,
    minPrice: Double? = null,
    maxPrice: Double? = null,
    inStockOnly: Boolean = false,
    sort: ProductSort = ProductSort.NEWEST,
    limit: Int = 50,
    sellerId: String? = null
): List<ProductCard> = marketplaceProductPage(
    search = search,
    categoryId = categoryId,
    minPrice = minPrice,
    maxPrice = maxPrice,
    inStockOnly = inStockOnly,
    sort = sort,
    pageSize = limit,
    offset = 0,
    sellerId = sellerId
).items

suspend fun Repository.marketplaceProductPage(
    search: String? = null,
    categoryId: String? = null,
    minPrice: Double? = null,
    maxPrice: Double? = null,
    inStockOnly: Boolean = false,
    sort: ProductSort = ProductSort.NEWEST,
    pageSize: Int = CatalogPagination.DEFAULT_PAGE_SIZE,
    offset: Int = 0,
    sellerId: String? = null
): CatalogPage<ProductCard> {
    require(offset >= 0) { "إزاحة الصفحة غير صحيحة" }
    val size = CatalogPagination.normalizedPageSize(pageSize)
    val query = mutableListOf(
        "select=*",
        "order=${sort.query}",
        "limit=${CatalogPagination.requestLimit(size)}",
        "offset=$offset"
    )

    categoryId?.takeIf { it.isNotBlank() }?.let { query += "category_id=eq.$it" }
    sellerId?.takeIf { it.isNotBlank() }?.let { query += "seller_id=eq.$it" }
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
    return CatalogPagination.page(result, offset, size)
}

suspend fun Repository.productCard(productId: String): ProductCard = Supabase.get<List<ProductCard>>(
    "marketplace_product_cards",
    "select=*&id=eq.$productId&limit=1"
).firstOrNull() ?: error("المنتج غير متاح حالياً")

suspend fun Repository.productImages(productId: String): List<ProductImage> = Supabase.get(
    "product_images",
    "select=id,product_id,storage_path,sort_order,is_primary&product_id=eq.$productId&order=is_primary.desc,sort_order.asc"
)

suspend fun Repository.productVariants(productId: String): List<ProductVariant> = Supabase.get(
    "product_variants",
    "select=id,product_id,name,sku,price,stock,attributes,is_active&product_id=eq.$productId&is_active=eq.true&order=created_at.asc"
)

suspend fun Repository.productReviews(productId: String, limit: Int = 20): List<Review> = Supabase.get(
    "reviews",
    "select=id,product_id,customer_id,rating,comment,created_at&product_id=eq.$productId&order=created_at.desc&limit=${limit.coerceIn(1,50)}"
)

suspend fun Repository.stores(search: String? = null, limit: Int = 50): List<StoreCard> {
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

suspend fun Repository.store(storeId: String): StoreCard = Supabase.get<List<StoreCard>>(
    "marketplace_store_cards",
    "select=*&id=eq.$storeId&limit=1"
).firstOrNull() ?: error("المتجر غير متاح حالياً")

suspend fun Repository.storeBySeller(sellerId: String): StoreCard? = Supabase.get<List<StoreCard>>(
    "marketplace_store_cards",
    "select=*&seller_id=eq.$sellerId&limit=1"
).firstOrNull()

suspend fun Repository.storeProducts(sellerId: String, limit: Int = 50): List<ProductCard> =
    marketplaceProducts(sellerId = sellerId, limit = limit)

suspend fun Repository.storeProductPage(
    sellerId: String,
    pageSize: Int = CatalogPagination.DEFAULT_PAGE_SIZE,
    offset: Int = 0
): CatalogPage<ProductCard> = marketplaceProductPage(
    sellerId = sellerId,
    pageSize = pageSize,
    offset = offset
)

suspend fun Repository.productDetails(productId: String): ProductDetails = coroutineScope {
    val product = productCard(productId)
    val imagesDeferred = async { productImages(productId) }
    val variantsDeferred = async { productVariants(productId) }
    val reviewsDeferred = async { productReviews(productId) }
    val relatedDeferred = async {
        product.category_id?.let { categoryId ->
            Supabase.get<List<ProductCard>>(
                "marketplace_product_cards",
                "select=*&category_id=eq.$categoryId&id=neq.$productId&order=average_rating.desc,created_at.desc&limit=6"
            )
        }.orEmpty()
    }
    val storeDeferred = async { storeBySeller(product.seller_id) }

    Analytics.track("product_view", screen = "product_details", entityType = "product", entityId = productId)
    ProductDetails(
        product = product,
        images = imagesDeferred.await(),
        variants = variantsDeferred.await(),
        reviews = reviewsDeferred.await(),
        related = relatedDeferred.await(),
        store = storeDeferred.await()
    )
}

suspend fun Repository.featuredProducts(limit: Int = 6): List<ProductCard> {
    val placements: List<FeaturedPlacement> = Supabase.get(
        "featured_placements",
        "select=id,product_id,seller_id,placement,starts_at,ends_at,is_active&is_active=eq.true&product_id=not.is.null&order=created_at.desc&limit=20"
    )
    val now = java.time.Instant.now()
    val ids = placements.filter { placement ->
        val startsOk = placement.starts_at?.let { value ->
            runCatchingCancellable { !java.time.Instant.parse(value).isAfter(now) }.getOrDefault(true)
        } ?: true
        val endsOk = placement.ends_at?.let { value ->
            runCatchingCancellable { java.time.Instant.parse(value).isAfter(now) }.getOrDefault(true)
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

suspend fun Repository.homeFeed(): HomeFeed = coroutineScope {
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

suspend fun Repository.searchMarketplace(search: String): Pair<List<ProductCard>, List<StoreCard>> {
    val clean = sanitizeSearch(search) ?: return emptyList<ProductCard>() to emptyList()
    Analytics.track(
        "search",
        screen = "search",
        metadata = buildJsonObject { put("query_length", clean.length) }
    )
    return marketplaceProducts(search = clean, limit = 30) to stores(search = clean, limit = 20)
}

fun Repository.productImageUrl(value: String?): String? {
    val clean = value?.trim().orEmpty()
    if (clean.isBlank()) return null
    if (clean.startsWith("http://") || clean.startsWith("https://")) return clean
    return "${Supabase.URL}/storage/v1/object/public/product-images/${clean.trimStart('/')}"
}

fun Repository.storeImageUrl(value: String?): String? {
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
