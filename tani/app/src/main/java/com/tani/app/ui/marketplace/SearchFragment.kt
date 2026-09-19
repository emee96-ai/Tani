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
import com.tani.app.ui.products.ProductsFragment
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
        val refine = view.findViewById<Button>(R.id.search_refine_button)
        val allStores = view.findViewById<Button>(R.id.search_all_stores_button)
        arguments?.getString(ARG_QUERY)?.let(query::setText)

        fun currentTerm(): String = query.text.toString().trim()

        refine.setOnClickListener {
            val term = currentTerm()
            if (term.length >= 2) (activity as MainActivity).show(ProductsFragment.newSearchInstance(term))
        }
        allStores.setOnClickListener {
            val term = currentTerm()
            if (term.length >= 2) (activity as MainActivity).show(StoresFragment.newSearchInstance(term))
        }

        fun productCard(product: com.tani.app.data.ProductCard): View =
            MarketplaceUi.productCard(
                    requireContext(), viewLifecycleOwner.lifecycleScope, product,
                    onOpen = { (activity as MainActivity).show(ProductDetailsFragment.newInstance(product.id)) },
                    onAdd = {
                        if (product.has_variants) {
                            Toast.makeText(requireContext(), "اختاري المقاس أو اللون أولاً", Toast.LENGTH_SHORT).show()
                            (activity as MainActivity).show(ProductDetailsFragment.newInstance(product.id))
                        } else if (Cart.add(product.toProduct())) {
                            (activity as? MainActivity)?.refreshCartBadge()
                            viewLifecycleOwner.lifecycleScope.launch {
                                Analytics.track("add_to_cart", screen = "search", entityType = "product", entityId = product.id)
                            }
                            Toast.makeText(requireContext(), "تمت إضافة ${product.name} للسلة", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(requireContext(), "تعذر إضافة كمية إضافية", Toast.LENGTH_SHORT).show()
                        }
                    }
            )

        fun runSearch() {
            val term = currentTerm()
            if (term.length < 2) {
                query.error = "اكتبي حرفين على الأقل"
                return
            }
            status.visibility = View.VISIBLE
            status.text = "جاري البحث..."
            refine.visibility = View.GONE
            allStores.visibility = View.GONE
            productBox.removeAllViews()
            storeBox.removeAllViews()
            viewLifecycleOwner.lifecycleScope.launch {
                val cache = MarketplaceCache(requireContext())
                val cacheKey = "search_" + term.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "_").take(40)
                runCatching {
                    coroutineScope {
                        val productsDeferred = async {
                            scaleRepository.rankedSearch(term, city = "كوستي", limit = 40)
                        }
                        val storesDeferred = async { repository.stores(term, limit = 20) }
                        val products = productsDeferred.await()
                        val stores = storesDeferred.await()
                        cache.save(cacheKey, products)
                        products to stores
                    }
                }
                    .onSuccess { (products, stores) ->
                        viewLifecycleOwner.lifecycleScope.launch {
                            Analytics.track("search_submitted", screen = "search", metadata = buildJsonObject {
                                put("query", term.take(80))
                                put("results", products.size)
                            })
                        }
                        status.text = "${products.size} منتج • ${stores.size} متجر"
                        refine.visibility = if (products.isNotEmpty()) View.VISIBLE else View.GONE
                        allStores.visibility = if (stores.isNotEmpty()) View.VISIBLE else View.GONE

                        if (products.isEmpty()) {
                            productBox.addView(MarketplaceUi.empty(requireContext(), "لا توجد منتجات مطابقة"))
                        } else {
                            MarketplaceUi.addTwoColumnGrid(
                                productBox,
                                products.take(8).map(::productCard),
                                requireContext()
                            )
                        }

                        if (stores.isEmpty()) {
                            storeBox.addView(MarketplaceUi.empty(requireContext(), "لا توجد متاجر مطابقة"))
                        } else stores.take(6).forEach { store ->
                            MarketplaceUi.addWithSpacing(
                                storeBox,
                                MarketplaceUi.storeCard(requireContext(), viewLifecycleOwner.lifecycleScope, store) {
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
                            refine.visibility = View.VISIBLE
                            MarketplaceUi.addTwoColumnGrid(
                                productBox,
                                cached.take(8).map(::productCard),
                                requireContext()
                            )
                        } else status.text = "تعذر البحث\n${error.message ?: "حاولي مرة أخرى"}"
                    }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { repository.categories() }.onSuccess { categories ->
                suggestionsBox.removeAllViews()
                categories.take(8).forEach { category ->
                    val chip = MarketplaceUi.chipButton(requireContext(), category.name) {
                        query.setText(category.name)
                        runSearch()
                    }
                    suggestionsBox.addView(
                        chip,
                        LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { marginEnd = MarketplaceUi.dp(requireContext(), 8) }
                    )
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
