package com.tani.app.ui.marketplace

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
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
import com.tani.app.ui.share.ShareHelper
import kotlinx.coroutines.launch
import java.util.Locale

class StoreDetailsFragment : Fragment(R.layout.fragment_store_details) {
    private val repository = Repository()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val storeId = requireArguments().getString(ARG_STORE_ID) ?: return
        val loading = view.findViewById<TextView>(R.id.store_details_loading)
        val content = view.findViewById<LinearLayout>(R.id.store_details_content)

        lifecycleScope.launch {
            runCatching {
                val store = repository.store(storeId)
                val products = repository.storeProducts(store.seller_id)
                store to products
            }.onSuccess { (store, products) ->
                loading.visibility = View.GONE
                content.visibility = View.VISIBLE
                view.findViewById<TextView>(R.id.store_details_name).text = store.name
                view.findViewById<TextView>(R.id.store_details_description).text =
                    store.description.ifBlank { "متجر محلي على تاني" }
                val trust = MarketplaceUi.verificationLabel(store.verification_status)
                view.findViewById<TextView>(R.id.store_details_status).text =
                    listOfNotNull(trust, if (store.is_open) "مفتوح الآن" else "مغلق حالياً").joinToString(" • ")
                view.findViewById<TextView>(R.id.store_details_location).text =
                    "${store.city}${store.area?.let { " - $it" } ?: ""}"
                view.findViewById<TextView>(R.id.store_details_rating).text =
                    if (store.review_count > 0) {
                        String.format(Locale.US, "★ %.1f (%d تقييم)", store.average_rating, store.review_count)
                    } else "بدون تقييمات بعد"
                val categoryNames = products.mapNotNull { it.category_name?.takeIf { name -> name.isNotBlank() } }.distinct().take(6)
                view.findViewById<TextView>(R.id.store_details_categories).text =
                    if (categoryNames.isEmpty()) "" else "الفئات: ${categoryNames.joinToString(" • ")}"

                view.findViewById<TextView>(R.id.store_details_delivery).text = buildString {
                    append(if (store.delivery_fee > 0) "التوصيل: ${MarketplaceUi.formatPrice(store.delivery_fee)}" else "رسوم التوصيل تظهر حسب إعدادات المتجر")
                    store.delivery_area?.let { append(" • $it") }
                    store.estimated_minutes?.let { append(" • حوالي $it دقيقة") }
                }
                view.findViewById<Button>(R.id.store_details_share).setOnClickListener {
                    ShareHelper.store(requireContext(), store.id, store.name)
                }

                val logo = view.findViewById<ImageView>(R.id.store_details_logo)
                logo.setImageResource(R.drawable.ic_image_placeholder)
                repository.storeImageUrl(store.logo_url)?.let { MarketplaceUi.loadImage(lifecycleScope, logo, it) }
                val cover = view.findViewById<ImageView>(R.id.store_details_cover)
                cover.setImageResource(R.drawable.ic_image_placeholder)
                repository.storeImageUrl(store.cover_url)?.let { MarketplaceUi.loadImage(lifecycleScope, cover, it) }

                val box = view.findViewById<LinearLayout>(R.id.store_details_products)
                box.removeAllViews()
                if (products.isEmpty()) {
                    box.addView(MarketplaceUi.empty(requireContext(), "لا توجد منتجات متاحة في هذا المتجر حالياً"))
                } else {
                    val cards = products.map { product ->
                        MarketplaceUi.productCard(
                            requireContext(), lifecycleScope, product,
                            onOpen = { (activity as MainActivity).show(ProductDetailsFragment.newInstance(product.id)) },
                            onAdd = {
                                if (Cart.add(product.toProduct())) {
                                    (activity as? MainActivity)?.refreshCartBadge()
                                    Toast.makeText(requireContext(), "تمت إضافة ${product.name} للسلة", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(requireContext(), "تعذر إضافة كمية إضافية", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                    MarketplaceUi.addTwoColumnGrid(box, cards, requireContext())
                }

                lifecycleScope.launch {
                    Analytics.track("store_view", screen = "store_details", entityType = "store", entityId = store.id)
                }
            }.onFailure {
                loading.text = "تعذر تحميل المتجر\n${it.message ?: "حاولي مرة أخرى"}"
            }
        }
    }

    companion object {
        private const val ARG_STORE_ID = "store_id"
        fun newInstance(storeId: String): StoreDetailsFragment = StoreDetailsFragment().apply {
            arguments = Bundle().apply { putString(ARG_STORE_ID, storeId) }
        }
    }
}
