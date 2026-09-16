package com.tani.app.ui.marketplace

import android.os.Bundle
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
import com.tani.app.data.Repository
import com.tani.app.data.cache.AppContentStore
import com.tani.app.data.cache.MarketplaceCache
import kotlinx.coroutines.launch

class StoresFragment : Fragment(R.layout.fragment_stores) {
    private val repository = Repository()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val search = view.findViewById<EditText>(R.id.stores_search)
        val loading = view.findViewById<TextView>(R.id.stores_loading)
        val list = view.findViewById<RecyclerView>(R.id.stores_list)
        arguments?.getString(ARG_QUERY)?.let(search::setText)

        val adapter = StoreListAdapter(requireContext(), viewLifecycleOwner.lifecycleScope) { store ->
            (activity as MainActivity).show(StoreDetailsFragment.newInstance(store.id))
        }
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter

        fun load() {
            loading.visibility = View.VISIBLE
            loading.text = "جاري تحميل المتاجر..."
            viewLifecycleOwner.lifecycleScope.launch {
                if (AppContentStore.storesLoaded) {
                    val stores = AppContentStore.filteredStores(search.text.toString())
                    adapter.submitList(stores)
                    loading.visibility = if (stores.isEmpty()) View.VISIBLE else View.GONE
                    if (stores.isEmpty()) loading.text = "لا توجد متاجر مطابقة حالياً"
                    return@launch
                }
                runCatching { repository.stores(search.text.toString(), limit = 100) }
                    .onSuccess { stores ->
                        if (search.text.isBlank()) {
                            AppContentStore.updateStores(stores)
                            MarketplaceCache(requireContext()).saveStores(stores)
                        }
                        adapter.submitList(stores)
                        if (stores.isEmpty()) {
                            loading.visibility = View.VISIBLE
                            loading.text = "لا توجد متاجر مطابقة حالياً"
                        } else {
                            loading.visibility = View.GONE
                        }
                    }
                    .onFailure {
                        adapter.submitList(emptyList())
                        loading.visibility = View.VISIBLE
                        loading.text = "تعذر تحميل المتاجر\n${it.message ?: "حاولي مرة أخرى"}"
                    }
            }
        }

        view.findViewById<Button>(R.id.stores_search_button).setOnClickListener { load() }
        search.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { load(); true } else false
        }
        load()
    }

    companion object {
        private const val ARG_QUERY = "query"
        fun newSearchInstance(query: String): StoresFragment = StoresFragment().apply {
            arguments = Bundle().apply { putString(ARG_QUERY, query) }
        }
    }
}
