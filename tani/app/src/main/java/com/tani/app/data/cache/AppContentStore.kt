package com.tani.app.data.cache

import android.content.Context
import com.tani.app.data.AccountProfile
import com.tani.app.data.Address
import com.tani.app.data.Category
import com.tani.app.data.HomeFeed
import com.tani.app.data.MerchantProfile
import com.tani.app.data.OrderGroup
import com.tani.app.data.ProductCard
import com.tani.app.data.ProductSort
import com.tani.app.data.Repository
import com.tani.app.data.StoreCard
import com.tani.app.data.Supabase
import com.tani.app.data.growth.NotificationItem
import com.tani.app.data.growth.NotificationPreferences
import com.tani.app.data.repository.GrowthRepository
import com.tani.app.data.repository.ScaleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Session-wide stale-while-revalidate store for high-frequency screens.
 *
 * Startup work is deliberately budgeted: disk hydration is bounded and only
 * public catalogue requests participate in the splash critical path. Private
 * account data and personalized recommendations refresh in the background so
 * a slow connection cannot keep the launch screen visible for many seconds.
 */
object AppContentStore {
    const val CATALOG_PREVIEW_KEY = "catalog_preview"

    private const val STARTUP_DISK_BUDGET_MS = 450L
    private const val STARTUP_PUBLIC_REQUEST_BUDGET_MS = 2_400L

    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile var homeFeed: HomeFeed? = null
        private set
    @Volatile var products: List<ProductCard> = emptyList()
        private set
    @Volatile var stores: List<StoreCard> = emptyList()
        private set
    @Volatile var recommendations: List<ProductCard> = emptyList()
        private set

    @Volatile var productsLoaded: Boolean = false
        private set
    @Volatile var storesLoaded: Boolean = false
        private set

    @Volatile var orders: List<OrderGroup> = emptyList()
        private set
    @Volatile var account: AccountProfile? = null
        private set
    @Volatile var merchant: MerchantProfile? = null
        private set
    @Volatile var favorites: List<ProductCard> = emptyList()
        private set
    @Volatile var notifications: List<NotificationItem> = emptyList()
        private set
    @Volatile var notificationPreferences: NotificationPreferences? = null
        private set
    @Volatile var addresses: List<Address> = emptyList()
        private set

    @Volatile var ordersLoaded = false
        private set
    @Volatile var accountLoaded = false
        private set
    @Volatile var merchantLoaded = false
        private set
    @Volatile var favoritesLoaded = false
        private set
    @Volatile var notificationsLoaded = false
        private set
    @Volatile var addressesLoaded = false
        private set

    private var privateDataUserId: String? = null
    @Volatile private var recommendationsUserId: String? = null

    fun hydrateFromDisk(context: Context): Boolean = runBlocking {
        withTimeoutOrNull(STARTUP_DISK_BUDGET_MS) {
            withContext(Dispatchers.IO) {
                val cache = MarketplaceCache(context.applicationContext)
                val cachedFeed = cache.loadHomeFeed(allowExpired = true)
                val cachedProducts = cache.load(CATALOG_PREVIEW_KEY, allowExpired = true)
                val cachedStores = cache.loadStores(allowExpired = true)
                val sessionUserId = Supabase.userId?.takeIf { Supabase.hasStoredSession() }
                val cachedRecommendations = sessionUserId?.let {
                    cache.loadRecommendations(it, allowExpired = true)
                }.orEmpty()

                homeFeed = cachedFeed
                products = cachedProducts.ifEmpty {
                    cachedFeed?.let { feed ->
                        (feed.featuredProducts + feed.newestProducts + feed.popularProducts)
                            .distinctBy { it.id }
                    }.orEmpty()
                }
                stores = cachedStores.ifEmpty { cachedFeed?.stores.orEmpty() }
                recommendations = cachedRecommendations
                recommendationsUserId = sessionUserId
                productsLoaded = products.isNotEmpty()
                storesLoaded = stores.isNotEmpty()
            }
        }
        hasCoreContent()
    }

    fun hasCoreContent(): Boolean = homeFeed != null && productsLoaded && storesLoaded

