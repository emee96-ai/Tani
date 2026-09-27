package com.tani.app.ui.home

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import androidx.lifecycle.LifecycleCoroutineScope
import com.tani.app.data.ProductCard
import com.tani.app.ui.marketplace.MarketplaceUi

/**
 * Home product presentation delegates to the shared marketplace card so the same
 * image sizing, availability, rating, favorite and cart behavior is used everywhere.
 */
object HomeProductUi {
    fun productCard(
        context: Context,
        scope: LifecycleCoroutineScope,
        product: ProductCard,
        onOpen: () -> Unit,
        onAdd: () -> Unit
    ): View = MarketplaceUi.productCard(
        context = context,
        scope = scope,
        product = product,
        onOpen = onOpen,
        onAdd = onAdd
    )

    fun addGrid(container: LinearLayout, views: List<View>, context: Context) {
        MarketplaceUi.addTwoColumnGrid(container, views, context)
    }
}
