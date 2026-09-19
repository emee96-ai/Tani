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
import com.tani.app.data.ProductSort
import com.tani.app.data.Repository
import com.tani.app.data.cache.AppContentStore
import com.tani.app.data.cache.MarketplaceCache
import com.tani.app.ui.marketplace.ProductDetailsFragment
import com.tani.app.ui.marketplace.ProductListAdapter
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
        val loading = view.findViewById<TextView>(R.id.products_loading)
        val list = view.findViewById<RecyclerView>(R.id.products_list)

        val categoryId = arguments?.getString(ARG_CATEGORY_ID)
        val categoryName = arguments?.getString(ARG_CATEGORY_NAME)
        val initialSearch = arguments?.getString(ARG_SEARCH)
        if (!categoryName.isNullOrBlank()) title.text = categoryName
        if (!initialSearch.isNullOrBlank()) search.setText(initialSearch)

        val sortLabels = listOf("الأحدث", "السعر: الأقل أولاً", "السعر: الأعلى أولاً", "الأعلى تقييماً")
        sort.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, sortLabels)

        val adapter = ProductListAdapter(
            requireContext(),
            viewLifecycleOwner.lifecycleScope,
            onOpen = { product -> (activity as MainActivity).show(ProductDetailsFragment.newInstance(product.id)) },
            onAdd = { product ->
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
        )
        list.layoutManager = GridLayoutManager(requireContext(), 2)
        list.adapter = adapter

        fun updateSummary() {
            val parts = mutableListOf<String>()
            parts += sortLabels[sort.selectedItemPosition.coerceIn(sortLabels.indices)]
            minPrice.text.toString().toDoubleOrNull()?.let { parts += "من ${it.toLong()}" }
            maxPrice.text.toString().toDoubleOrNull()?.let { parts += "حتى ${it.toLong()}" }
            if (inStock.isChecked) parts += "المتوفر فقط"
            filterSummary.text = parts.joinToString(" • ")
        }

        fun load() {
            loading.visibility = View.VISIBLE
            loading.text = "جاري تحميل المنتجات..."
            viewLifecycleOwner.lifecycleScope.launch {
                val sortValue = when (sort.selectedItemPosition) {
                    1 -> ProductSort.PRICE_LOW
                    2 -> ProductSort.PRICE_HIGH
                    3 -> ProductSort.RATING
                    else -> ProductSort.NEWEST
                }
                if (AppContentStore.productsLoaded) {
                    val products = AppContentStore.filteredProducts(
                        search = search.text.toString(),
                        categoryId = categoryId,
                        minPrice = minPrice.text.toString().toDoubleOrNull(),
                        maxPrice = maxPrice.text.toString().toDoubleOrNull(),
                        inStockOnly = inStock.isChecked,
                        sort = sortValue
                    )
                    adapter.submitList(products)
                    updateSummary()
                    loading.visibility = if (products.isEmpty()) View.VISIBLE else View.GONE
                    if (products.isEmpty()) {
                        loading.text = "ما لقينا نتائج مطابقة. جرّبي تغيير البحث أو الفلاتر."
                    }
                    return@launch
                }
                runCatching {
                    repository.marketplaceProducts(
                        search = search.text.toString(),
                        categoryId = categoryId,
                        minPrice = minPrice.text.toString().toDoubleOrNull(),
                        maxPrice = maxPrice.text.toString().toDoubleOrNull(),
                        inStockOnly = inStock.isChecked,
                        sort = sortValue,
                        limit = 100
                    )
                }.onSuccess { products ->
                    val isUnfilteredList = search.text.isBlank() && categoryId.isNullOrBlank() &&
                        minPrice.text.isBlank() && maxPrice.text.isBlank() && !inStock.isChecked &&
                        sortValue == ProductSort.NEWEST
                    if (isUnfilteredList) {
                        AppContentStore.updateProducts(products)
                        MarketplaceCache(requireContext()).save(AppContentStore.ALL_PRODUCTS_KEY, products)
                    }
                    adapter.submitList(products)
                    updateSummary()
                    if (products.isEmpty()) {
                        loading.visibility = View.VISIBLE
                        loading.text = "ما لقينا نتائج مطابقة. جرّبي تغيير البحث أو الفلاتر."
                    } else {
                        loading.visibility = View.GONE
                    }
                }.onFailure {
                    adapter.submitList(emptyList())
                    loading.visibility = View.VISIBLE
                    loading.text = "تعذر تحميل المنتجات\n${it.message ?: "حاولي مرة أخرى"}"
                }
            }
        }

        view.findViewById<Button>(R.id.products_filter_toggle).setOnClickListener {
            filtersPanel.visibility = if (filtersPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        view.findViewById<Button>(R.id.products_apply).setOnClickListener {
            filtersPanel.visibility = View.GONE
            load()
        }
        view.findViewById<Button>(R.id.products_clear).setOnClickListener {
            search.text.clear()
            minPrice.text.clear()
            maxPrice.text.clear()
            inStock.isChecked = false
            sort.setSelection(0)
            filtersPanel.visibility = View.GONE
            load()
        }
        search.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                load(); true
            } else false
        }
        load()
    }

    companion object {
        private const val ARG_CATEGORY_ID = "category_id"
        private const val ARG_CATEGORY_NAME = "category_name"
        private const val ARG_SEARCH = "search"

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
    }
}
