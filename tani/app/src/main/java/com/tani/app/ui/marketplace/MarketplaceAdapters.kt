package com.tani.app.ui.marketplace

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.tani.app.data.ProductCard
import com.tani.app.data.StoreCard

class ProductListAdapter(
    private val context: Context,
    private val scope: LifecycleCoroutineScope,
    private val onOpen: (ProductCard) -> Unit,
    private val onAdd: (ProductCard) -> Unit
) : RecyclerView.Adapter<ProductListAdapter.ProductHolder>() {
    private val items = mutableListOf<ProductCard>()

    fun submitList(values: List<ProductCard>) {
        replaceItems(this, items, values) { it.id }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductHolder {
        val frame = FrameLayout(context).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = MarketplaceUi.dp(context, 5)
                marginEnd = MarketplaceUi.dp(context, 5)
                bottomMargin = MarketplaceUi.dp(context, 12)
            }
        }
        return ProductHolder(frame)
    }

    override fun onBindViewHolder(holder: ProductHolder, position: Int) {
        val item = items[position]
        holder.container.removeAllViews()
        holder.container.addView(
            MarketplaceUi.productCard(
                context,
                scope,
                item,
                onOpen = { onOpen(item) },
                onAdd = { onAdd(item) }
            ),
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
    }

    override fun getItemCount(): Int = items.size

    class ProductHolder(val container: FrameLayout) : RecyclerView.ViewHolder(container)
}

class StoreListAdapter(
    private val context: Context,
    private val scope: LifecycleCoroutineScope,
    private val onOpen: (StoreCard) -> Unit
) : RecyclerView.Adapter<StoreListAdapter.StoreHolder>() {
    private val items = mutableListOf<StoreCard>()

    fun submitList(values: List<StoreCard>) {
        replaceItems(this, items, values) { it.id }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StoreHolder {
        val frame = FrameLayout(context).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = MarketplaceUi.dp(context, 12) }
        }
        return StoreHolder(frame)
    }

    override fun onBindViewHolder(holder: StoreHolder, position: Int) {
        val item = items[position]
        holder.container.removeAllViews()
        holder.container.addView(
            MarketplaceUi.storeCard(context, scope, item) { onOpen(item) },
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
    }

    override fun getItemCount(): Int = items.size

    class StoreHolder(val container: FrameLayout) : RecyclerView.ViewHolder(container)
}

private fun <T> replaceItems(
    adapter: RecyclerView.Adapter<*>,
    items: MutableList<T>,
    values: List<T>,
    key: (T) -> String
) {
    val old = items.toList()
    val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
        override fun getOldListSize() = old.size
        override fun getNewListSize() = values.size
        override fun areItemsTheSame(oldPosition: Int, newPosition: Int) = key(old[oldPosition]) == key(values[newPosition])
        override fun areContentsTheSame(oldPosition: Int, newPosition: Int) = old[oldPosition] == values[newPosition]
    })
    items.clear()
    items.addAll(values)
    diff.dispatchUpdatesTo(adapter)
}
