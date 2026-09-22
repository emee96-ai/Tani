package com.tani.app.ui.home

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

        fun openSearch() {
            val query = search.text.toString().trim()
            if (query.length < 2) {
                search.error = "اكتبي حرفين على الأقل"
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
            loading.visibility = View.GONE
            categoriesBox.removeAllViews()
            feed.categories.forEach { category ->
                val button = MarketplaceUi.chipButton(requireContext(), category.name) {
                    (activity as MainActivity).show(
                        ProductsFragment.newInstance(category.id, category.name)
                    )
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
                    setCompoundDrawablesRelativeWithIntrinsicBounds(
                        0,
                        categoryIcon(category.name),
                        0,
                        0
                    )
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
                featuredTitle.text = "ممول • ظهور مميز"
                renderProducts(featuredBox, feed.featuredProducts)
            } else {
                featuredTitle.visibility = View.GONE
                featuredBox.removeAllViews()
            }
            renderProducts(newestBox, feed.newestProducts)
            renderProducts(popularBox, feed.popularProducts)

            storesBox.removeAllViews()
            if (feed.stores.isEmpty()) {
                storesBox.addView(MarketplaceUi.empty(requireContext(), "لا توجد متاجر معتمدة حالياً"))
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

        val cache = MarketplaceCache(requireContext())
        viewLifecycleOwner.lifecycleScope.launch {
            val online = NetworkStatus.isOnline(requireContext())
            val cachedFeed = AppContentStore.homeFeed ?: cache.loadHomeFeed(allowExpired = true)
            if (cachedFeed != null) {
                renderFeed(cachedFeed)
                if (!online) {
                    loading.visibility = View.VISIBLE
                    loading.text = "بدون اتصال — نعرض آخر بيانات محفوظة"
                }
            }

            if (!online) {
                if (cachedFeed == null) {
                    loading.visibility = View.VISIBLE
                    loading.text = "لا يوجد اتصال بالإنترنت ولا توجد بيانات محفوظة بعد"
                }
                return@launch
            }

            if (cachedFeed == null) {
                runCatching { repository.homeFeed() }
                    .onSuccess { feed ->
                        cache.saveHomeFeed(feed)
                        renderFeed(feed)
                    }
                    .onFailure {
                        loading.visibility = View.VISIBLE
                        loading.text = "تعذر تحديث السوق الآن\n${it.message ?: "حاولي مرة أخرى"}"
                    }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val online = NetworkStatus.isOnline(requireContext())
            val userId = Supabase.userId?.takeIf { Supabase.hasStoredSession() }
            if (userId == null) {
                recommendedTitle.text = if (online) "منتجات مقترحة" else "منتجات مقترحة • محفوظة"
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
                if (!online) recommendedTitle.text = "مقترحة لك • محفوظة"
                renderProducts(recommendedBox, preloaded)
                return@launch
            }

            if (!online) {
                recommendedTitle.text = "مقترحة لك • بدون اتصال"
                recommendedBox.removeAllViews()
                recommendedBox.addView(MarketplaceUi.empty(requireContext(), "لا توجد اقتراحات محفوظة لهذا الحساب بعد"))
                return@launch
            }

            runCatching { scaleRepository.recommendations(8) }
                .onSuccess { products ->
                    if (Supabase.userId != userId || !Supabase.hasStoredSession()) return@onSuccess
                    AppContentStore.updateRecommendations(userId, products)
                    cache.saveRecommendations(userId, products)
                    if (products.isEmpty()) {
                        recommendedTitle.text = "مقترحة لك"
                        recommendedBox.removeAllViews()
                        recommendedBox.addView(MarketplaceUi.empty(requireContext(), "ستتحسن الاقتراحات مع استخدامك لتاني"))
                    } else {
                        renderProducts(recommendedBox, products)
                    }
                }
                .onFailure {
                    if (Supabase.userId != userId || !Supabase.hasStoredSession()) return@onFailure
                    val cached = cache.loadRecommendations(userId, allowExpired = true)
                    recommendedTitle.text = if (cached.isNotEmpty()) "مقترحة لك • محفوظة" else "مقترحة لك"
                    if (cached.isNotEmpty()) renderProducts(recommendedBox, cached)
                }
        }
    }

    private fun categoryIcon(name: String): Int = when {
        name.contains("نساء", ignoreCase = true) || name.contains("نسائي", ignoreCase = true) -> R.drawable.ic_cat_women
        name.contains("طفل", ignoreCase = true) || name.contains("أطفال", ignoreCase = true) -> R.drawable.ic_cat_kids
        name.contains("بيت", ignoreCase = true) || name.contains("منزل", ignoreCase = true) -> R.drawable.ic_cat_home
        name.contains("إكسس", ignoreCase = true) || name.contains("اكسس", ignoreCase = true) -> R.drawable.ic_cat_accessories
        name.contains("جمال", ignoreCase = true) || name.contains("عناية", ignoreCase = true) -> R.drawable.ic_cat_beauty
        else -> R.drawable.ic_cat_generic
    }

    private fun renderProducts(container: LinearLayout, products: List<com.tani.app.data.ProductCard>) {
        container.removeAllViews()
        if (products.isEmpty()) {
            container.addView(MarketplaceUi.empty(requireContext(), "لا توجد منتجات متاحة حالياً"))
            return
        }
        val cards = products.map { product ->
            HomeProductUi.productCard(
                requireContext(),
                viewLifecycleOwner.lifecycleScope,
                product
            ) {
                (activity as MainActivity).show(ProductDetailsFragment.newInstance(product.id))
            }
        }
        HomeProductUi.addMasonryGrid(container, cards, requireContext())
    }
}
