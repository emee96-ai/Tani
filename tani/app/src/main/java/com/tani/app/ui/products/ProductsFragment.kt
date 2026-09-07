package com.tani.app.ui.products

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.tani.app.MainActivity
import com.tani.app.R
import com.tani.app.data.*
import com.tani.app.ui.marketplace.MarketplaceUi
import com.tani.app.ui.marketplace.ProductDetailsFragment
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
        val box = view.findViewById<LinearLayout>(R.id.products_box)
        val loading = view.findViewById<TextView>(R.id.products_loading)

        val categoryId = arguments?.getString(ARG_CATEGORY_ID)
        val categoryName = arguments?.getString(ARG_CATEGORY_NAME)
        val initialSearch = arguments?.getString(ARG_SEARCH)
        if (!categoryName.isNullOrBlank()) title.text = categoryName
        if (!initialSearch.isNullOrBlank()) search.setText(initialSearch)

        val sortLabels = listOf("الأحدث", "السعر: الأقل أولاً", "السعر: الأعلى أولاً", "الأعلى تقييماً")
        sort.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, sortLabels)

        fun load() {
            loading.visibility = View.VISIBLE
            loading.text = "جاري تحميل المنتجات..."
            box.removeAllViews()
            lifecycleScope.launch {
                val sortValue = when (sort.selectedItemPosition) {
                    1 -> ProductSort.PRICE_LOW
                    2 -> ProductSort.PRICE_HIGH
                    3 -> ProductSort.RATING
                    else -> ProductSort.NEWEST
                }
                runCatching {
                    repository.marketplaceProducts(
                        search = search.text.toString(),
                        categoryId = categoryId,
                        minPrice = minPrice.text.toString().toDoubleOrNull(),
                        maxPrice = maxPrice.text.toString().toDoubleOrNull(),
                        inStockOnly = inStock.isChecked,
                        sort = sortValue
                    )
                }.onSuccess { products ->
                    loading.visibility = View.GONE
                    render(box, products)
                }.onFailure {
                    loading.text = "تعذر تحميل المنتجات\n${it.message ?: "حاولي مرة أخرى"}"
                }
            }
        }

        view.findViewById<Button>(R.id.products_apply).setOnClickListener { load() }
        view.findViewById<Button>(R.id.products_clear).setOnClickListener {
            search.text.clear(); minPrice.text.clear(); maxPrice.text.clear(); inStock.isChecked = false; sort.setSelection(0); load()
        }
        load()
    }

    private fun render(box: LinearLayout, list: List<ProductCard>) {
        box.removeAllViews()
        if (list.isEmpty()) {
            box.addView(MarketplaceUi.empty(requireContext(), "ما لقينا نتائج مطابقة. جرّبي تغيير البحث أو الفلاتر."))
            return
        }
        list.forEach { product ->
            MarketplaceUi.addWithSpacing(
                box,
                MarketplaceUi.productCard(
                    requireContext(), lifecycleScope, product,
                    onOpen = { (activity as MainActivity).show(ProductDetailsFragment.newInstance(product.id)) },
                    onAdd = {
                        Cart.add(product.toProduct())
                        lifecycleScope.launch {
                            Analytics.track("add_to_cart", screen = "products", entityType = "product", entityId = product.id)
                        }
                        Toast.makeText(requireContext(), "تمت إضافة ${product.name} للسلة", Toast.LENGTH_SHORT).show()
                    }
                ),
                requireContext()
            )
        }
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
