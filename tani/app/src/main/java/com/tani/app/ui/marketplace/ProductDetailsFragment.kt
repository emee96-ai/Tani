package com.tani.app.ui.marketplace

import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.MediaController
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
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

    private var activeVideo: VideoView? = null
    private var mediaItems: List<MediaItem> = emptyList()
    private var selectedMediaIndex = 0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val productId = requireArguments().getString(ARG_PRODUCT_ID) ?: return
        val loading = view.findViewById<TextView>(R.id.product_details_loading)
        val content = view.findViewById<LinearLayout>(R.id.product_details_content)

        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { repository.productDetails(productId) }
                .onSuccess { details ->
                    loading.visibility = View.GONE
                    content.visibility = View.VISIBLE
                    bind(view, details)
                }
                .onFailure { loading.text = "تعذر تحميل المنتج\n${it.message ?: "حاولي مرة أخرى"}" }
        }
    }

    override fun onDestroyView() {
        runCatching { activeVideo?.stopPlayback() }
        activeVideo = null
        mediaItems = emptyList()
        super.onDestroyView()
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

        view.findViewById<TextView>(R.id.product_details_rating).text =
            if (product.review_count > 0) {
                String.format(Locale.US, "★ %.1f من 5 (%d تقييم)", product.average_rating, product.review_count)
            } else "منتج جديد • لا توجد تقييمات بعد"

        setupMediaGallery(view, details)
        setupFavoriteAndShare(view, product.id, product.name)
        setupQuantityAndCart(view, details)
        bindTrustAndDelivery(view, details, verification)
        bindStore(view, details)
        bindVariants(view, details)
        bindReviews(view, details)
        bindRelated(view, details)
    }

    private fun setupMediaGallery(view: View, details: ProductDetails) {
        val paths = details.images.map { it.storage_path }.distinct().toMutableList()
        val legacyImage = details.product.image?.takeIf { it.isNotBlank() }
        if (paths.none { mediaKind(it) == MediaKind.IMAGE } && legacyImage != null && legacyImage !in paths) {
            paths.add(0, legacyImage)
        }
        if (paths.isEmpty() && legacyImage != null) paths.add(legacyImage)

        mediaItems = paths.take(12).map { MediaItem(it, mediaKind(it)) }
        selectedMediaIndex = 0
        renderSelectedMedia(view)
        renderMediaThumbnails(view)
    }

    private fun renderSelectedMedia(view: View) {
        val image = view.findViewById<ImageView>(R.id.product_details_image)
        val video = view.findViewById<VideoView>(R.id.product_details_video)
        val play = view.findViewById<ImageButton>(R.id.product_details_play)
        val counter = view.findViewById<TextView>(R.id.product_details_media_counter)
        activeVideo = video

        counter.visibility = if (mediaItems.isEmpty()) View.GONE else View.VISIBLE
        if (mediaItems.isNotEmpty()) counter.text = "${selectedMediaIndex + 1} / ${mediaItems.size}"

        if (mediaItems.isEmpty()) {
            runCatching { video.stopPlayback() }
            video.visibility = View.GONE
            play.visibility = View.GONE
            image.visibility = View.VISIBLE
            image.setImageResource(R.drawable.ic_image_placeholder)
            return
        }

        val item = mediaItems[selectedMediaIndex.coerceIn(mediaItems.indices)]
        val url = repository.productImageUrl(item.path)
        if (item.kind == MediaKind.IMAGE) {
            runCatching { video.stopPlayback() }
            video.visibility = View.GONE
            play.visibility = View.GONE
            image.visibility = View.VISIBLE
            image.setImageResource(R.drawable.ic_image_placeholder)
            if (url != null) MarketplaceUi.loadImage(viewLifecycleOwner.lifecycleScope, image, url)
            image.contentDescription = "صورة المنتج ${selectedMediaIndex + 1} من ${mediaItems.size}"
            return
        }

        image.visibility = View.GONE
        video.visibility = View.VISIBLE
        play.visibility = View.VISIBLE
        video.setMediaController(MediaController(requireContext()).apply { setAnchorView(video) })
        if (url == null) {
            play.visibility = View.GONE
            Toast.makeText(requireContext(), "تعذر فتح الفيديو", Toast.LENGTH_SHORT).show()
            return
        }
        video.setVideoURI(Uri.parse(url))
        video.setOnPreparedListener { player ->
            player.isLooping = false
            runCatching { video.seekTo(1) }
            play.visibility = View.VISIBLE
        }
        video.setOnCompletionListener { play.visibility = View.VISIBLE }
        video.setOnErrorListener { _, _, _ ->
            play.visibility = View.GONE
            Toast.makeText(requireContext(), "تعذر تشغيل هذا الفيديو", Toast.LENGTH_SHORT).show()
            true
        }
        val togglePlayback = {
            if (video.isPlaying) {
                video.pause()
                play.visibility = View.VISIBLE
            } else {
                video.start()
                play.visibility = View.GONE
            }
        }
        play.setOnClickListener { togglePlayback() }
        video.setOnClickListener { togglePlayback() }
    }

    private fun renderMediaThumbnails(view: View) {
        val gallery = view.findViewById<LinearLayout>(R.id.product_details_images)
        val scroller = view.findViewById<View>(R.id.product_details_media_scroller)
        gallery.removeAllViews()
        scroller.visibility = if (mediaItems.size > 1) View.VISIBLE else View.GONE

        mediaItems.forEachIndexed { index, item ->
            val selected = index == selectedMediaIndex
            val card = MaterialCardView(requireContext()).apply {
                radius = MarketplaceUi.dp(requireContext(), 12).toFloat()
                cardElevation = 0f
                strokeWidth = MarketplaceUi.dp(requireContext(), if (selected) 2 else 1)
                strokeColor = ContextCompat.getColor(requireContext(), if (selected) R.color.tani_primary else R.color.border)
                setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.brand_soft))
                isClickable = true
                contentDescription = if (item.kind == MediaKind.VIDEO) "عرض فيديو المنتج" else "عرض صورة المنتج ${index + 1}"
                setOnClickListener {
                    if (selectedMediaIndex != index) {
                        selectedMediaIndex = index
                        renderSelectedMedia(view)
                        renderMediaThumbnails(view)
                    }
                }
            }

            if (item.kind == MediaKind.IMAGE) {
                val thumb = ImageView(requireContext()).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setImageResource(R.drawable.ic_image_placeholder)
                }
                repository.productImageUrl(item.path)?.let {
                    MarketplaceUi.loadImage(viewLifecycleOwner.lifecycleScope, thumb, it)
                }
                card.addView(thumb, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            } else {
                val videoThumb = TextView(requireContext()).apply {
                    text = "▶\nفيديو"
                    gravity = Gravity.CENTER
                    textSize = 13f
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.tani_primary))
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                }
                card.addView(videoThumb, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            }

            gallery.addView(
                card,
                LinearLayout.LayoutParams(
                    MarketplaceUi.dp(requireContext(), 78),
                    MarketplaceUi.dp(requireContext(), 78)
                ).apply { marginEnd = MarketplaceUi.dp(requireContext(), 8) }
            )
        }
    }

    private fun setupFavoriteAndShare(view: View, productId: String, productName: String) {
        val favoriteButton = view.findViewById<ImageButton>(R.id.product_details_favorite)
        val shareButton = view.findViewById<ImageButton>(R.id.product_details_share)
        var favoriteState = false

        fun renderFavorite() {
            favoriteButton.setImageResource(if (favoriteState) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_outline)
            favoriteButton.contentDescription = if (favoriteState) "إزالة $productName من المفضلة" else "إضافة $productName للمفضلة"
        }
        renderFavorite()

        if (!Supabase.userId.isNullOrBlank()) {
            viewLifecycleOwner.lifecycleScope.launch {
                runCatching { growthRepository.isFavorite(productId) }.onSuccess { value ->
                    favoriteState = value
                    renderFavorite()
                }
            }
        }

        favoriteButton.setOnClickListener {
            if (Supabase.userId.isNullOrBlank()) {
                Toast.makeText(requireContext(), "سجلي الدخول لإضافة المنتج للمفضلة", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            favoriteButton.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                val target = !favoriteState
                runCatching { growthRepository.setFavorite(productId, target) }
                    .onSuccess {
                        favoriteState = target
                        renderFavorite()
                        Toast.makeText(
                            requireContext(),
                            if (target) "تمت الإضافة للمفضلة" else "تمت الإزالة من المفضلة",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    .onFailure { Toast.makeText(requireContext(), it.message ?: "تعذر تحديث المفضلة", Toast.LENGTH_LONG).show() }
                favoriteButton.isEnabled = true
            }
        }
        shareButton.setOnClickListener { ShareHelper.product(requireContext(), productId, productName) }
    }

    private fun setupQuantityAndCart(view: View, details: ProductDetails) {
        val product = details.product
        val minus = view.findViewById<Button>(R.id.product_details_quantity_minus)
        val plus = view.findViewById<Button>(R.id.product_details_quantity_plus)
        val quantityText = view.findViewById<TextView>(R.id.product_details_quantity_value)
        val quantityBox = view.findViewById<View>(R.id.product_details_quantity_box)
        val addButton = view.findViewById<MaterialButton>(R.id.product_details_add)
        var quantity = 1

        fun availableToAdd(): Int {
            val current = Cart.all().firstOrNull { it.product.id == product.id }?.quantity ?: 0
            return (product.stock - current).coerceAtLeast(0).coerceAtMost(99)
        }

        fun refreshQuantityUi() {
            val available = availableToAdd()
            quantity = quantity.coerceIn(1, available.coerceAtLeast(1))
            quantityText.text = quantity.toString()
            quantityBox.visibility = if (product.stock > 0) View.VISIBLE else View.GONE
            minus.isEnabled = available > 0 && quantity > 1
            plus.isEnabled = available > 0 && quantity < available
            addButton.isEnabled = available > 0
            addButton.text = when {
                product.stock <= 0 -> "غير متوفر حالياً"
                available <= 0 -> "الكمية المتاحة موجودة في السلة"
                else -> "إضافة للسلة • ${MarketplaceUi.formatPrice(product.price * quantity)}"
            }
        }

        minus.setOnClickListener {
            if (quantity > 1) quantity--
            refreshQuantityUi()
        }
        plus.setOnClickListener {
            if (quantity < availableToAdd()) quantity++
            refreshQuantityUi()
        }
        addButton.setOnClickListener {
            val wanted = quantity.coerceAtMost(availableToAdd())
            var added = 0
            repeat(wanted) {
                if (Cart.add(product.toProduct())) added++
            }
            if (added > 0) {
                (activity as? MainActivity)?.refreshCartBadge()
                viewLifecycleOwner.lifecycleScope.launch {
                    Analytics.track("add_to_cart", screen = "product_details", entityType = "product", entityId = product.id)
                }
                Toast.makeText(requireContext(), "تمت إضافة $added من ${product.name} للسلة", Toast.LENGTH_SHORT).show()
                quantity = 1
            } else {
                Toast.makeText(requireContext(), "تعذر إضافة كمية إضافية", Toast.LENGTH_SHORT).show()
            }
            refreshQuantityUi()
        }
        refreshQuantityUi()
    }

    private fun bindTrustAndDelivery(view: View, details: ProductDetails, verification: String?) {
        val product = details.product
        val trustText = view.findViewById<TextView>(R.id.product_details_trust)
        trustText.text = "جاري حساب مستوى الثقة…"
        viewLifecycleOwner.lifecycleScope.launch {
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

        view.findViewById<TextView>(R.id.product_details_delivery).text = buildString {
            append(if (product.delivery_fee > 0) "رسوم التوصيل: ${MarketplaceUi.formatPrice(product.delivery_fee)}" else "رسوم التوصيل يحددها المتجر")
            product.delivery_area?.takeIf { it.isNotBlank() }?.let { append("\nمنطقة التوصيل: $it") }
            product.estimated_minutes?.let { append("\nالوقت التقديري: $it دقيقة") }
        }
    }

    private fun bindStore(view: View, details: ProductDetails) {
        val storeButton = view.findViewById<MaterialButton>(R.id.product_details_store)
        storeButton.isEnabled = details.store != null
        storeButton.text = details.store?.let { "زيارة متجر ${it.name}" } ?: "المتجر غير متاح"
        storeButton.setOnClickListener {
            details.store?.let { store ->
                (activity as MainActivity).show(StoreDetailsFragment.newInstance(store.id))
            }
        }
    }

    private fun bindVariants(view: View, details: ProductDetails) {
        val section = view.findViewById<LinearLayout>(R.id.product_details_variants_section)
        val variantNote = view.findViewById<TextView>(R.id.product_details_variant_note)
        val variantsBox = view.findViewById<LinearLayout>(R.id.product_details_variants)
        variantsBox.removeAllViews()
        section.visibility = if (details.variants.isEmpty()) View.GONE else View.VISIBLE
        if (details.variants.isNotEmpty()) {
            variantNote.text = "اختاري من الخيارات المتاحة، ويؤكد المتجر تفاصيل الخيار مع الطلب."
            details.variants.forEach { variant ->
                val price = variant.price?.let { " • ${MarketplaceUi.formatPrice(it)}" }.orEmpty()
                val availability = if (variant.stock > 0) "متوفر" else "غير متوفر"
                val chip = MarketplaceUi.chipLabel(requireContext(), "${variant.name}$price • $availability").apply {
                    alpha = if (variant.stock > 0) 1f else 0.5f
                }
                variantsBox.addView(
                    chip,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { marginEnd = MarketplaceUi.dp(requireContext(), 8) }
                )
            }
        }
    }

    private fun bindReviews(view: View, details: ProductDetails) {
        val reviewsBox = view.findViewById<LinearLayout>(R.id.product_details_reviews)
        reviewsBox.removeAllViews()
        if (details.reviews.isEmpty()) {
            reviewsBox.addView(MarketplaceUi.empty(requireContext(), "لا توجد تقييمات بعد"))
        } else details.reviews.take(8).forEach { review ->
            val stars = "★".repeat(review.rating.coerceIn(1, 5))
            val reviewText = if (review.comment.isBlank()) stars else "$stars\n${review.comment}"
            reviewsBox.addView(
                MarketplaceUi.text(requireContext(), reviewText, 14f, false, R.color.text_dark).apply {
                    background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_card_soft)
                    setPadding(
                        MarketplaceUi.dp(requireContext(), 12),
                        MarketplaceUi.dp(requireContext(), 10),
                        MarketplaceUi.dp(requireContext(), 12),
                        MarketplaceUi.dp(requireContext(), 10)
                    )
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = MarketplaceUi.dp(requireContext(), 8) }
            )
        }
    }

    private fun bindRelated(view: View, details: ProductDetails) {
        val relatedBox = view.findViewById<LinearLayout>(R.id.product_details_related)
        relatedBox.removeAllViews()
        if (details.related.isEmpty()) {
            relatedBox.addView(MarketplaceUi.empty(requireContext(), "لا توجد منتجات مشابهة حالياً"))
            return
        }
        val cards = details.related.map { related ->
            MarketplaceUi.productCard(
                requireContext(), viewLifecycleOwner.lifecycleScope, related,
                onOpen = { (activity as MainActivity).show(newInstance(related.id)) },
                onAdd = {
                    if (Cart.add(related.toProduct())) {
                        (activity as? MainActivity)?.refreshCartBadge()
                        Toast.makeText(requireContext(), "تمت إضافة ${related.name} للسلة", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "تعذر إضافة كمية إضافية", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
        MarketplaceUi.addTwoColumnGrid(relatedBox, cards, requireContext())
    }

    private fun mediaKind(path: String): MediaKind {
        val clean = path.substringBefore('?').substringBefore('#').lowercase(Locale.US)
        return if (
            clean.endsWith(".mp4") || clean.endsWith(".webm") || clean.endsWith(".m4v") || clean.endsWith(".mov")
        ) MediaKind.VIDEO else MediaKind.IMAGE
    }

    private data class MediaItem(val path: String, val kind: MediaKind)
    private enum class MediaKind { IMAGE, VIDEO }

    companion object {
        private const val ARG_PRODUCT_ID = "product_id"
        fun newInstance(productId: String): ProductDetailsFragment = ProductDetailsFragment().apply {
            arguments = Bundle().apply { putString(ARG_PRODUCT_ID, productId) }
        }
    }
}
