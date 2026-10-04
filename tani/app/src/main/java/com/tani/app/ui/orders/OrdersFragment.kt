package com.tani.app.ui.orders

import com.tani.app.data.orderGroups

import com.tani.app.data.network.CustomerErrorMessages

import com.tani.app.util.runCatchingCancellable

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.tani.app.MainActivity
import com.tani.app.R
import com.tani.app.data.OrderGroup
import com.tani.app.data.Repository
import com.tani.app.data.cache.AppContentStore
import com.tani.app.ui.commerce.CommerceUi
import com.tani.app.ui.commerce.OrderDetailsFragment
import com.tani.app.ui.marketplace.MarketplaceUi
import kotlinx.coroutines.launch

class OrdersFragment : Fragment(R.layout.fragment_orders) {
    private val repository = Repository()

    private lateinit var box: LinearLayout
    private lateinit var progress: ProgressBar
    private lateinit var summary: TextView
    private lateinit var allButton: Button
    private lateinit var activeButton: Button
    private lateinit var completedButton: Button
    private lateinit var cancelledButton: Button

    private var groups: List<OrderGroup> = emptyList()
    private var filter: Filter = Filter.ALL

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        box = view.findViewById(R.id.orders_box)
        progress = view.findViewById(R.id.orders_progress)
        summary = view.findViewById(R.id.orders_summary)
        allButton = view.findViewById(R.id.orders_all)
        activeButton = view.findViewById(R.id.orders_active)
        completedButton = view.findViewById(R.id.orders_completed)
        cancelledButton = view.findViewById(R.id.orders_cancelled)

        allButton.setOnClickListener { filter = Filter.ALL; render() }
        activeButton.setOnClickListener { filter = Filter.ACTIVE; render() }
        completedButton.setOnClickListener { filter = Filter.COMPLETED; render() }
        cancelledButton.setOnClickListener { filter = Filter.CANCELLED; render() }
        view.findViewById<Button>(R.id.orders_refresh).setOnClickListener { load(forceRefresh = true) }

