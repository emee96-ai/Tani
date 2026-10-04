package com.tani.app.ui.home

import com.tani.app.data.homeFeed

import com.tani.app.util.runCatchingCancellable

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.tani.app.MainActivity
import com.tani.app.R
import com.tani.app.data.Analytics
import com.tani.app.data.Cart
import com.tani.app.data.HomeFeed
import com.tani.app.data.Repository
import com.tani.app.data.Supabase
import com.tani.app.data.cache.AppContentStore
import com.tani.app.data.cache.MarketplaceCache
import com.tani.app.data.network.CustomerErrorMessages
import com.tani.app.data.network.NetworkStatus
import com.tani.app.data.repository.ScaleRepository
import com.tani.app.ui.marketplace.MarketplaceUi
import com.tani.app.ui.marketplace.ProductDetailsFragment
import com.tani.app.ui.marketplace.SearchFragment
import com.tani.app.ui.marketplace.StoreDetailsFragment
import com.tani.app.ui.marketplace.StoresFragment
import com.tani.app.ui.products.ProductsFragment
import kotlinx.coroutines.launch

class HomeFragment : Fragment(R.layout.fragment_home) {
    private val repository = Repository()
    private val scaleRepository = ScaleRepository()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val search = view.findViewById<EditText>(R.id.home_search)
        val categoriesBox = view.findViewById<LinearLayout>(R.id.home_categories_box)
        val featuredTitle = view.findViewById<TextView>(R.id.home_featured_title)
        val featuredBox = view.findViewById<LinearLayout>(R.id.home_featured_products_box)
        val recommendedBox = view.findViewById<LinearLayout>(R.id.home_recommended_products_box)
        val recommendedTitle = view.findViewById<TextView>(R.id.home_recommended_title)
        val newestBox = view.findViewById<LinearLayout>(R.id.home_new_products_box)
        val popularBox = view.findViewById<LinearLayout>(R.id.home_popular_products_box)
        val storesBox = view.findViewById<LinearLayout>(R.id.home_stores_box)
        val loading = view.findViewById<TextView>(R.id.home_loading)
        val retry = view.findViewById<Button>(R.id.home_retry)
        val cache = MarketplaceCache(requireContext())

        fun openSearch() {
            val query = search.text.toString().trim()
            if (query.length < 2) {
                search.error = getString(R.string.home_1)
                return
            }
            (activity as MainActivity).show(SearchFragment.newInstance(query))
        }

        fun openProducts() {
            (activity as MainActivity).show(ProductsFragment())
        }

