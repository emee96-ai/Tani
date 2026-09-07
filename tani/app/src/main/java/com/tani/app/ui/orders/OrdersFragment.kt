package com.tani.app.ui.orders

import android.os.Bundle
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
import com.tani.app.ui.commerce.CommerceUi
import com.tani.app.ui.commerce.OrderDetailsFragment
import com.tani.app.ui.marketplace.MarketplaceUi
import kotlinx.coroutines.launch

class OrdersFragment : Fragment(R.layout.fragment_orders) {
    private val repository = Repository()
    private lateinit var box: LinearLayout
    private lateinit var progress: ProgressBar
    private var groups: List<OrderGroup> = emptyList()
    private var filter: Filter = Filter.ALL

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        box = view.findViewById(R.id.orders_box)
        progress = view.findViewById(R.id.orders_progress)
        view.findViewById<Button>(R.id.orders_all).setOnClickListener { filter = Filter.ALL; render() }
        view.findViewById<Button>(R.id.orders_active).setOnClickListener { filter = Filter.ACTIVE; render() }
        view.findViewById<Button>(R.id.orders_completed).setOnClickListener { filter = Filter.COMPLETED; render() }
        view.findViewById<Button>(R.id.orders_cancelled).setOnClickListener { filter = Filter.CANCELLED; render() }
        load()
    }

    private fun load() {
        progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            runCatching { repository.orderGroups() }
                .onSuccess { groups = it; render() }
                .onFailure {
                    box.removeAllViews()
                    box.addView(message(it.message ?: "تعذر تحميل الطلبات"))
                }
            progress.visibility = View.GONE
        }
    }

    private fun render() {
        box.removeAllViews()
        val shown = groups.filter { group ->
            when (filter) {
                Filter.ALL -> true
                Filter.ACTIVE -> group.status !in setOf("delivered", "cancelled")
                Filter.COMPLETED -> group.status == "delivered"
                Filter.CANCELLED -> group.status == "cancelled"
            }
        }
        if (shown.isEmpty()) {
            box.addView(message("لا توجد طلبات في هذا القسم."))
            return
        }
        shown.forEach { group ->
            val card = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 16, 16, 16)
                background = requireContext().getDrawable(R.drawable.bg_card)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    (activity as? MainActivity)?.show(OrderDetailsFragment.newInstance(group.id))
                }
            }
            card.addView(TextView(requireContext()).apply {
                text = "طلب ${group.id.take(8).uppercase()}"
                textSize = 18f
                setTextColor(requireContext().getColor(R.color.text_dark))
            })
            card.addView(TextView(requireContext()).apply {
                text = "${CommerceUi.statusLabel(group.status)}\n${MarketplaceUi.formatPrice(group.grand_total)} • الدفع عند الاستلام\n${CommerceUi.formatDate(group.created_at)}"
                textSize = 15f
                setPadding(0, 7, 0, 8)
                setTextColor(requireContext().getColor(R.color.text_muted))
            })
            card.addView(Button(requireContext()).apply {
                text = "التفاصيل والمتابعة"
                setOnClickListener {
                    (activity as? MainActivity)?.show(OrderDetailsFragment.newInstance(group.id))
                }
            })
            box.addView(card, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 12 })
        }
    }

    private fun message(value: String) = TextView(requireContext()).apply {
        text = value
        textSize = 16f
        setPadding(6, 24, 6, 24)
        setTextColor(requireContext().getColor(R.color.text_muted))
    }

    private enum class Filter { ALL, ACTIVE, COMPLETED, CANCELLED }
}
