package com.tani.app.ui.categories

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
import com.google.android.material.appbar.MaterialToolbar
import com.tani.app.MainActivity
import com.tani.app.R
import com.tani.app.data.Repository
import com.tani.app.ui.marketplace.StoreDetailsFragment
import com.tani.app.ui.marketplace.StoreListAdapter
import kotlinx.coroutines.launch

/**
 * This primary navigation destination used to show categories.
 * It now intentionally presents the stores directory while keeping the
 * existing destination class/id stable for backwards compatibility.
 */
class CategoriesFragment : Fragment(R.layout.fragment_stores) {
    private val repository = Repository()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        activity?.findViewById<MaterialToolbar>(R.id.top_app_bar)?.title = "المتاجر"

        val search = view.findViewById<EditText>(R.id.stores_search)
        val loading = view.findViewById<TextView>(R.id.stores_loading)
        val list = view.findViewById<RecyclerView>(R.id.stores_list)

        val adapter = StoreListAdapter(requireContext(), viewLifecycleOwner.lifecycleScope) { store ->
            (activity as MainActivity).show(StoreDetailsFragment.newInstance(store.id))
        }
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter

        fun load() {
            loading.visibility = View.VISIBLE
            loading.text = "جاري تحميل المتاجر..."
            viewLifecycleOwner.lifecycleScope.launch {
                runCatching { repository.stores(search.text.toString(), limit = 100) }
                    .onSuccess { stores ->
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
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                load()
                true
            } else {
                false
            }
        }
        load()
    }
}