        search.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                openSearch(); true
            } else false
        }
        view.findViewById<View>(R.id.home_hero_cta).setOnClickListener { openProducts() }
        view.findViewById<View>(R.id.home_all_categories).setOnClickListener {
            (activity as MainActivity).nav.selectedItemId = R.id.categories
        }
        view.findViewById<Button>(R.id.home_all_products).setOnClickListener { openProducts() }
        view.findViewById<Button>(R.id.home_all_stores).setOnClickListener {
            (activity as MainActivity).show(StoresFragment())
        }

        fun renderFeed(feed: HomeFeed) {
            categoriesBox.removeAllViews()
            feed.categories.forEach { category ->
                val button = MarketplaceUi.chipButton(requireContext(), category.name) {
                    (activity as MainActivity).show(ProductsFragment.newInstance(category.id, category.name))
                }.apply {
                    background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_category_card)
                    minWidth = 0
                    minimumWidth = 0
                    minHeight = 0
                    minimumHeight = 0
                    gravity = Gravity.CENTER
                    textSize = 12.5f
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.text_dark))
                    setPadding(
                        MarketplaceUi.dp(requireContext(), 8),
                        MarketplaceUi.dp(requireContext(), 10),
                        MarketplaceUi.dp(requireContext(), 8),
                        MarketplaceUi.dp(requireContext(), 8)
                    )
                    setCompoundDrawablesRelativeWithIntrinsicBounds(0, categoryIcon(category.name), 0, 0)
                    compoundDrawablePadding = MarketplaceUi.dp(requireContext(), 7)
                }
                categoriesBox.addView(
                    button,
                    LinearLayout.LayoutParams(
                        MarketplaceUi.dp(requireContext(), 86),
                        MarketplaceUi.dp(requireContext(), 84)
                    ).apply { marginEnd = MarketplaceUi.dp(requireContext(), 10) }
                )
            }

            if (feed.featuredProducts.isNotEmpty()) {
                featuredTitle.visibility = View.VISIBLE
                featuredTitle.text = getString(R.string.home_2)
                renderProducts(featuredBox, feed.featuredProducts)
            } else {
                featuredTitle.visibility = View.GONE
                featuredBox.removeAllViews()
            }
            renderProducts(newestBox, feed.newestProducts)
            renderProducts(popularBox, feed.popularProducts)

            storesBox.removeAllViews()
            if (feed.stores.isEmpty()) {
                storesBox.addView(MarketplaceUi.empty(requireContext(), getString(R.string.home_3)))
            } else feed.stores.forEach { store ->
                MarketplaceUi.addWithSpacing(
                    storesBox,
                    MarketplaceUi.storeCard(requireContext(), viewLifecycleOwner.lifecycleScope, store) {
                        (activity as MainActivity).show(StoreDetailsFragment.newInstance(store.id))
                    },
                    requireContext()
                )
            }
        }

        fun setFeedState(message: String?, canRetry: Boolean) {
            loading.visibility = if (message == null) View.GONE else View.VISIBLE
            loading.text = message.orEmpty()
            retry.visibility = if (canRetry) View.VISIBLE else View.GONE
        }

        fun refreshHomeFeed() {
            retry.visibility = View.GONE
            viewLifecycleOwner.lifecycleScope.launch {
                val cachedFeed = AppContentStore.homeFeed ?: cache.loadHomeFeed(allowExpired = true)
                if (cachedFeed != null) {
                    renderFeed(cachedFeed)
                    setFeedState(getString(R.string.home_4), canRetry = false)
                } else {
                    setFeedState(getString(R.string.home_5), canRetry = false)
                }

                if (!NetworkStatus.isOnline(requireContext())) {
                    setFeedState(
                        if (cachedFeed != null) {
                            getString(R.string.home_6)
                        } else {
                            getString(R.string.home_7)
                        },
                        canRetry = true
                    )
                    return@launch
                }

                runCatchingCancellable { repository.homeFeed() }
                    .onSuccess { feed ->
                        cache.saveHomeFeed(feed)
                        renderFeed(feed)
                        setFeedState(null, canRetry = false)
                    }
                    .onFailure { error ->
                        val fallback = if (cachedFeed != null) {
                            getString(R.string.home_8)
                        } else {
                            getString(R.string.home_9)
                        }
                        setFeedState(CustomerErrorMessages.from(error, fallback), canRetry = true)
                    }
            }
        }

        retry.setOnClickListener { refreshHomeFeed() }
        refreshHomeFeed()

        viewLifecycleOwner.lifecycleScope.launch {
            val online = NetworkStatus.isOnline(requireContext())
            val userId = Supabase.userId?.takeIf { Supabase.hasStoredSession() }
            if (userId == null) {
                recommendedTitle.text = if (online) getString(R.string.home_10) else getString(R.string.home_11)
                val fallback = AppContentStore.products.ifEmpty {
                    cache.load(AppContentStore.CATALOG_PREVIEW_KEY, allowExpired = true)
                }.take(8)
                renderProducts(recommendedBox, fallback)
                return@launch
            }

            val preloaded = AppContentStore.recommendationsFor(userId).ifEmpty {
                cache.loadRecommendations(userId, allowExpired = true)
            }
            if (preloaded.isNotEmpty()) {
                if (!online) recommendedTitle.text = getString(R.string.home_12)
                renderProducts(recommendedBox, preloaded)
                if (!online) return@launch
            } else if (!online) {
                recommendedTitle.text = getString(R.string.home_13)
                recommendedBox.removeAllViews()
                recommendedBox.addView(MarketplaceUi.empty(requireContext(), getString(R.string.home_14)))
                return@launch
            }

            runCatchingCancellable { scaleRepository.recommendations(8) }
                .onSuccess { products ->
                    if (Supabase.userId != userId || !Supabase.hasStoredSession()) return@onSuccess
                    AppContentStore.updateRecommendations(userId, products)
                    cache.saveRecommendations(userId, products)
                    recommendedTitle.text = getString(R.string.home_15)
                    if (products.isEmpty()) {
                        recommendedBox.removeAllViews()
                        recommendedBox.addView(MarketplaceUi.empty(requireContext(), getString(R.string.home_16)))
                    } else {
                        renderProducts(recommendedBox, products)
                    }
                }
                .onFailure {
                    if (Supabase.userId != userId || !Supabase.hasStoredSession()) return@onFailure
                    val cached = cache.loadRecommendations(userId, allowExpired = true)
                    recommendedTitle.text = if (cached.isNotEmpty()) getString(R.string.home_12) else getString(R.string.home_15)
                    if (cached.isNotEmpty()) renderProducts(recommendedBox, cached)
                }
        }
    }

    private fun categoryIcon(name: String): Int = when {
        name.contains(getString(R.string.home_17), ignoreCase = true) || name.contains(getString(R.string.home_18), ignoreCase = true) -> R.drawable.ic_cat_women
        name.contains(getString(R.string.home_19), ignoreCase = true) || name.contains(getString(R.string.home_20), ignoreCase = true) -> R.drawable.ic_cat_kids
        name.contains(getString(R.string.home_21), ignoreCase = true) || name.contains(getString(R.string.home_22), ignoreCase = true) -> R.drawable.ic_cat_home
        name.contains(getString(R.string.home_23), ignoreCase = true) || name.contains(getString(R.string.home_24), ignoreCase = true) -> R.drawable.ic_cat_accessories
        name.contains(getString(R.string.home_25), ignoreCase = true) || name.contains(getString(R.string.home_26), ignoreCase = true) -> R.drawable.ic_cat_beauty
        else -> R.drawable.ic_cat_generic
    }

    private fun renderProducts(container: LinearLayout, products: List<com.tani.app.data.ProductCard>) {
        container.removeAllViews()
        if (products.isEmpty()) {
            container.addView(MarketplaceUi.empty(requireContext(), getString(R.string.home_27)))
            return
        }
        val cards = products.map { product ->
            HomeProductUi.productCard(
                context = requireContext(),
                scope = viewLifecycleOwner.lifecycleScope,
                product = product,
                onOpen = { (activity as MainActivity).show(ProductDetailsFragment.newInstance(product.id)) },
                onAdd = {
                    if (product.has_variants) {
                        (activity as MainActivity).show(ProductDetailsFragment.newInstance(product.id))
                    } else if (Cart.add(product.toProduct())) {
                        (activity as? MainActivity)?.refreshCartBadge()
                        viewLifecycleOwner.lifecycleScope.launch {
                            Analytics.track("add_to_cart", screen = "home", entityType = "product", entityId = product.id)
                        }
                        Toast.makeText(requireContext(), getString(R.string.home_28, product.name), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), getString(R.string.home_29), Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
        HomeProductUi.addGrid(container, cards, requireContext())
    }
}
