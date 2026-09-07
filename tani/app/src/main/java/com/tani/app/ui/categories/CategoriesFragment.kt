package com.tani.app.ui.categories

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.tani.app.MainActivity
import com.tani.app.R
import com.tani.app.data.Repository
import com.tani.app.ui.marketplace.MarketplaceUi
import com.tani.app.ui.marketplace.StoresFragment
import com.tani.app.ui.products.ProductsFragment
import kotlinx.coroutines.launch

class CategoriesFragment : Fragment(R.layout.fragment_categories) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val box = view.findViewById<LinearLayout>(R.id.categories_box)
        val loading = view.findViewById<TextView>(R.id.categories_loading)
        view.findViewById<Button>(R.id.categories_all_products).setOnClickListener {
            (activity as MainActivity).show(ProductsFragment())
        }
        view.findViewById<Button>(R.id.categories_stores).setOnClickListener {
            (activity as MainActivity).show(StoresFragment())
        }

        lifecycleScope.launch {
            runCatching { Repository().categories() }
                .onSuccess { categories ->
                    loading.visibility = View.GONE
                    box.removeAllViews()
                    if (categories.isEmpty()) {
                        box.addView(MarketplaceUi.empty(requireContext(), "لا توجد فئات مفعلة حالياً"))
                    } else categories.forEach { category ->
                        MarketplaceUi.addWithSpacing(
                            box,
                            Button(requireContext()).apply {
                                text = category.name
                                setOnClickListener {
                                    (activity as MainActivity).show(
                                        ProductsFragment.newInstance(category.id, category.name)
                                    )
                                }
                            },
                            requireContext(),
                            8
                        )
                    }
                }
                .onFailure { loading.text = "تعذر تحميل الفئات\n${it.message ?: "حاولي مرة أخرى"}" }
        }
    }
}
