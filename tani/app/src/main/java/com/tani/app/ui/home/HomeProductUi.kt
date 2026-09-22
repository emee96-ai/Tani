package com.tani.app.ui.home

import android.content.Context
import android.text.TextUtils
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleCoroutineScope
import com.google.android.material.card.MaterialCardView
import com.tani.app.R
import com.tani.app.data.ProductCard
import com.tani.app.data.Repository
import com.tani.app.ui.marketplace.MarketplaceUi

/** Home-only product presentation.
 *
 * Keeps product cards lightweight: adaptive image height, short copy and price only.
 * Cart and favorite actions stay inside product details.
 */
object HomeProductUi {
    private val repository = Repository()

    fun productCard(
        context: Context,
        scope: LifecycleCoroutineScope,
        product: ProductCard,
        onOpen: () -> Unit
    ): View {
        val card = MaterialCardView(context).apply {
            radius = MarketplaceUi.dp(context, 14).toFloat()
            cardElevation = 0f
            strokeWidth = MarketplaceUi.dp(context, 1)
            strokeColor = ContextCompat.getColor(context, R.color.border)
            setCardBackgroundColor(ContextCompat.getColor(context, R.color.tani_surface))
            clipToOutline = true
            isClickable = true
            isFocusable = true
            contentDescription = "${product.name}، ${MarketplaceUi.formatPrice(product.price)}"
            setOnClickListener { onOpen() }
        }

        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val image = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            adjustViewBounds = true
            minimumHeight = MarketplaceUi.dp(context, 120)
            maxHeight = MarketplaceUi.dp(context, 250)
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(R.drawable.ic_image_placeholder)
            setBackgroundColor(ContextCompat.getColor(context, R.color.brand_soft))
            contentDescription = product.name
        }
        repository.productImageUrl(product.image)?.let { MarketplaceUi.loadImage(scope, image, it) }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                MarketplaceUi.dp(context, 10),
                MarketplaceUi.dp(context, 9),
                MarketplaceUi.dp(context, 10),
                MarketplaceUi.dp(context, 10)
            )
        }

        val title = MarketplaceUi.text(context, product.name, 14.5f, true, R.color.text_dark).apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        content.addView(title)

        product.description.trim().takeIf { it.isNotEmpty() }?.let { description ->
            val descriptionView = MarketplaceUi.text(
                context,
                description,
                12f,
                false,
                R.color.text_muted
            ).apply {
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
            }
            content.addView(
                descriptionView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = MarketplaceUi.dp(context, 4) }
            )
        }

        val price = MarketplaceUi.text(
            context,
            MarketplaceUi.formatPrice(product.price),
            15.5f,
            true,
            R.color.tani_secondary
        ).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        content.addView(
            price,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = MarketplaceUi.dp(context, 7) }
        )

        body.addView(image)
        body.addView(content)
        card.addView(body)
        return card
    }

    fun addMasonryGrid(container: LinearLayout, views: List<View>, context: Context) {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val firstColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        val secondColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        row.addView(
            firstColumn,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = MarketplaceUi.dp(context, 5)
            }
        )
        row.addView(
            secondColumn,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = MarketplaceUi.dp(context, 5)
            }
        )

        views.forEachIndexed { index, view ->
            val column = if (index % 2 == 0) firstColumn else secondColumn
            column.addView(
                view,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = MarketplaceUi.dp(context, 10) }
            )
        }

        container.addView(
            row,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
    }
}
