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
import com.tani.app.data.ProductDetails
import com.tani.app.data.Repository
import com.tani.app.data.Supabase
import com.tani.app.data.repository.GrowthRepository
import com.tani.app.data.repository.TrustRepository
import com.tani.app.ui.share.ShareHelper
import kotlinx.coroutines.launch
import java.util.Locale

class ProductDetailsFragment : Fragment(R.layout.fragment_product_details) {
    private val repository = Repository()
    private val growthRepository = GrowthRepository()
    private val trustRepository = TrustRepository()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val productId = requireArguments().getString(ARG_PRODUCT_ID)
            ?: return
        val loading = view.findViewById<TextView>(R.id.product_details_loading)
        val content = view.findViewById<LinearLayout>(R.id.product_details_content)

        lifecycleScope.launch {
            runCatching { repository.productDetails(productId) }
                .onSuccess { details ->
                    loading.visibility = View.GONE
                    content.visibility = View.VISIBLE
                    bind(view, details)
                }
                .onFailure { loading.text = "تعذر تحميل المنتج\n${it.message ?: "حاولي مرة أخرى"}" }
        }
    }

    private fun bind(view: View, details: ProductDetails) {
        val product = details.product
        view.findViewById<TextView>(R.id.product_details_name).text = product.name
        view.findViewById<TextView>(R.id.product_details_price).text = MarketplaceUi.formatPrice(product.price)
        view.findViewById<TextView>(R.id.product_details_description).text =
            product.description.ifBlank { "لا يوجد وصف إضافي لهذا المنتج." }
        view.findViewById<TextView>(R.id.product_details_stock).text =
            if (product.stock > 0) "متوفر: ${product.stock}" else "غير متوفر حالياً"
        view.findViewById<TextView>(R.id.product_details_merchant).text =
            "${product.store_name} • متجر موثق ✓"
        val trustText = view.findViewById<TextView>(R.id.product_details_trust)
        trustText.text = "جاري حساب مستوى الثقة…"
        lifecycleScope.launch {
            runCatching { trustRepository.merchantTrust(product.seller_id) }.onSuccess { trust ->
                trustText.text = if (trust == null) "الثقة: موثق" else {
                    val level = when (trust.trust_level) {
                        "high_performing" -> "أداء مرتفع"
                        "trusted" -> "موثوق"
                        "restricted" -> "مقيّد"
                        else -> "موثق"
                    }
                    "الثقة: $level • ${String.format(Locale.US, "%.0f", trust.trust_score)}/100 • إكمال ${String.format(Locale.US, "%.0f", trust.completion_rate)}%"
                }
            }.onFailure { trustText.text = "الثقة: متجر موثق" }
        }
        view.findViewById<TextView>(R.id.product_details_rating).text =
            if (product.review_count > 0) {
                String.format(Locale.US, "★ %.1f من 5 (%d تقييم)", product.average_rating, product.review_count)
            } else "لا توجد تقييمات بعد"
        view.findViewById<TextView>(R.id.product_details_delivery).text = buildString {
            append(if (product.delivery_fee > 0) "رسوم التوصيل: ${MarketplaceUi.formatPrice(product.delivery_fee)}" else "رسوم التوصيل يحددها المتجر")
            product.delivery_area?.takeIf { it.isNotBlank() }?.let { append("\nمنطقة التوصيل: $it") }
            product.estimated_minutes?.let { append("\nالوقت التقديري: $it دقيقة") }
        }

        val image = view.findViewById<ImageView>(R.id.product_details_image)
        val imagePath = details.images.firstOrNull { it.is_primary }?.storage_path
            ?: details.images.firstOrNull()?.storage_path
            ?: product.image
        repository.productImageUrl(imagePath)?.let { MarketplaceUi.loadImage(lifecycleScope, image, it) }

        val gallery = view.findViewById<LinearLayout>(R.id.product_details_images)
        gallery.removeAllViews()
        val galleryPaths = details.images.map { it.storage_path }.ifEmpty {
            listOfNotNull(product.image)
        }
        galleryPaths.take(8).forEach { path ->
            val thumb = ImageView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    (96 * resources.displayMetrics.density).toInt(),
                    (96 * resources.displayMetrics.density).toInt()
                ).apply { marginEnd = (8 * resources.displayMetrics.density).toInt() }
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageResource(android.R.drawable.ic_menu_gallery)
                setOnClickListener {
                    repository.productImageUrl(path)?.let { url -> MarketplaceUi.loadImage(lifecycleScope, image, url) }
                }
            }
            repository.productImageUrl(path)?.let { MarketplaceUi.loadImage(lifecycleScope, thumb, it) }
            gallery.addView(thumb)
        }

        view.findViewById<Button>(R.id.product_details_add).apply {
            isEnabled = product.stock > 0
            setOnClickListener {
                Cart.add(product.toProduct())
                lifecycleScope.launch {
                    Analytics.track("add_to_cart", screen = "product_details", entityType = "product", entityId = product.id)
                }
                Toast.makeText(requireContext(), "تمت إضافة ${product.name} للسلة", Toast.LENGTH_SHORT).show()
            }
        }

        val favoriteButton = view.findViewById<Button>(R.id.product_details_favorite)
        val shareButton = view.findViewById<Button>(R.id.product_details_share)
        var favoriteState = false
        favoriteButton.isEnabled = !Supabase.userId.isNullOrBlank()
        if (favoriteButton.isEnabled) lifecycleScope.launch {
            runCatching { growthRepository.isFavorite(product.id) }.onSuccess { value ->
                favoriteState = value
                favoriteButton.text = if (value) "محفوظ ✓" else "حفظ"
            }
        } else favoriteButton.text = "سجلي الدخول للحفظ"
        favoriteButton.setOnClickListener {
            lifecycleScope.launch {
                val target = !favoriteState
                runCatching { growthRepository.setFavorite(product.id, target) }
                    .onSuccess { favoriteState = target; favoriteButton.text = if (target) "محفوظ ✓" else "حفظ" }
                    .onFailure { Toast.makeText(requireContext(), it.message ?: "تعذر تحديث المفضلة", Toast.LENGTH_LONG).show() }
            }
        }
        shareButton.setOnClickListener { ShareHelper.product(requireContext(), product.id, product.name) }

        val storeButton = view.findViewById<Button>(R.id.product_details_store)
        storeButton.isEnabled = details.store != null
        storeButton.setOnClickListener {
            details.store?.let { store ->
                (activity as MainActivity).show(StoreDetailsFragment.newInstance(store.id))
            }
        }

        val variantsBox = view.findViewById<LinearLayout>(R.id.product_details_variants)
        variantsBox.removeAllViews()
        if (details.variants.isEmpty()) {
            variantsBox.addView(MarketplaceUi.empty(requireContext(), "لا توجد خيارات إضافية لهذا المنتج"))
        } else details.variants.forEach { variant ->
            val price = variant.price?.let { " • ${MarketplaceUi.formatPrice(it)}" }.orEmpty()
            variantsBox.addView(
                MarketplaceUi.text(
                    requireContext(),
                    "${variant.name}$price • ${if (variant.stock > 0) "متوفر" else "غير متوفر"}",
                    14f,
                    false,
                    R.color.text_dark
                )
            )
        }

        val reviewsBox = view.findViewById<LinearLayout>(R.id.product_details_reviews)
        reviewsBox.removeAllViews()
        if (details.reviews.isEmpty()) {
            reviewsBox.addView(MarketplaceUi.empty(requireContext(), "لا توجد تقييمات بعد"))
        } else details.reviews.take(8).forEach { review ->
            val stars = "★".repeat(review.rating.coerceIn(1, 5))
            val text = if (review.comment.isBlank()) stars else "$stars\n${review.comment}"
            val reviewView = MarketplaceUi.text(requireContext(), text, 14f, false, R.color.text_dark)
                .apply { setPadding(0, 10, 0, 10) }
            reviewsBox.addView(reviewView)
        }

        val relatedBox = view.findViewById<LinearLayout>(R.id.product_details_related)
        relatedBox.removeAllViews()
        if (details.related.isEmpty()) {
            relatedBox.addView(MarketplaceUi.empty(requireContext(), "لا توجد منتجات مشابهة حالياً"))
        } else details.related.forEach { related ->
            MarketplaceUi.addWithSpacing(
                relatedBox,
                MarketplaceUi.productCard(
                    requireContext(), lifecycleScope, related,
                    onOpen = { (activity as MainActivity).show(newInstance(related.id)) },
                    onAdd = {
                        Cart.add(related.toProduct())
                        Toast.makeText(requireContext(), "تمت إضافة ${related.name} للسلة", Toast.LENGTH_SHORT).show()
                    }
                ),
                requireContext()
            )
        }
    }

    companion object {
        private const val ARG_PRODUCT_ID = "product_id"
        fun newInstance(productId: String): ProductDetailsFragment = ProductDetailsFragment().apply {
            arguments = Bundle().apply { putString(ARG_PRODUCT_ID, productId) }
        }
    }
}
