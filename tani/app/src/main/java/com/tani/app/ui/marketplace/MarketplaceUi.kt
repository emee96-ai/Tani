package com.tani.app.ui.marketplace

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleCoroutineScope
import com.tani.app.R
import com.tani.app.data.ProductCard
import com.tani.app.data.Repository
import com.tani.app.data.StoreCard
import com.tani.app.data.Supabase
import kotlinx.coroutines.launch
import java.util.Locale

object MarketplaceUi {
    private val repository = Repository()
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
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(context, 10), dp(context, 10), dp(context, 10), dp(context, 10))
            background = ContextCompat.getDrawable(context, R.drawable.bg_card)
            isClickable = true
            isFocusable = true
            contentDescription = "${product.name}، ${formatPrice(product.price)}"
            setOnClickListener { onOpen() }
        }

        val image = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(context, 142)
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(R.drawable.ic_image_placeholder)
            setBackgroundColor(ContextCompat.getColor(context, R.color.brand_soft))
            contentDescription = product.name
        }
        repository.productImageUrl(product.image)?.let { loadImage(scope, image, it) }

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
        footer.addView(Button(context).apply {
            text = if (product.stock > 0) "+" else "×"
            isEnabled = product.stock > 0
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setAllCaps(false)
            setPadding(0, 0, 0, 0)
            setOnClickListener { onAdd() }
        }, LinearLayout.LayoutParams(dp(context, 40), dp(context, 40)))

        card.addView(image)
        card.addView(title, marginTop(context, 10))
        card.addView(meta, marginTop(context, 4))
        card.addView(footer, marginTop(context, 7))
        return card
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
            val bitmap = decodeSampledBitmap(bytes, 900) ?: return@launch
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
