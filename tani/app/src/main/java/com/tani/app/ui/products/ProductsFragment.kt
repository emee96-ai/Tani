package com.tani.app.ui.products

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tani.app.MainActivity
import com.tani.app.R
import com.tani.app.data.Analytics
import com.tani.app.data.Cart
import com.tani.app.data.ProductCard
import com.tani.app.data.ProductSort
import com.tani.app.data.Repository
import com.tani.app.data.cache.AppContentStore
import com.tani.app.data.cache.MarketplaceCache
import com.tani.app.data.catalog.CatalogPagination
import com.tani.app.ui.marketplace.ProductDetailsFragment
import com.tani.app.ui.marketplace.ProductListAdapter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ProductsFragment : Fragment(R.layout.fragment_products) {
    private val repository = Repository()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val title = view.findViewById<TextView>(R.id.products_title)
        val search = view.findViewById<EditText>(R.id.products_search)
        val minPrice = view.findViewById<EditText>(R.id.products_min_price)
        val maxPrice = view.findViewById<EditText>(R.id.products_max_price)
        val inStock = view.findViewById<CheckBox>(R.id.products_in_stock)
        val sort = view.findViewById<Spinner>(R.id.products_sort)
        val filtersPanel = view.findViewById<LinearLayout>(R.id.products_filters_panel)
        val filterSummary = view.findViewById<TextView>(R.id.products_filter_summary)
        val status = view.findViewById<TextView>(R.id.products_loading)
        val loadMore = view.findViewById<Button>(R.id.products_load_more)
        val list = view.findViewById<RecyclerView>(R.id.products_list)

        val categoryId = arguments?.getString(ARG_CATEGORY_ID)
        val categoryName = arguments?.getString(ARG_CATEGORY_NAME)
        val sellerId = arguments?.getString(ARG_SELLER_ID)
        val sellerName = arguments?.getString(ARG_SELLER_NAME)
        val initialSearch = arguments?.getString(ARG_SEARCH)
        title.text = when {
            !categoryName.isNullOrBlank() -> categoryName
            !sellerName.isNullOrBlank() -> "منتجات $sellerName"
            else -> "المنتجات"
        }
        if (!initialSearch.isNullOrBlank()) search.setText(initialSearch)

        val sortLabels = listOf("الأحدث", "السعر: الأقل أولاً", "السعر: الأعلى أولاً", "الأعلى تقييماً")
        sort.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, sortLabels)

        val adapter = ProductListAdapter(
            requireContext(),
            viewLifecycleOwner.lifecycleScope,
            onOpen = { product -> (activity as MainActivity).show(ProductDetailsFragment.newInstance(product.id)) },
            onAdd = { product -> addToCart(product) }
        )
        val layoutManager = GridLayoutManager(requireContext(), 2)
        list.layoutManager = layoutManager
        list.adapter = adapter

        val loaded = mutableListOf<ProductCard>()
        var nextOffset = 0
        var hasMore = true
        var isLoading = false
        var generation = 0
        var requestJob: Job? = null

        fun currentSort(): ProductSort = when (sort.selectedItemPosition) {
            1 -> ProductSort.PRICE_LOW
            2 -> ProductSort.PRICE_HIGH
            3 -> ProductSort.RATING
            else -> ProductSort.NEWEST
        }

        fun updateSummary() {
            val parts = mutableListOf(sortLabels[sort.selectedItemPosition.coerceIn(sortLabels.indices)])
            minPrice.text.toString().toDoubleOrNull()?.let { parts += "من ${it.toLong()}" }
            maxPrice.text.toString().toDoubleOrNull()?.let { parts += "حتى ${it.toLong()}" }
            if (inStock.isChecked) parts += "المتوفر فقط"
            filterSummary.text = parts.joinToString(" • ")
        }

        fun renderPageState(message: String? = null) {
            status.visibility = View.VISIBLE
            status.text = message ?: when {
                isLoading && loaded.isEmpty() -> "جاري تحميل المنتجات..."
                isLoading -> "جاري تحميل المزيد..."
                loaded.isEmpty() -> "ما لقينا نتائج مطابقة. جرّبي تغيير البحث أو الفلاتر."
                else -> "تم عرض ${loaded.size} منتج"
            }
            loadMore.visibility = if (loaded.isNotEmpty() && hasMore) View.VISIBLE else View.GONE
            loadMore.isEnabled = hasMore && !isLoading
            loadMore.text = if (isLoading) "جاري التحميل..." else "عرض المزيد"
        }

        fun loadPage(reset: Boolean) {
            if (!reset && (isLoading || !hasMore)) return
            if (reset) {
                generation++
                requestJob?.cancel()
                loaded.clear()
                adapter.submitList(emptyList())
                nextOffset = 0
                hasMore = true
            }

            val requestGeneration = generation
            val requestOffset = nextOffset
            val searchValue = search.text.toString()
            val minValue = minPrice.text.toString().toDoubleOrNull()
            val maxValue = maxPrice.text.toString().toDoubleOrNull()
            val stockOnly = inStock.isChecked
            val sortValue = currentSort()
            isLoading = true
            updateSummary()
            renderPageState()

            requestJob = viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val page = repository.marketplaceProductPage(
                        search = searchValue,
                        categoryId = categoryId,
                        minPrice = minValue,
                        maxPrice = maxValue,
                        inStockOnly = stockOnly,
                        sort = sortValue,
                        pageSize = PAGE_SIZE,
                        offset = requestOffset,
                        sellerId = sellerId
                    )
                    if (requestGeneration != generation) return@launch

                    val ids = loaded.mapTo(mutableSetOf()) { it.id }
                    page.items.forEach { product -> if (ids.add(product.id)) loaded += product }
                    nextOffset = page.nextOffset
                    hasMore = page.hasMore

                    if (
                        requestOffset == 0 && searchValue.isBlank() && categoryId.isNullOrBlank() &&
                        sellerId.isNullOrBlank() && minValue == null && maxValue == null &&
                        !stockOnly && sortValue == ProductSort.NEWEST
                    ) {
                        AppContentStore.updateProducts(page.items)
                        MarketplaceCache(requireContext()).save(AppContentStore.CATALOG_PREVIEW_KEY, page.items)
                    }
                    adapter.submitList(loaded.toList())
                    isLoading = false
                    renderPageState()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    if (requestGeneration != generation) return@launch
                    isLoading = false
                    if (reset && sellerId.isNullOrBlank() && AppContentStore.productsLoaded) {
                        val cached = AppContentStore.filteredProducts(
                            search = searchValue,
                            categoryId = categoryId,
                            minPrice = minValue,
                            maxPrice = maxValue,
                            inStockOnly = stockOnly,
                            sort = sortValue
                        ).take(PAGE_SIZE)
                        if (cached.isNotEmpty()) {
                            loaded.clear()
                            loaded.addAll(cached)
                            hasMore = false
                            adapter.submitList(loaded.toList())
                            renderPageState("تم عرض ${loaded.size} منتج من النسخة المحفوظة")
                            return@launch
                        }
                    }
                    renderPageState(
                        if (loaded.isEmpty()) "تعذر تحميل المنتجات\n${error.message ?: "حاولي مرة أخرى"}"
                        else "تعذر تحميل المزيد. اضغطي «عرض المزيد» للمحاولة مجددًا."
                    )
                }
            }
        }

        list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0 || isLoading || !hasMore) return
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                if (lastVisible >= adapter.itemCount - LOAD_AHEAD_ITEMS) loadPage(reset = false)
            }
        })
        loadMore.setOnClickListener { loadPage(reset = false) }

        view.findViewById<Button>(R.id.products_filter_toggle).setOnClickListener {
            filtersPanel.visibility = if (filtersPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        view.findViewById<Button>(R.id.products_apply).setOnClickListener {
            filtersPanel.visibility = View.GONE
            loadPage(reset = true)
        }
        view.findViewById<Button>(R.id.products_clear).setOnClickListener {
            search.text.clear()
            minPrice.text.clear()
            maxPrice.text.clear()
            inStock.isChecked = false
            sort.setSelection(0)
            filtersPanel.visibility = View.GONE
            loadPage(reset = true)
        }
        search.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                loadPage(reset = true)
                true
            } else false
        }
        loadPage(reset = true)
    }

    private fun addToCart(product: ProductCard) {
        if (product.has_variants) {
            Toast.makeText(requireContext(), "اختاري المقاس أو اللون أولاً", Toast.LENGTH_SHORT).show()
            (activity as MainActivity).show(ProductDetailsFragment.newInstance(product.id))
        } else if (Cart.add(product.toProduct())) {
            (activity as? MainActivity)?.refreshCartBadge()
            viewLifecycleOwner.lifecycleScope.launch {
                Analytics.track("add_to_cart", screen = "products", entityType = "product", entityId = product.id)
            }
            Toast.makeText(requireContext(), "تمت إضافة ${product.name} للسلة", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "تعذر إضافة كمية إضافية", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val ARG_CATEGORY_ID = "category_id"
        private const val ARG_CATEGORY_NAME = "category_name"
        private const val ARG_SEARCH = "search"
        private const val ARG_SELLER_ID = "seller_id"
        private const val ARG_SELLER_NAME = "seller_name"
        private const val PAGE_SIZE = CatalogPagination.DEFAULT_PAGE_SIZE
        private const val LOAD_AHEAD_ITEMS = 6

        fun newInstance(categoryId: String, categoryName: String): ProductsFragment =
            ProductsFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CATEGORY_ID, categoryId)
                    putString(ARG_CATEGORY_NAME, categoryName)
                }
            }

        fun newSearchInstance(search: String): ProductsFragment = ProductsFragment().apply {
            arguments = Bundle().apply { putString(ARG_SEARCH, search) }
        }

        fun newStoreInstance(sellerId: String, sellerName: String): ProductsFragment =
            ProductsFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_SELLER_ID, sellerId)
                    putString(ARG_SELLER_NAME, sellerName)
                }
            }
    }
}
