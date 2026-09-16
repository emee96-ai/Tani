package com.tani.app.ui.orders

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
        view.findViewById<Button>(R.id.orders_refresh).setOnClickListener { load() }

        load()
    }

    private fun load() {
        progress.visibility = View.VISIBLE
        summary.text = "جاري تحميل طلباتك…"
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { repository.orderGroups() }
                .onSuccess {
                    groups = it
                    render()
                }
                .onFailure {
                    groups = emptyList()
                    box.removeAllViews()
                    summary.text = "تعذر تحميل الطلبات"
                    box.addView(message(it.message ?: "حاولي مرة أخرى"))
                }
            progress.visibility = View.GONE
        }
    }

    private fun render() {
        box.removeAllViews()

        val activeCount = groups.count(::isActive)
        val completedCount = groups.count { it.status == "delivered" }
        val cancelledCount = groups.count(::isCancelledLike)

        allButton.text = "الكل (${groups.size})"
        activeButton.text = "النشطة ($activeCount)"
        completedButton.text = "المكتملة ($completedCount)"
        cancelledButton.text = "الملغاة ($cancelledCount)"
        updateFilterStyle()

        summary.text = when {
            groups.isEmpty() -> "ما عندك طلبات لسه"
            activeCount > 0 -> "عندك $activeCount ${if (activeCount == 1) "طلب قيد المتابعة" else "طلبات قيد المتابعة"}"
            else -> "كل طلباتك الحالية مكتملة أو منتهية"
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
            setPadding(dp(16), dp(16), dp(16), dp(16))
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
            text = "طلب #${group.id.take(8).uppercase()}"
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(context.getColor(R.color.text_dark))
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(TextView(context).apply {
            text = CommerceUi.statusLabel(group.status)
            textSize = 12.5f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(context.getColor(statusColor(group.status)))
            setBackgroundResource(R.drawable.bg_chip)
            setPadding(dp(10), dp(6), dp(10), dp(6))
        })
        card.addView(header)

        card.addView(TextView(context).apply {
            text = CommerceUi.formatDate(group.created_at)
            textSize = 13f
            setPadding(0, dp(7), 0, 0)
            setTextColor(context.getColor(R.color.text_muted))
        })

        card.addView(divider())

        val totalRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        totalRow.addView(TextView(context).apply {
            text = "الإجمالي"
            textSize = 14f
            setTextColor(context.getColor(R.color.text_muted))
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        totalRow.addView(TextView(context).apply {
            text = MarketplaceUi.formatPrice(group.grand_total)
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(context.getColor(R.color.text_dark))
        })
        card.addView(totalRow)

        val payment = paymentLabel(group.payment_method)
        val paymentState = paymentStatusLabel(group.payment_status)
        card.addView(TextView(context).apply {
            text = "$payment • $paymentState"
            textSize = 13f
            setPadding(0, dp(7), 0, 0)
            setTextColor(context.getColor(R.color.text_muted))
        })

        if (group.delivery_total > 0 || group.discount_total > 0) {
            card.addView(TextView(context).apply {
                val parts = buildList {
                    if (group.delivery_total > 0) add("التوصيل ${MarketplaceUi.formatPrice(group.delivery_total)}")
                    if (group.discount_total > 0) add("الخصم ${MarketplaceUi.formatPrice(group.discount_total)}")
                }
                text = parts.joinToString(" • ")
                textSize = 12.5f
                setPadding(0, dp(4), 0, 0)
                setTextColor(context.getColor(R.color.text_muted))
            })
        }

        card.addView(Button(context).apply {
            text = if (isActive(group)) "متابعة الطلب" else "عرض التفاصيل"
            setOnClickListener { openDetails(group.id) }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(50)
        ).apply { topMargin = dp(14) })

        return card
    }

    private fun divider() = View(requireContext()).apply {
        setBackgroundColor(requireContext().getColor(R.color.border))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(1)
        ).apply {
            topMargin = dp(12)
            bottomMargin = dp(12)
        }
    }

    private fun emptyState() = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(18), dp(34), dp(18), dp(34))
        background = requireContext().getDrawable(R.drawable.bg_card)
        addView(TextView(requireContext()).apply {
            text = when (filter) {
                Filter.ALL -> "ما عندك طلبات لسه"
                Filter.ACTIVE -> "ما عندك طلبات نشطة حالياً"
                Filter.COMPLETED -> "ما عندك طلبات مكتملة لسه"
                Filter.CANCELLED -> "ما عندك طلبات ملغاة"
            }
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(requireContext().getColor(R.color.text_dark))
        })
        addView(TextView(requireContext()).apply {
            text = if (filter == Filter.ALL) "أي طلب جديد ح يظهر هنا وتقدري تتابعي حالته." else "اختاري قسم تاني لعرض بقية الطلبات."
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, 0)
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
            button.alpha = if (type == filter) 1f else 0.68f
            button.setTypeface(button.typeface, if (type == filter) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    private fun isActive(group: OrderGroup): Boolean =
        group.status !in setOf("delivered", "cancelled", "rejected", "failed")

    private fun isCancelledLike(group: OrderGroup): Boolean =
        group.status in setOf("cancelled", "rejected", "failed")

    private fun paymentLabel(method: String): String = when (method.lowercase()) {
        "cod" -> "الدفع عند الاستلام"
        "bank_transfer" -> "تحويل بنكي"
        "wallet" -> "محفظة إلكترونية"
        "card" -> "بطاقة"
        else -> "طريقة الدفع: $method"
    }

    private fun paymentStatusLabel(status: String): String = when (status.lowercase()) {
        "paid" -> "مدفوع"
        "pending" -> "بانتظار الدفع"
        "failed" -> "فشل الدفع"
        "refunded" -> "تم الاسترداد"
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
    ).apply { bottomMargin = dp(12) }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private enum class Filter { ALL, ACTIVE, COMPLETED, CANCELLED }
}
