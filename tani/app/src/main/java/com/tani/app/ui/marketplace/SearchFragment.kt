package com.tani.app.ui.marketplace

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.tani.app.MainActivity
import com.tani.app.R
import com.tani.app.data.Analytics
import com.tani.app.data.Cart
import com.tani.app.data.ProductCard
import com.tani.app.data.Repository
import com.tani.app.data.StoreCard
import com.tani.app.data.cache.AppContentStore
import com.tani.app.data.cache.MarketplaceCache
import com.tani.app.data.cache.SearchPreferences
import com.tani.app.data.network.NetworkStatus
import com.tani.app.data.repository.ScaleRepository
import com.tani.app.data.storePage
import com.tani.app.ui.products.ProductsFragment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SearchFragment : Fragment(R.layout.fragment_search) {
    private val repository = Repository()
    private val scaleRepository = ScaleRepository()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val query = view.findViewById<EditText>(R.id.search_query)
        val citySpinner = view.findViewById<Spinner>(R.id.search_city)
        val status = view.findViewById<TextView>(R.id.search_status)
        val productBox = view.findViewById<LinearLayout>(R.id.search_products_box)
        val storeBox = view.findViewById<LinearLayout>(R.id.search_stores_box)
        val suggestionsBox = view.findViewById<LinearLayout>(R.id.search_suggestions_box)
        val recentTitle = view.findViewById<TextView>(R.id.search_recent_title)
        val recentScroll = view.findViewById<View>(R.id.search_recent_scroll)
        val recentBox = view.findViewById<LinearLayout>(R.id.search_recent_box)
        val refine = view.findViewById<Button>(R.id.search_refine_button)
        val allStores = view.findViewById<Button>(R.id.search_all_stores_button)
        val cache = MarketplaceCache(requireContext())
        val searchPreferences = SearchPreferences(requireContext())

        val explicitQuery = arguments?.getString(ARG_QUERY)?.trim()?.takeIf { it.length >= 2 }
        val restoredQuery = explicitQuery ?: searchPreferences.lastQuery
        restoredQuery?.let(query::setText)

        var searchJob: Job? = null
        var debounceJob: Job? = null
        var generation = 0
        val cityOptions = mutableListOf<String>()

        fun currentTerm(): String = query.text.toString().trim()
        fun selectedCity(): String? = cityOptions
            .getOrNull(citySpinner.selectedItemPosition)
            ?.takeIf { !it.equals(ALL_CITIES, ignoreCase = true) }

        fun renderRecentSearches() {
            val recent = searchPreferences.recentQueries
            recentBox.removeAllViews()
            recentTitle.visibility = if (recent.isEmpty()) View.GONE else View.VISIBLE
            recentScroll.visibility = if (recent.isEmpty()) View.GONE else View.VISIBLE
            recent.forEach { term ->
                recentBox.addView(
                    MarketplaceUi.chipButton(requireContext(), term) {
                        query.setText(term)
                        query.setSelection(query.text.length)
                        scheduleSearch(immediate = true, remember = true)
                    },
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { marginEnd = MarketplaceUi.dp(requireContext(), 8) }
                )
            }
        }

        fun setCityOptions(names: List<String>) {
            val preferred = searchPreferences.selectedCity
            val clean = names.map { it.trim() }.filter { it.isNotBlank() }.distinct()
            val options = buildList {
                add(ALL_CITIES)
                if (!preferred.isNullOrBlank() && clean.none { it.equals(preferred, ignoreCase = true) }) {
                    add(preferred)
                }
                clean.forEach { city ->
                    if (none { it.equals(city, ignoreCase = true) }) add(city)
                }
            }
            cityOptions.clear()
            cityOptions.addAll(options)
            citySpinner.adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                cityOptions
            )
            val selectedIndex = preferred?.let { remembered ->
                cityOptions.indexOfFirst { it.equals(remembered, ignoreCase = true) }.takeIf { it >= 0 }
            } ?: 0
            citySpinner.setSelection(selectedIndex, false)
        }

        setCityOptions(listOfNotNull(searchPreferences.selectedCity))
        renderRecentSearches()

        refine.setOnClickListener {
            val term = currentTerm()
            if (term.length >= 2) {
                (activity as MainActivity).show(
                    ProductsFragment.newSearchInstance(term, selectedCity())
                )
            }
        }
        allStores.setOnClickListener {
            val term = currentTerm()
            if (term.length >= 2) {
                (activity as MainActivity).show(
                    StoresFragment.newSearchInstance(term, selectedCity())
                )
            }
        }

        fun productCard(product: ProductCard): View =
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

        fun renderResults(
            products: List<ProductCard>,
            stores: List<StoreCard>,
            message: String
        ) {
            status.visibility = View.VISIBLE
            status.text = message
            refine.visibility = if (products.isNotEmpty()) View.VISIBLE else View.GONE
            allStores.visibility = if (stores.isNotEmpty()) View.VISIBLE else View.GONE

            productBox.removeAllViews()
            if (products.isEmpty()) {
                productBox.addView(
                    MarketplaceUi.empty(
                        requireContext(),
                        "ما لقينا منتجات مطابقة. جرّبي كلمة أبسط أو اختاري فئة من الاقتراحات."
                    )
                )
            } else {
                MarketplaceUi.addTwoColumnGrid(
                    productBox,
                    products.take(PREVIEW_PRODUCTS).map(::productCard),
                    requireContext()
                )
            }

            storeBox.removeAllViews()
            if (stores.isEmpty()) {
                storeBox.addView(MarketplaceUi.empty(requireContext(), "لا توجد متاجر مطابقة"))
            } else stores.take(PREVIEW_STORES).forEach { store ->
                MarketplaceUi.addWithSpacing(
                    storeBox,
                    MarketplaceUi.storeCard(requireContext(), viewLifecycleOwner.lifecycleScope, store) {
                        (activity as MainActivity).show(StoreDetailsFragment.newInstance(store.id))
                    },
                    requireContext()
                )
            }
        }

        suspend fun cachedStores(term: String, city: String?): List<StoreCard> {
            if (!AppContentStore.storesLoaded) {
                val disk = cache.loadStores(allowExpired = true)
                if (disk.isNotEmpty()) AppContentStore.updateStores(disk)
            }
            return AppContentStore.filteredStores(term).filter { store ->
                city == null || store.city.equals(city, ignoreCase = true)
            }
        }

        fun clearForShortQuery() {
            generation++
            searchJob?.cancel()
            debounceJob?.cancel()
            refine.visibility = View.GONE
            allStores.visibility = View.GONE
            productBox.removeAllViews()
            storeBox.removeAllViews()
            status.visibility = View.VISIBLE
            status.text = "اكتبي حرفين على الأقل للبحث"
        }

        fun runSearch(remember: Boolean) {
            val term = currentTerm()
            if (term.length < 2) {
                query.error = "اكتبي حرفين على الأقل"
                clearForShortQuery()
                return
            }

            if (remember) {
                searchPreferences.recordQuery(term)
                renderRecentSearches()
            }

            generation++
            val requestGeneration = generation
            val city = selectedCity()
            val citySuffix = city?.let { " • $it" }.orEmpty()
            searchJob?.cancel()
            searchJob = viewLifecycleOwner.lifecycleScope.launch {
                val cacheKey = searchCacheKey(term, city)
                val online = NetworkStatus.isOnline(requireContext())
                val cachedProducts = cache.load(cacheKey, allowExpired = true)
                val cachedStoreResults = cachedStores(term, city)

                if (requestGeneration != generation) return@launch

                if (cachedProducts.isNotEmpty() || cachedStoreResults.isNotEmpty()) {
                    renderResults(
                        cachedProducts,
                        cachedStoreResults,
                        if (online) {
                            "نتائج محفوظة$citySuffix • جاري التحديث..."
                        } else {
                            "بدون اتصال$citySuffix • ${cachedProducts.size} منتج • ${cachedStoreResults.size} متجر محفوظ"
                        }
                    )
                } else {
                    status.visibility = View.VISIBLE
                    status.text = if (online) "جاري البحث$citySuffix..." else "لا يوجد اتصال ولا توجد نتائج محفوظة لهذا البحث$citySuffix"
                    refine.visibility = View.GONE
                    allStores.visibility = View.GONE
                    productBox.removeAllViews()
                    storeBox.removeAllViews()
                }

                if (!online) return@launch

                try {
                    val (products, stores) = coroutineScope {
                        val productsDeferred = async {
                            scaleRepository.rankedSearch(term, city = city, limit = SEARCH_RESULT_LIMIT)
                        }
                        val storesDeferred = async {
                            repository.storePage(
                                search = term,
                                city = city,
                                pageSize = STORE_RESULT_LIMIT,
                                offset = 0
                            ).items
                        }
                        productsDeferred.await() to storesDeferred.await()
                    }
                    if (requestGeneration != generation) return@launch

                    cache.save(cacheKey, products)
                    renderResults(
                        products,
                        stores,
                        if (products.isEmpty() && stores.isEmpty()) {
                            "ما لقينا نتائج لـ «${term.take(40)}»$citySuffix"
                        } else {
                            "${products.size} منتج • ${stores.size} متجر$citySuffix"
                        }
                    )

                    launch {
                        Analytics.track("search_submitted", screen = "search", metadata = buildJsonObject {
                            put("query", term.take(80))
                            put("results", products.size)
                            city?.let { put("city", it) }
                        })
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    if (requestGeneration != generation) return@launch
                    if (cachedProducts.isNotEmpty() || cachedStoreResults.isNotEmpty()) {
                        renderResults(
                            cachedProducts,
                            cachedStoreResults,
                            "تعذر التحديث$citySuffix • نعرض آخر نتائج محفوظة"
                        )
                    } else {
                        status.visibility = View.VISIBLE
                        status.text = "تعذر البحث\n${error.message ?: "حاولي مرة أخرى"}"
                    }
                }
            }
        }

        fun scheduleSearch(immediate: Boolean = false, remember: Boolean = false) {
            val term = currentTerm()
            debounceJob?.cancel()
            if (term.length < 2) {
                clearForShortQuery()
                return
            }
            debounceJob = viewLifecycleOwner.lifecycleScope.launch {
                if (!immediate) delay(SEARCH_DEBOUNCE_MS)
                runSearch(remember)
            }
        }

        fun renderSuggestions(categories: List<com.tani.app.data.Category>) {
            suggestionsBox.removeAllViews()
            categories.take(8).forEach { category ->
                val chip = MarketplaceUi.chipButton(requireContext(), category.name) {
                    query.setText(category.name)
                    query.setSelection(query.text.length)
                    scheduleSearch(immediate = true, remember = true)
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

        citySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, itemView: View?, position: Int, id: Long) {
                val city = selectedCity()
                if (city == searchPreferences.selectedCity) return
                searchPreferences.selectedCity = city
                if (currentTerm().length >= 2) scheduleSearch(immediate = true)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        AppContentStore.homeFeed?.categories?.takeIf { it.isNotEmpty() }?.let(::renderSuggestions)
        viewLifecycleOwner.lifecycleScope.launch {
            if (!NetworkStatus.isOnline(requireContext())) return@launch
            runCatching { repository.categories() }.onSuccess(::renderSuggestions)
        }
        viewLifecycleOwner.lifecycleScope.launch {
            if (!NetworkStatus.isOnline(requireContext())) return@launch
            runCatching { scaleRepository.activeCities() }.onSuccess { cities ->
                setCityOptions(cities.map { it.name })
            }
        }

        query.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                scheduleSearch()
            }
        })

        view.findViewById<Button>(R.id.search_button).setOnClickListener {
            scheduleSearch(immediate = true, remember = true)
        }
        query.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                scheduleSearch(immediate = true, remember = true)
                true
            } else false
        }

        if (currentTerm().length >= 2) {
            scheduleSearch(immediate = true, remember = explicitQuery != null)
        } else {
            clearForShortQuery()
        }
    }

    private fun searchCacheKey(term: String, city: String?): String {
        fun normalize(value: String): String = value
            .lowercase()
            .replace(Regex("[أإآٱ]"), "ا")
            .replace('ى', 'ي')
            .replace('ؤ', 'و')
            .replace('ئ', 'ي')
            .replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
            .replace(Regex("[^\\p{L}\\p{N}]+"), "_")
            .trim('_')

        val normalizedTerm = normalize(term).take(48)
        val normalizedCity = city?.let(::normalize)?.take(24) ?: "all"
        return "search_v3_${normalizedCity}_$normalizedTerm"
    }

    companion object {
        private const val ARG_QUERY = "query"
        private const val SEARCH_DEBOUNCE_MS = 400L
        private const val SEARCH_RESULT_LIMIT = 40
        private const val STORE_RESULT_LIMIT = 20
        private const val PREVIEW_PRODUCTS = 8
        private const val PREVIEW_STORES = 6
        private const val ALL_CITIES = "كل المدن"

        fun newInstance(query: String): SearchFragment = SearchFragment().apply {
            arguments = Bundle().apply { putString(ARG_QUERY, query) }
        }
    }
}
