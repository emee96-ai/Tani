package com.tani.app.ui.marketplace

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleCoroutineScope
import com.google.android.material.card.MaterialCardView
import com.tani.app.R
import com.tani.app.data.ProductCard
import com.tani.app.data.Repository
import com.tani.app.data.StoreCard
import com.tani.app.data.Supabase
import com.tani.app.data.repository.GrowthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale

object MarketplaceUi {
    private val repository = Repository()
    private val growthRepository = GrowthRepository()
    private val favoriteMutex = Mutex()
    private val favoriteIdsCache = mutableSetOf<String>()
    private var favoriteCacheUser: String? = null
    private var favoriteCacheReady = false

    private val imageCache = object : android.util.LruCache<String, Bitmap>(16 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = (value.byteCount / 1024).coerceAtLeast(1)
    }

    fun productCard(
        context: Context,
        scope: LifecycleCoroutineScope,
        product: ProductCard,
        onOpen: () -> Unit,
        onAdd: () -> Unit
    ): View {
        val card = MaterialCardView(context).apply {
            radius = dp(context, 18).toFloat()
            cardElevation = 0f
            strokeWidth = dp(context, 1)
            strokeColor = ContextCompat.getColor(context, R.color.border)
            setCardBackgroundColor(ContextCompat.getColor(context, R.color.tani_surface))
            clipToOutline = true
            isClickable = true
            isFocusable = true
            contentDescription = "${product.name}، ${formatPrice(product.price)}"
            setOnClickListener { onOpen() }
        }

        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val imageFrame = FrameLayout(context).apply {
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(context, 150)
            )
        }
        val image = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(R.drawable.ic_image_placeholder)
            setBackgroundColor(ContextCompat.getColor(context, R.color.brand_soft))
            contentDescription = product.name
        }
        imageFrame.addView(
            image,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        repository.productImageUrl(product.image)?.let { loadImage(scope, image, it) }

        var favoriteActive = false
        val favoriteButton = ImageButton(context).apply {
            setImageResource(R.drawable.ic_favorite_outline)
            background = favoriteCircleBackground(context)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(context, 9), dp(context, 9), dp(context, 9), dp(context, 9))
            elevation = dp(context, 3).toFloat()
            contentDescription = "إضافة ${product.name} للمفضلة"
        }
        imageFrame.addView(
            favoriteButton,
            FrameLayout.LayoutParams(dp(context, 40), dp(context, 40), Gravity.TOP or Gravity.END).apply {
                setMargins(dp(context, 9), dp(context, 9), dp(context, 9), dp(context, 9))
            }
        )

        if (!Supabase.userId.isNullOrBlank()) {
            scope.launch {
                runCatching { favoriteState(product.id) }.onSuccess { state ->
                    favoriteActive = state
                    updateFavoriteButton(favoriteButton, state, product.name)
                }
            }
        }
        favoriteButton.setOnClickListener {
            if (Supabase.userId.isNullOrBlank()) {
                Toast.makeText(context, "سجلي الدخول لإضافة المنتجات للمفضلة", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val target = !favoriteActive
            favoriteButton.isEnabled = false
            scope.launch {
                runCatching {
                    growthRepository.setFavorite(product.id, target)
                    updateFavoriteCache(product.id, target)
                    target
                }.onSuccess { state ->
                    favoriteActive = state
                    updateFavoriteButton(favoriteButton, state, product.name)
                    Toast.makeText(
                        context,
                        if (state) "تمت الإضافة للمفضلة" else "تمت الإزالة من المفضلة",
                        Toast.LENGTH_SHORT
                    ).show()
                }.onFailure {
                    Toast.makeText(context, it.message ?: "تعذر تحديث المفضلة", Toast.LENGTH_SHORT).show()
                }
                favoriteButton.isEnabled = true
            }
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 12))
        }

        val title = text(context, product.name, 15f, true, R.color.text_dark).apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        val ratingText = if (product.review_count > 0) {
            String.format(Locale.US, "★ %.1f (%d)", product.average_rating, product.review_count)
        } else "جديد"
        val meta = text(context, "${product.store_name} • $ratingText", 11.5f, false, R.color.text_muted).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        val price = text(context, formatPrice(product.price), 16f, true, R.color.tani_secondary).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val footer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        footer.addView(price, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val canAdd = product.stock > 0
        footer.addView(ImageButton(context).apply {
            setImageResource(R.drawable.ic_cart_plus)
            background = cartButtonBackground(context, canAdd)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(context, 10), dp(context, 10), dp(context, 10), dp(context, 10))
            isEnabled = canAdd
            alpha = if (canAdd) 1f else 0.45f
            contentDescription = if (canAdd) "إضافة ${product.name} للسلة" else "المنتج غير متوفر"
            setOnClickListener { onAdd() }
        }, LinearLayout.LayoutParams(dp(context, 44), dp(context, 44)))

        content.addView(title)
        content.addView(meta, marginTop(context, 4))
        content.addView(footer, marginTop(context, 8))

        body.addView(imageFrame)
        body.addView(content)
        card.addView(body)
        return card
    }

    private suspend fun favoriteState(productId: String): Boolean {
        val uid = Supabase.userId ?: return false
        return favoriteMutex.withLock {
            if (!favoriteCacheReady || favoriteCacheUser != uid) {
                favoriteIdsCache.clear()
                favoriteIdsCache.addAll(growthRepository.favoriteIds())
                favoriteCacheUser = uid
                favoriteCacheReady = true
            }
            favoriteIdsCache.contains(productId)
        }
    }

    private suspend fun updateFavoriteCache(productId: String, favorite: Boolean) {
        favoriteMutex.withLock {
            val uid = Supabase.userId
            if (favoriteCacheUser != uid) {
                favoriteIdsCache.clear()
                favoriteCacheUser = uid
            }
            if (favorite) favoriteIdsCache.add(productId) else favoriteIdsCache.remove(productId)
            favoriteCacheReady = true
        }
    }

    private fun updateFavoriteButton(button: ImageButton, favorite: Boolean, productName: String) {
        button.setImageResource(if (favorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_outline)
        button.contentDescription = if (favorite) "إزالة $productName من المفضلة" else "إضافة $productName للمفضلة"
    }

    private fun favoriteCircleBackground(context: Context) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(ContextCompat.getColor(context, R.color.tani_surface))
        setStroke(dp(context, 1), ContextCompat.getColor(context, R.color.border))
    }

    private fun cartButtonBackground(context: Context, enabled: Boolean) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(context, 14).toFloat()
        setColor(ContextCompat.getColor(context, if (enabled) R.color.tani_primary else R.color.border))
    }

    fun storeCard(
        context: Context,
        scope: LifecycleCoroutineScope,
        store: StoreCard,
        onOpen: () -> Unit
    ): View {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(context, 14), dp(context, 14), dp(context, 14), dp(context, 14))
            background = ContextCompat.getDrawable(context, R.drawable.bg_card)
            isClickable = true
            isFocusable = true
            contentDescription = "فتح متجر ${store.name}"
            setOnClickListener { onOpen() }
        }

        val logo = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(context, 64), dp(context, 64))
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(R.drawable.ic_image_placeholder)
            background = ContextCompat.getDrawable(context, R.drawable.bg_avatar)
        }
        repository.storeImageUrl(store.logo_url)?.let { loadImage(scope, logo, it) }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(context, 12)
            }
        }

        val status = if (store.is_open) "مفتوح الآن" else "مغلق حالياً"
        val rating = if (store.review_count > 0) {
            String.format(Locale.US, "★ %.1f (%d)", store.average_rating, store.review_count)
        } else "جديد"
        val trust = verificationLabel(store.verification_status)
        content.addView(text(context, store.name, 18f, true, R.color.text_dark))
        content.addView(
            text(
                context,
                listOfNotNull(trust, status, rating).joinToString(" • "),
                13f,
                false,
                R.color.text_muted
            ),
            marginTop(context, 4)
        )
        content.addView(text(context, "${store.city}${store.area?.let { " - $it" } ?: ""}", 13f, false, R.color.text_muted), marginTop(context, 4))
        content.addView(text(context, "${store.product_count} منتج", 12f, false, R.color.text_muted), marginTop(context, 4))

        card.addView(logo)
        card.addView(content)
        card.addView(text(context, "عرض", 13f, true, R.color.tani_primary).apply {
            setPadding(dp(context, 8), dp(context, 12), dp(context, 8), dp(context, 12))
        })
        return card
    }

    fun chipButton(context: Context, value: String, onClick: () -> Unit): Button = Button(context).apply {
        text = value
        background = ContextCompat.getDrawable(context, R.drawable.bg_chip)
        setTextColor(ContextCompat.getColor(context, R.color.text_dark))
        minHeight = dp(context, 48)
        minimumHeight = dp(context, 48)
        setAllCaps(false)
        setPadding(dp(context, 14), dp(context, 8), dp(context, 14), dp(context, 8))
        setOnClickListener { onClick() }
    }

    fun chipLabel(context: Context, value: String): TextView = text(context, value, 12f, true, R.color.tani_primary).apply {
        background = ContextCompat.getDrawable(context, R.drawable.bg_chip)
        setPadding(dp(context, 10), dp(context, 6), dp(context, 10), dp(context, 6))
    }

    fun verificationLabel(status: String?): String? = when (status?.lowercase()) {
        "approved", "verified" -> "موثق ✓"
        "pending", "under_review" -> "قيد التحقق"
        "restricted" -> "مقيّد"
        else -> null
    }

    fun text(context: Context, value: String, size: Float, bold: Boolean, colorRes: Int): TextView =
        TextView(context).apply {
            text = value
            textSize = size
            setTextColor(ContextCompat.getColor(context, colorRes))
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    fun empty(context: Context, value: String): TextView = text(
        context,
        value,
        15f,
        false,
        R.color.text_muted
    ).apply {
        setPadding(0, dp(context, 18), 0, dp(context, 18))
        gravity = Gravity.CENTER_HORIZONTAL
    }

    fun formatPrice(value: Double): String =
        if (value % 1.0 == 0.0) "${value.toLong()} جنيه" else String.format(Locale.US, "%.2f جنيه", value)

    fun addWithSpacing(container: LinearLayout, view: View, context: Context, bottomDp: Int = 12) {
        container.addView(
            view,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(context, bottomDp) }
        )
    }

    fun addTwoColumnGrid(container: LinearLayout, views: List<View>, context: Context) {
        views.chunked(2).forEach { pair ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
            }
            pair.forEachIndexed { index, item ->
                row.addView(
                    item,
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    ).apply {
                        if (index == 0) marginEnd = dp(context, 10)
                    }
                )
            }
            if (pair.size == 1) {
                row.addView(
                    View(context),
                    LinearLayout.LayoutParams(0, 1, 1f).apply {
                        marginStart = dp(context, 10)
                    }
                )
            }
            container.addView(
                row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(context, 12) }
            )
        }
    }

    fun loadImage(scope: LifecycleCoroutineScope, imageView: ImageView, url: String) {
        imageCache.get(url)?.let {
            imageView.setImageBitmap(it)
            return
        }
        scope.launch {
            val bytes = Supabase.downloadPublicBytes(url) ?: return@launch
            val bitmap = withContext(Dispatchers.Default) {
                decodeSampledBitmap(bytes, 900)
            } ?: return@launch
            imageCache.put(url, bitmap)
            imageView.setImageBitmap(bitmap)
        }
    }

    private fun decodeSampledBitmap(bytes: ByteArray, maxDimension: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1) }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun marginTop(context: Context, topDp: Int) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(context, topDp) }

    fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
