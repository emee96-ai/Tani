package com.tani.app.ui.marketplace

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.tani.app.MainActivity
import com.tani.app.R
import com.tani.app.data.Repository
import kotlinx.coroutines.launch

class StoresFragment : Fragment(R.layout.fragment_stores) {
    private val repository = Repository()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val search = view.findViewById<EditText>(R.id.stores_search)
        val box = view.findViewById<LinearLayout>(R.id.stores_box)
        val loading = view.findViewById<TextView>(R.id.stores_loading)

        fun load() {
            loading.visibility = View.VISIBLE
            loading.text = "جاري تحميل المتاجر..."
            box.removeAllViews()
            lifecycleScope.launch {
                runCatching { repository.stores(search.text.toString()) }
                    .onSuccess { stores ->
                        loading.visibility = View.GONE
                        if (stores.isEmpty()) {
                            box.addView(MarketplaceUi.empty(requireContext(), "لا توجد متاجر مطابقة حالياً"))
                        } else stores.forEach { store ->
                            MarketplaceUi.addWithSpacing(
                                box,
                                MarketplaceUi.storeCard(requireContext(), lifecycleScope, store) {
                                    (activity as MainActivity).show(StoreDetailsFragment.newInstance(store.id))
                                },
                                requireContext()
                            )
                        }
                    }
                    .onFailure { loading.text = "تعذر تحميل المتاجر\n${it.message ?: "حاولي مرة أخرى"}" }
            }
        }

        view.findViewById<Button>(R.id.stores_search_button).setOnClickListener { load() }
        search.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { load(); true } else false
        }
        load()
    }
}
