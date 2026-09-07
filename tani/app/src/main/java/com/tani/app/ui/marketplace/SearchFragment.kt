package com.tani.app.ui.marketplace

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.tani.app.MainActivity
import com.tani.app.R
import com.tani.app.data.Analytics
import com.tani.app.data.Cart
import com.tani.app.data.Repository
import com.tani.app.data.cache.MarketplaceCache
import com.tani.app.data.repository.ScaleRepository
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SearchFragment : Fragment(R.layout.fragment_search) {
    private val repository = Repository()
    private val scaleRepository = ScaleRepository()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val query = view.findViewById<EditText>(R.id.search_query)
        val status = view.findViewById<TextView>(R.id.search_status)
        val productBox = view.findViewById<LinearLayout>(R.id.search_products_box)
        val storeBox = view.findViewById<LinearLayout>(R.id.search_stores_box)
        val suggestionsBox = view.findViewById<LinearLayout>(R.id.search_suggestions_box)
        arguments?.getString(ARG_QUERY)?.let(query::setText)

        fun runSearch() {
            val term = query.text.toString().trim()
            if (term.length < 2) {
                query.error = "اكتبي حرفين على الأقل"
                return
            }
            status.visibility = View.VISIBLE
            status.text = "جاري البحث..."
            productBox.removeAllViews(); storeBox.removeAllViews()
            lifecycleScope.launch {
                val cache = MarketplaceCache(requireContext())
                val cacheKey = "search_" + term.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "_").take(40)
                runCatching {
                    val products = scaleRepository.rankedSearch(term, city = "كوستي", limit = 40)
                    val stores = repository.stores(term, limit = 20)
                    cache.save(cacheKey, products)
                    products to stores
                }
                    .onSuccess { (products, stores) ->
                        lifecycleScope.launch { Analytics.track("search_submitted", screen = "search", metadata = buildJsonObject { put("query", term.take(80)); put("results", products.size) }) }
                        status.text = "${products.size} منتج • ${stores.size} متجر"
                        if (products.isEmpty()) {
                            productBox.addView(MarketplaceUi.empty(requireContext(), "لا توجد منتجات مطابقة"))
                        } else products.forEach { product ->
                            MarketplaceUi.addWithSpacing(
                                productBox,
                                MarketplaceUi.productCard(
                                    requireContext(), lifecycleScope, product,
                                    onOpen = { (activity as MainActivity).show(ProductDetailsFragment.newInstance(product.id)) },
                                    onAdd = {
                                        Cart.add(product.toProduct())
                                        lifecycleScope.launch {
                                            Analytics.track("add_to_cart", screen = "search", entityType = "product", entityId = product.id)
                                        }
                                        Toast.makeText(requireContext(), "تمت إضافة ${product.name} للسلة", Toast.LENGTH_SHORT).show()
                                    }
                                ),
                                requireContext()
                            )
                        }

                        if (stores.isEmpty()) {
                            storeBox.addView(MarketplaceUi.empty(requireContext(), "لا توجد متاجر مطابقة"))
                        } else stores.forEach { store ->
                            MarketplaceUi.addWithSpacing(
                                storeBox,
                                MarketplaceUi.storeCard(requireContext(), lifecycleScope, store) {
                                    (activity as MainActivity).show(StoreDetailsFragment.newInstance(store.id))
                                },
                                requireContext()
                            )
                        }
                    }
                    .onFailure { error ->
                        val cached = cache.load(cacheKey)
                        if (cached.isNotEmpty()) {
                            status.text = "${cached.size} منتج • نتائج محفوظة بدون اتصال"
                            cached.forEach { product ->
                                MarketplaceUi.addWithSpacing(productBox, MarketplaceUi.productCard(requireContext(), lifecycleScope, product,
                                    onOpen = { (activity as MainActivity).show(ProductDetailsFragment.newInstance(product.id)) },
                                    onAdd = { Cart.add(product.toProduct()); Toast.makeText(requireContext(), "تمت الإضافة للسلة", Toast.LENGTH_SHORT).show() }
                                ), requireContext())
                            }
                        } else status.text = "تعذر البحث\n${error.message ?: "حاولي مرة أخرى"}"
                    }
            }
        }

        lifecycleScope.launch {
            runCatching { repository.categories() }.onSuccess { categories ->
                suggestionsBox.removeAllViews()
                categories.take(8).forEach { category ->
                    suggestionsBox.addView(Button(requireContext()).apply {
                        text = category.name
                        setOnClickListener {
                            query.setText(category.name)
                            runSearch()
                        }
                    })
                }
            }
        }

        view.findViewById<Button>(R.id.search_button).setOnClickListener { runSearch() }
        query.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { runSearch(); true } else false
        }

        if (query.text.toString().trim().length >= 2) runSearch()
    }

    companion object {
        private const val ARG_QUERY = "query"
        fun newInstance(query: String): SearchFragment = SearchFragment().apply {
            arguments = Bundle().apply { putString(ARG_QUERY, query) }
        }
    }
}
