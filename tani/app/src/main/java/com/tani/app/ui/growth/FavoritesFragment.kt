package com.tani.app.ui.growth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.tani.app.MainActivity
import com.tani.app.R
import com.tani.app.data.Cart
import com.tani.app.data.repository.GrowthRepository
import com.tani.app.ui.common.ScreenUi
import com.tani.app.ui.marketplace.MarketplaceUi
import com.tani.app.ui.marketplace.ProductDetailsFragment
import kotlinx.coroutines.launch

class FavoritesFragment : Fragment() {
    private val repository = GrowthRepository()
    private lateinit var root: LinearLayout
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val c=requireContext(); root=ScreenUi.root(c)
        return ScrollView(c).apply { setBackgroundColor(c.getColor(R.color.tani_background)); addView(root) }
    }
    override fun onViewCreated(view: View, state: Bundle?) { load() }
    private fun load() {
        val c=requireContext(); root.removeAllViews(); root.addView(ScreenUi.title(c,"المفضلة")); root.addView(ScreenUi.subtitle(c,"المنتجات التي حفظتيها للرجوع إليها بسهولة."))
        lifecycleScope.launch {
            runCatching { repository.favoriteProducts() }.onSuccess { products ->
                if(products.isEmpty()) root.addView(ScreenUi.muted(c,"لم تحفظي أي منتج بعد."))
                products.forEach { p -> MarketplaceUi.addWithSpacing(root, MarketplaceUi.productCard(c,lifecycleScope,p,
                    onOpen={ (activity as MainActivity).show(ProductDetailsFragment.newInstance(p.id)) },
                    onAdd={ Cart.add(p.toProduct()); Toast.makeText(c,"تمت الإضافة للسلة",Toast.LENGTH_SHORT).show() }
                ),c) }
            }.onFailure { root.addView(ScreenUi.muted(c,it.message?:"تعذر تحميل المفضلة")) }
        }
    }
}