    suspend fun warmUp(context: Context) = supervisorScope {
        val appContext = context.applicationContext
        val repository = Repository()
        val cache = MarketplaceCache(appContext)
        val sessionUserId = Supabase.userId?.takeIf { Supabase.hasStoredSession() }

        prepareSessionState(sessionUserId)
        refreshPersonalizedDataInBackground(appContext, sessionUserId)

        val productsRequest = async {
            boundedPublicRequest {
                repository.marketplaceProducts(limit = 24)
            }?.onSuccess { items ->
                products = items
                productsLoaded = true
                runCatching { cache.save(CATALOG_PREVIEW_KEY, items) }
            }
        }
        val storesRequest = async {
            boundedPublicRequest {
                repository.stores(limit = 100)
            }?.onSuccess { items ->
                stores = items
                storesLoaded = true
                runCatching { cache.saveStores(items) }
            }
        }
        val categoriesRequest = async {
            boundedPublicRequest { repository.categories() }
        }
        val featuredRequest = async {
            boundedPublicRequest { repository.featuredProducts(limit = 6) }
        }
        val popularRequest = async {
            boundedPublicRequest {
                repository.marketplaceProducts(sort = ProductSort.RATING, limit = 6)
            }
        }

        productsRequest.await()
        storesRequest.await()
        val categories = categoriesRequest.await()?.getOrNull()
            ?: homeFeed?.categories
            ?: emptyList<Category>()
        val featured = featuredRequest.await()?.getOrNull()
            ?: homeFeed?.featuredProducts
            ?: emptyList()
        val popular = popularRequest.await()?.getOrNull()
            ?: homeFeed?.popularProducts
            ?: products.sortedWith(
                compareByDescending<ProductCard> { it.average_rating }
                    .thenByDescending { it.review_count }
                    .thenByDescending { it.created_at.orEmpty() }
            ).take(6)

        if (categories.isNotEmpty() || productsLoaded || storesLoaded) {
            val feed = HomeFeed(
                categories = categories,
                featuredProducts = featured,
                newestProducts = products.sortedByDescending { it.created_at.orEmpty() }.take(6),
                popularProducts = popular,
                stores = stores.take(5)
            )
            homeFeed = feed
            backgroundScope.launch {
                runCatching { cache.saveHomeFeed(feed) }
            }
        }
    }

    private suspend fun <T> boundedPublicRequest(block: suspend () -> T): Result<T>? =
        withTimeoutOrNull(STARTUP_PUBLIC_REQUEST_BUDGET_MS) {
            runCatching { block() }
        }

    private fun prepareSessionState(sessionUserId: String?) {
        if (sessionUserId == null) {
            clearPrivateData()
            return
        }

        if (privateDataUserId != sessionUserId) clearPrivateData()
        privateDataUserId = sessionUserId
        if (recommendationsUserId != sessionUserId) {
            recommendations = emptyList()
            recommendationsUserId = sessionUserId
        }
    }

    private fun refreshPersonalizedDataInBackground(context: Context, sessionUserId: String?) {
        if (sessionUserId == null) return

        val uid = sessionUserId
        backgroundScope.launch {
            val cache = MarketplaceCache(context)
            if (recommendations.isEmpty()) {
                val cached = runCatching {
                    cache.loadRecommendations(uid, allowExpired = true)
                }.getOrDefault(emptyList())
                if (sessionMatches(uid) && cached.isNotEmpty()) {
                    recommendations = cached
                    recommendationsUserId = uid
                }
            }

            runCatching { ScaleRepository().recommendations(8) }
                .onSuccess { items ->
                    runCatching { cache.saveRecommendations(uid, items) }
                    if (sessionMatches(uid)) {
                        recommendations = items
                        recommendationsUserId = uid
                    }
                }
        }

        backgroundScope.launch {
            refreshPrivateData(uid)
        }
    }

