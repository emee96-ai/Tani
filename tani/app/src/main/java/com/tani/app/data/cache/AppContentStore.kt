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
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope

/**
 * A session-wide, stale-while-revalidate store for screens that users open often.
 * Public catalogue data is also persisted by [MarketplaceCache], while private
 * account data deliberately stays in memory only.
 */
object AppContentStore {
    const val CATALOG_PREVIEW_KEY = "catalog_preview"

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

    fun hydrateFromDisk(context: Context): Boolean {
        val cache = MarketplaceCache(context)
        homeFeed = cache.loadHomeFeed(allowExpired = true)
        products = cache.load(CATALOG_PREVIEW_KEY, allowExpired = true)
        stores = cache.loadStores(allowExpired = true)
        recommendations = cache.load("home_recommendations", allowExpired = true)
        productsLoaded = products.isNotEmpty()
        storesLoaded = stores.isNotEmpty()
        return hasCoreContent()
    }

    fun hasCoreContent(): Boolean = homeFeed != null && productsLoaded && storesLoaded

    suspend fun warmUp(context: Context) = supervisorScope {
        val repository = Repository()
        val growthRepository = GrowthRepository()
        val scaleRepository = ScaleRepository()
        val cache = MarketplaceCache(context)

        val productsRequest = async {
            runCatching { repository.marketplaceProducts(limit = 24) }
                .onSuccess {
                    products = it
                    productsLoaded = true
                    cache.save(CATALOG_PREVIEW_KEY, it)
                }
        }
        val storesRequest = async {
            runCatching { repository.stores(limit = 100) }
                .onSuccess {
                    stores = it
                    storesLoaded = true
                    cache.saveStores(it)
                }
        }
        val categoriesRequest = async { runCatching { repository.categories() } }
        val featuredRequest = async { runCatching { repository.featuredProducts(limit = 6) } }
        val popularRequest = async {
            runCatching { repository.marketplaceProducts(sort = ProductSort.RATING, limit = 6) }
        }
        val recommendationsRequest = async {
            if (!Supabase.hasStoredSession()) return@async
            runCatching { scaleRepository.recommendations(8) }
                .onSuccess {
                    recommendations = it
                    cache.save("home_recommendations", it)
                }
        }

        val userId = Supabase.userId
        if (Supabase.hasStoredSession() && !userId.isNullOrBlank()) {
            if (privateDataUserId != userId) clearPrivateData()
            privateDataUserId = userId

            val ordersRequest = async {
                runCatching { repository.orderGroups() }.onSuccess {
                    orders = it
                    ordersLoaded = true
                }
            }
            val accountRequest = async {
                runCatching { repository.accountProfile() }.onSuccess {
                    account = it
                    accountLoaded = true
                }
            }
            val merchantRequest = async {
                runCatching { repository.merchantProfile() }.onSuccess {
                    merchant = it
                    merchantLoaded = true
                }
            }
            val favoritesRequest = async {
                runCatching { growthRepository.favoriteProducts() }.onSuccess {
                    favorites = it
                    favoritesLoaded = true
                }
            }
            val notificationsRequest = async {
                runCatching {
                    growthRepository.notifications() to growthRepository.notificationPreferences()
                }.onSuccess { (items, preferences) ->
                    notifications = items
                    notificationPreferences = preferences
                    notificationsLoaded = true
                }
            }
            val addressesRequest = async {
                runCatching { repository.addresses() }.onSuccess {
                    addresses = it
                    addressesLoaded = true
                }
            }
            ordersRequest.await()
            accountRequest.await()
            merchantRequest.await()
            favoritesRequest.await()
            notificationsRequest.await()
            addressesRequest.await()
        } else {
            clearPrivateData()
        }

        productsRequest.await()
        storesRequest.await()
        recommendationsRequest.await()
        val categories = categoriesRequest.await().getOrNull()
            ?: homeFeed?.categories
            ?: emptyList<Category>()
        val featured = featuredRequest.await().getOrNull()
            ?: homeFeed?.featuredProducts
            ?: emptyList()
        val popular = popularRequest.await().getOrNull()
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
            cache.saveHomeFeed(feed)
        }
    }

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
