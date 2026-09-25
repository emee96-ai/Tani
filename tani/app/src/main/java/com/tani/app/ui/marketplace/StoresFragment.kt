package com.tani.app.ui.marketplace

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tani.app.MainActivity
import com.tani.app.R
import com.tani.app.data.StoreCard
import com.tani.app.data.Repository
import com.tani.app.data.cache.AppContentStore
import com.tani.app.data.cache.MarketplaceCache
import com.tani.app.data.catalog.CatalogPagination
import com.tani.app.data.network.NetworkStatus
import com.tani.app.data.storePage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class StoresFragment : Fragment(R.layout.fragment_stores) {
    private val repository = Repository()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val search = view.findViewById<EditText>(R.id.stores_search)
        val loading = view.findViewById<TextView>(R.id.stores_loading)
        val list = view.findViewById<RecyclerView>(R.id.stores_list)
        val cache = MarketplaceCache(requireContext())
        arguments?.getString(ARG_QUERY)?.let(search::setText)

        val adapter = StoreListAdapter(requireContext(), viewLifecycleOwner.lifecycleScope) { store ->
            (activity as MainActivity).show(StoreDetailsFragment.newInstance(store.id))
        }
        val layoutManager = LinearLayoutManager(requireContext())
        list.layoutManager = layoutManager
        list.adapter = adapter

        val loaded = mutableListOf<StoreCard>()
        var nextOffset = 0
        var hasMore = true
        var isLoading = false
        var generation = 0
        var requestJob: Job? = null
        var debounceJob: Job? = null

        fun currentTerm(): String = search.text.toString().trim()

        fun renderStatus(message: String? = null) {
            loading.visibility = View.VISIBLE
            loading.text = message ?: when {
                isLoading && loaded.isEmpty() -> "جاري تحميل المتاجر..."
                isLoading -> "جاري تحميل المزيد..."
                loaded.isEmpty() -> "لا توجد متاجر مطابقة حالياً"
                hasMore -> "تم عرض ${loaded.size} متجر • مرري لعرض المزيد"
                else -> "تم عرض ${loaded.size} متجر"
            }
        }

        suspend fun cachedStores(term: String): List<StoreCard> {
            if (!AppContentStore.storesLoaded) {
                val diskStores = cache.loadStores(allowExpired = true)
                if (diskStores.isNotEmpty()) AppContentStore.updateStores(diskStores)
            }
            return AppContentStore.filteredStores(term)
        }

        fun loadPage(reset: Boolean, showCacheFirst: Boolean = reset) {
            if (!reset && (isLoading || !hasMore)) return

            val term = currentTerm()
            if (term.length == 1) {
                generation++
                requestJob?.cancel()
                debounceJob?.cancel()
                loaded.clear()
                adapter.submitList(emptyList())
                hasMore = false
                isLoading = false
                renderStatus("اكتبي حرفين على الأقل للبحث، أو امسحي النص لعرض كل المتاجر")
                return
            }

            if (reset) {
                generation++
                requestJob?.cancel()
                nextOffset = 0
                hasMore = true
            }

            val requestGeneration = generation
            val requestOffset = if (reset) 0 else nextOffset
            isLoading = true
            renderStatus()

            requestJob = viewLifecycleOwner.lifecycleScope.launch {
                val online = NetworkStatus.isOnline(requireContext())

                if (showCacheFirst && reset) {
                    val cached = cachedStores(term)
                    if (requestGeneration != generation) return@launch
                    if (cached.isNotEmpty()) {
                        loaded.clear()
                        loaded.addAll(cached)
                        adapter.submitList(loaded.toList())
                        renderStatus(
                            if (online) "نعرض آخر بيانات محفوظة • جاري التحديث..."
                            else "بدون اتصال — نعرض ${loaded.size} متجر من النسخة المحفوظة"
                        )
                    }
                }

                if (!online) {
                    isLoading = false
                    hasMore = false
                    if (loaded.isEmpty()) {
                        renderStatus("لا يوجد اتصال ولا توجد متاجر محفوظة مطابقة")
                    } else {
                        renderStatus("بدون اتصال — نعرض ${loaded.size} متجر من النسخة المحفوظة")
                    }
                    return@launch
                }

                try {
                    val page = repository.storePage(
                        search = term,
                        pageSize = PAGE_SIZE,
                        offset = requestOffset
                    )
                    if (requestGeneration != generation) return@launch

                    if (reset) loaded.clear()
                    val ids = loaded.mapTo(mutableSetOf()) { it.id }
                    page.items.forEach { store -> if (ids.add(store.id)) loaded += store }
                    nextOffset = page.nextOffset
                    hasMore = page.hasMore

                    if (reset && term.isBlank()) {
                        AppContentStore.updateStores(page.items)
                        cache.saveStores(page.items)
                    }

                    adapter.submitList(loaded.toList())
                    isLoading = false
                    renderStatus()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    if (requestGeneration != generation) return@launch
                    isLoading = false
                    if (loaded.isEmpty() && reset) {
                        val cached = cachedStores(term)
                        loaded.clear()
                        loaded.addAll(cached)
                        adapter.submitList(loaded.toList())
                    }
                    renderStatus(
                        if (loaded.isNotEmpty()) {
                            "تعذر التحديث — نعرض ${loaded.size} متجر محفوظ. مرري أو ابحثي للمحاولة مجدداً."
                        } else {
                            "تعذر تحميل المتاجر\n${error.message ?: "حاولي مرة أخرى"}"
                        }
                    )
                }
            }
        }

        fun scheduleSearch(immediate: Boolean = false) {
            debounceJob?.cancel()
            debounceJob = viewLifecycleOwner.lifecycleScope.launch {
                if (!immediate) delay(SEARCH_DEBOUNCE_MS)
                loadPage(reset = true)
            }
        }

        list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0 || isLoading || !hasMore) return
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                if (lastVisible >= adapter.itemCount - LOAD_AHEAD_ITEMS) {
                    loadPage(reset = false, showCacheFirst = false)
                }
            }
        })

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                scheduleSearch()
            }
        })

        view.findViewById<Button>(R.id.stores_search_button).setOnClickListener {
            scheduleSearch(immediate = true)
        }
        search.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                scheduleSearch(immediate = true)
                true
            } else false
        }

        loadPage(reset = true)
    }

    companion object {
        private const val ARG_QUERY = "query"
        private const val PAGE_SIZE = CatalogPagination.DEFAULT_PAGE_SIZE
        private const val LOAD_AHEAD_ITEMS = 5
        private const val SEARCH_DEBOUNCE_MS = 450L

        fun newSearchInstance(query: String): StoresFragment = StoresFragment().apply {
            arguments = Bundle().apply { putString(ARG_QUERY, query) }
        }
    }
}