    private suspend fun refreshPrivateData(uid: String) = supervisorScope {
        val repository = Repository()
        val growthRepository = GrowthRepository()

        val ordersRequest = async { runCatching { repository.orderGroups() } }
        val accountRequest = async { runCatching { repository.accountProfile() } }
        val merchantRequest = async { runCatching { repository.merchantProfile() } }
        val favoritesRequest = async { runCatching { growthRepository.favoriteProducts() } }
        val notificationsRequest = async {
            runCatching {
                growthRepository.notifications() to growthRepository.notificationPreferences()
            }
        }
        val addressesRequest = async { runCatching { repository.addresses() } }

        ordersRequest.await().onSuccess {
            if (sessionMatches(uid)) {
                orders = it
                ordersLoaded = true
            }
        }
        accountRequest.await().onSuccess {
            if (sessionMatches(uid)) {
                account = it
                accountLoaded = true
            }
        }
        merchantRequest.await().onSuccess {
            if (sessionMatches(uid)) {
                merchant = it
                merchantLoaded = true
            }
        }
        favoritesRequest.await().onSuccess {
            if (sessionMatches(uid)) {
                favorites = it
                favoritesLoaded = true
            }
        }
        notificationsRequest.await().onSuccess { (items, preferences) ->
            if (sessionMatches(uid)) {
                notifications = items
                notificationPreferences = preferences
                notificationsLoaded = true
            }
        }
        addressesRequest.await().onSuccess {
            if (sessionMatches(uid)) {
                addresses = it
                addressesLoaded = true
            }
        }
    }

    private fun sessionMatches(uid: String): Boolean =
        Supabase.hasStoredSession() && Supabase.userId == uid && privateDataUserId == uid

    fun filteredProducts(
        search: String?,
        categoryId: String?,
        minPrice: Double?,
        maxPrice: Double?,
        inStockOnly: Boolean,
        sort: ProductSort
    ): List<ProductCard> {
        val term = search.orEmpty().trim().lowercase()
        val filtered = products.asSequence()
            .filter { categoryId.isNullOrBlank() || it.category_id == categoryId }
            .filter { minPrice == null || it.price >= minPrice }
            .filter { maxPrice == null || it.price <= maxPrice }
            .filter { !inStockOnly || it.stock > 0 }
            .filter {
                term.length < 2 || listOf(it.name, it.description, it.store_name, it.category_name.orEmpty())
                    .any { value -> value.lowercase().contains(term) }
            }
            .toList()
        return when (sort) {
            ProductSort.NEWEST -> filtered.sortedByDescending { it.created_at.orEmpty() }
            ProductSort.PRICE_LOW -> filtered.sortedBy { it.price }
            ProductSort.PRICE_HIGH -> filtered.sortedByDescending { it.price }
            ProductSort.RATING -> filtered.sortedWith(
                compareByDescending<ProductCard> { it.average_rating }
                    .thenByDescending { it.review_count }
                    .thenByDescending { it.created_at.orEmpty() }
            )
        }
    }

    fun filteredStores(search: String?): List<StoreCard> {
        val term = search.orEmpty().trim().lowercase()
        if (term.length < 2) return stores
        return stores.filter {
            listOf(it.name, it.description, it.city, it.area.orEmpty())
                .any { value -> value.lowercase().contains(term) }
        }
    }

    fun recommendationsFor(userId: String?): List<ProductCard> =
        if (!userId.isNullOrBlank() && recommendationsUserId == userId) recommendations else emptyList()

    fun updateRecommendations(userId: String, value: List<ProductCard>) {
        require(userId.isNotBlank()) { "userId is required" }
        recommendationsUserId = userId
        recommendations = value
    }

    fun updateOrders(value: List<OrderGroup>) {
        orders = value
        ordersLoaded = true
    }

    fun updateProducts(value: List<ProductCard>) {
        products = value
        productsLoaded = true
    }

    fun updateStores(value: List<StoreCard>) {
        stores = value
        storesLoaded = true
    }

    fun updateAccount(value: AccountProfile) {
        account = value
        accountLoaded = true
    }

    fun updateMerchant(value: MerchantProfile?) {
        merchant = value
        merchantLoaded = true
    }

    fun updateFavorites(value: List<ProductCard>) {
        favorites = value
        favoritesLoaded = true
    }

    fun updateNotifications(items: List<NotificationItem>, preferences: NotificationPreferences?) {
        notifications = items
        notificationPreferences = preferences
        notificationsLoaded = true
    }

    fun updateAddresses(value: List<Address>) {
        addresses = value
        addressesLoaded = true
    }

    fun clearPrivateData() {
        privateDataUserId = null
        recommendationsUserId = null
        recommendations = emptyList()
        orders = emptyList()
        account = null
        merchant = null
        favorites = emptyList()
        notifications = emptyList()
        notificationPreferences = null
        addresses = emptyList()
        ordersLoaded = false
        accountLoaded = false
        merchantLoaded = false
        favoritesLoaded = false
        notificationsLoaded = false
        addressesLoaded = false
    }
}
