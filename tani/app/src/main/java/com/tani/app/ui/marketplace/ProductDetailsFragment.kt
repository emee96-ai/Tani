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
        val productId = requireArguments().getString(ARG_PRODUCT_ID) ?: return
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
        val formattedPrice = MarketplaceUi.formatPrice(product.price)
        view.findViewById<TextView>(R.id.product_details_name).text = product.name
        view.findViewById<TextView>(R.id.product_details_price).text = formattedPrice
        view.findViewById<TextView>(R.id.product_details_sticky_price).text = formattedPrice
        view.findViewById<TextView>(R.id.product_details_description).text =
            product.description.ifBlank { "لا يوجد وصف إضافي لهذا المنتج." }

        view.findViewById<TextView>(R.id.product_details_stock).apply {
            text = if (product.stock > 0) "متوفر (${product.stock})" else "غير متوفر حالياً"
            setTextColor(requireContext().getColor(if (product.stock > 0) R.color.success else R.color.error))
        }

        val verification = MarketplaceUi.verificationLabel(product.verification_status)
        view.findViewById<TextView>(R.id.product_details_merchant).text = buildString {
            append(product.store_name)
            verification?.let { append(" • $it") }
        }

        val trustText = view.findViewById<TextView>(R.id.product_details_trust)
        trustText.text = "جاري حساب مستوى الثقة…"
        lifecycleScope.launch {
            runCatching { trustRepository.merchantTrust(product.seller_id) }.onSuccess { trust ->
                trustText.text = if (trust == null) {
                    verification?.let { "حالة المتجر: $it" } ?: "لا توجد بيانات ثقة إضافية"
                } else {
                    val level = when (trust.trust_level) {
                        "high_performing" -> "أداء مرتفع"
                        "trusted" -> "موثوق"
                        "restricted" -> "مقيّد"
                        else -> "موثق"
                    }
                    "الثقة: $level • ${String.format(Locale.US, "%.0f", trust.trust_score)}/100 • إكمال ${String.format(Locale.US, "%.0f", trust.completion_rate)}%"
                }
            }.onFailure {
                trustText.text = verification?.let { "حالة المتجر: $it" } ?: "تعذر تحميل مستوى الثقة"
            }
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
        val galleryPaths = details.images.map { it.storage_path }.ifEmpty { listOfNotNull(product.image) }
        galleryPaths.take(8).forEach { path ->
            val thumb = ImageView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    MarketplaceUi.dp(requireContext(), 88),
                    MarketplaceUi.dp(requireContext(), 88)
                ).apply { marginEnd = MarketplaceUi.dp(requireContext(), 8) }
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageResource(R.drawable.ic_image_placeholder)
                setOnClickListener {
                    repository.productImageUrl(path)?.let { url -> MarketplaceUi.loadImage(lifecycleScope, image, url) }
                }
            }
            repository.productImageUrl(path)?.let { MarketplaceUi.loadImage(lifecycleScope, thumb, it) }
            gallery.addView(thumb)
        }

        view.findViewById<Button>(R.id.product_details_add).apply {
            isEnabled = product.stock > 0
            text = if (product.stock > 0) "أضيفي للسلة" else "غير متوفر"
            setOnClickListener {
                if (Cart.add(product.toProduct())) {
                    (activity as? MainActivity)?.refreshCartBadge()
                    lifecycleScope.launch {
                        Analytics.track("add_to_cart", screen = "product_details", entityType = "product", entityId = product.id)
                    }
                    Toast.makeText(requireContext(), "تمت إضافة ${product.name} للسلة", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "تعذر إضافة كمية إضافية", Toast.LENGTH_SHORT).show()
                }
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
        } else favoriteButton.text = "دخول للحفظ"
        favoriteButton.setOnClickListener {
            lifecycleScope.launch {
                val target = !favoriteState
                runCatching { growthRepository.setFavorite(product.id, target) }
                    .onSuccess {
                        favoriteState = target
                        favoriteButton.text = if (target) "محفوظ ✓" else "حفظ"
                    }
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

        val variantNote = view.findViewById<TextView>(R.id.product_details_variant_note)
        val variantsBox = view.findViewById<LinearLayout>(R.id.product_details_variants)
        variantsBox.removeAllViews()
        if (details.variants.isEmpty()) {
            variantNote.text = "لا توجد خيارات إضافية لهذا المنتج."
        } else {
            variantNote.text = "الخيارات المعروضة متاحة من المتجر، ويؤكد المتجر تفاصيل الخيار مع الطلب."
            details.variants.forEach { variant ->
                val price = variant.price?.let { " • ${MarketplaceUi.formatPrice(it)}" }.orEmpty()
                val availability = if (variant.stock > 0) "متوفر" else "غير متوفر"
                val chip = MarketplaceUi.chipLabel(requireContext(), "${variant.name}$price • $availability")
                variantsBox.addView(
                    chip,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { marginEnd = MarketplaceUi.dp(requireContext(), 8) }
                )
            }
        }

        val reviewsBox = view.findViewById<LinearLayout>(R.id.product_details_reviews)
        reviewsBox.removeAllViews()
        if (details.reviews.isEmpty()) {
            reviewsBox.addView(MarketplaceUi.empty(requireContext(), "لا توجد تقييمات بعد"))
        } else details.reviews.take(8).forEach { review ->
            val stars = "★".repeat(review.rating.coerceIn(1, 5))
            val reviewText = if (review.comment.isBlank()) stars else "$stars\n${review.comment}"
            reviewsBox.addView(
                MarketplaceUi.text(requireContext(), reviewText, 14f, false, R.color.text_dark)
                    .apply { setPadding(0, 10, 0, 10) }
            )
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
                        if (Cart.add(related.toProduct())) {
                            (activity as? MainActivity)?.refreshCartBadge()
                            Toast.makeText(requireContext(), "تمت إضافة ${related.name} للسلة", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(requireContext(), "تعذر إضافة كمية إضافية", Toast.LENGTH_SHORT).show()
                        }
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