        load()
    }

    private fun load(forceRefresh: Boolean = false) {
        if (!forceRefresh && AppContentStore.ordersLoaded) {
            groups = AppContentStore.orders
            progress.visibility = View.GONE
            render()
            return
        }
        progress.visibility = View.VISIBLE
        summary.text = getString(R.string.orders_1)
        viewLifecycleOwner.lifecycleScope.launch {
            runCatchingCancellable { repository.orderGroups() }
                .onSuccess {
                    groups = it
                    AppContentStore.updateOrders(it)
                    render()
                }
                .onFailure {
                    groups = emptyList()
                    box.removeAllViews()
                    summary.text = getString(R.string.orders_2)
                    box.addView(message(CustomerErrorMessages.from(it, getString(R.string.notifications_4))))
                }
            progress.visibility = View.GONE
        }
    }

    private fun render() {
        box.removeAllViews()

        val activeCount = groups.count(::isActive)
        val completedCount = groups.count { it.status == "delivered" }
        val cancelledCount = groups.count(::isCancelledLike)

        allButton.text = getString(R.string.notifications_14, groups.size.toString())
        activeButton.text = getString(R.string.orders_3, activeCount.toString())
        completedButton.text = getString(R.string.orders_4, completedCount.toString())
        cancelledButton.text = getString(R.string.orders_5, cancelledCount.toString())
        updateFilterStyle()

        summary.text = when {
            groups.isEmpty() -> getString(R.string.orders_6)
            activeCount > 0 -> "عندك $activeCount ${if (activeCount == 1) "طلب قيد المتابعة" else "طلبات قيد المتابعة"}"
            else -> getString(R.string.orders_7)
        }

        val shown = groups.filter { group ->
            when (filter) {
                Filter.ALL -> true
                Filter.ACTIVE -> isActive(group)
                Filter.COMPLETED -> group.status == "delivered"
                Filter.CANCELLED -> isCancelledLike(group)
            }
        }

        if (shown.isEmpty()) {
            box.addView(emptyState())
            return
        }

        shown.forEach { group -> box.addView(orderCard(group), cardParams()) }
    }

    private fun orderCard(group: OrderGroup): View {
        val context = requireContext()
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = context.getDrawable(R.drawable.bg_card)
            isClickable = true
            isFocusable = true
            setOnClickListener { openDetails(group.id) }
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        header.addView(TextView(context).apply {
            text = getString(R.string.order_details_1, (group.id.take(8).uppercase()))
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(context.getColor(R.color.text_dark))
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(TextView(context).apply {
            text = CommerceUi.statusLabel(group.status)
            textSize = 13.5f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(context.getColor(statusColor(group.status)))
            setBackgroundResource(R.drawable.bg_chip)
            setPadding(dp(11), dp(7), dp(11), dp(7))
        })
        card.addView(header)

        card.addView(TextView(context).apply {
            text = CommerceUi.formatDate(group.created_at)
            textSize = 14.5f
            setPadding(0, dp(8), 0, 0)
            setTextColor(context.getColor(R.color.text_muted))
        })

        card.addView(divider())

        val totalRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        totalRow.addView(TextView(context).apply {
            text = getString(R.string.order_details_6)
            textSize = 15.5f
            setTextColor(context.getColor(R.color.text_muted))
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        totalRow.addView(TextView(context).apply {
            text = MarketplaceUi.formatPrice(group.grand_total)
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(context.getColor(R.color.text_dark))
        })
        card.addView(totalRow)

        val payment = paymentLabel(group.payment_method)
        val paymentState = paymentStatusLabel(group.payment_status)
        card.addView(TextView(context).apply {
            text = "$payment • $paymentState"
            textSize = 15f
            setPadding(0, dp(9), 0, 0)
            setTextColor(context.getColor(R.color.text_muted))
        })

        if (group.delivery_total > 0 || group.discount_total > 0) {
            card.addView(TextView(context).apply {
                val parts = buildList {
                    if (group.delivery_total > 0) add(getString(R.string.orders_8, (MarketplaceUi.formatPrice(group.delivery_total))))
                    if (group.discount_total > 0) add(getString(R.string.orders_9, (MarketplaceUi.formatPrice(group.discount_total))))
                }
                text = parts.joinToString(" • ")
                textSize = 14f
                setPadding(0, dp(6), 0, 0)
                setTextColor(context.getColor(R.color.text_muted))
            })
        }

        card.addView(Button(context).apply {
            text = if (isActive(group)) getString(R.string.orders_10) else getString(R.string.orders_11)
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(context.getColor(R.color.white))
            backgroundTintList = ColorStateList.valueOf(context.getColor(R.color.tani_primary))
            setOnClickListener { openDetails(group.id) }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(54)
        ).apply { topMargin = dp(16) })

        return card
    }

    private fun divider() = View(requireContext()).apply {
        setBackgroundColor(requireContext().getColor(R.color.border))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(1)
        ).apply {
            topMargin = dp(14)
            bottomMargin = dp(14)
        }
    }

    private fun emptyState() = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(18), dp(38), dp(18), dp(38))
        background = requireContext().getDrawable(R.drawable.bg_card)
        addView(TextView(requireContext()).apply {
            text = when (filter) {
                Filter.ALL -> getString(R.string.orders_6)
                Filter.ACTIVE -> getString(R.string.orders_12)
                Filter.COMPLETED -> getString(R.string.orders_13)
                Filter.CANCELLED -> getString(R.string.orders_14)
            }
            textSize = 19f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(requireContext().getColor(R.color.text_dark))
        })
        addView(TextView(requireContext()).apply {
            text = if (filter == Filter.ALL) getString(R.string.orders_15) else getString(R.string.orders_16)
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
            setTextColor(requireContext().getColor(R.color.text_muted))
        })
    }

    private fun updateFilterStyle() {
        val buttons = mapOf(
            Filter.ALL to allButton,
            Filter.ACTIVE to activeButton,
            Filter.COMPLETED to completedButton,
            Filter.CANCELLED to cancelledButton
        )
        buttons.forEach { (type, button) ->
            val selected = type == filter
            button.alpha = if (selected) 1f else 0.78f
            button.setTypeface(button.typeface, if (selected) Typeface.BOLD else Typeface.NORMAL)
            button.textSize = 15f
        }
    }

    private fun isActive(group: OrderGroup): Boolean =
        group.status !in setOf("delivered", "cancelled", "rejected", "failed")

    private fun isCancelledLike(group: OrderGroup): Boolean =
        group.status in setOf("cancelled", "rejected", "failed")

    private fun paymentLabel(method: String): String = when (method.lowercase()) {
        "cod" -> getString(R.string.orders_17)
        "bank_transfer" -> getString(R.string.orders_18)
        "wallet" -> getString(R.string.orders_19)
        "card" -> getString(R.string.orders_20)
        else -> getString(R.string.orders_21, method)
    }

    private fun paymentStatusLabel(status: String): String = when (status.lowercase()) {
        "paid" -> getString(R.string.orders_22)
        "pending" -> getString(R.string.orders_23)
        "failed" -> getString(R.string.orders_24)
        "refunded" -> getString(R.string.orders_25)
        else -> status
    }

    private fun statusColor(status: String): Int = when (status) {
        "delivered" -> R.color.success
        "cancelled", "rejected", "failed" -> R.color.error
        "out_for_delivery", "ready", "preparing" -> R.color.warning
        else -> R.color.tani_primary
    }

    private fun openDetails(groupId: String) {
        (activity as? MainActivity)?.show(OrderDetailsFragment.newInstance(groupId))
    }

    private fun message(value: String) = TextView(requireContext()).apply {
        text = value
        textSize = 16f
        gravity = Gravity.CENTER
        setPadding(dp(8), dp(28), dp(8), dp(28))
        setTextColor(requireContext().getColor(R.color.text_muted))
    }

    private fun cardParams() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = dp(14) }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private enum class Filter { ALL, ACTIVE, COMPLETED, CANCELLED }
}
