package com.tani.app.ui.commerce

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.EditText
import android.widget.RatingBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.tani.app.R
import com.tani.app.MainActivity
import com.tani.app.ui.trust.SupportCenterFragment
import com.tani.app.data.Order
import com.tani.app.data.OrderGroupDetails
import com.tani.app.data.OrderItem
import com.tani.app.data.Repository
import com.tani.app.data.repository.TrustRepository
import com.tani.app.ui.marketplace.MarketplaceUi
import kotlinx.coroutines.launch

class OrderDetailsFragment : Fragment(R.layout.fragment_order_details) {
    private val repository = Repository()
    private val trustRepository = TrustRepository()
    private lateinit var progress: ProgressBar
    private lateinit var summary: LinearLayout
    private lateinit var merchants: LinearLayout
    private lateinit var cancelButton: Button
    private var details: OrderGroupDetails? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val groupId = requireArguments().getString(ARG_GROUP_ID) ?: return
        progress = view.findViewById(R.id.order_details_progress)
        summary = view.findViewById(R.id.order_details_summary)
        merchants = view.findViewById(R.id.order_details_merchants)
        cancelButton = view.findViewById(R.id.order_details_cancel)
        view.findViewById<TextView>(R.id.order_details_title).text = "طلب ${groupId.take(8).uppercase()}"
        view.findViewById<Button>(R.id.order_details_refresh).setOnClickListener { load(groupId) }
        cancelButton.setOnClickListener { confirmCancel(groupId) }
        load(groupId)
    }

    private fun load(groupId: String) {
        progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            runCatching { repository.orderGroupDetails(groupId) }
                .onSuccess {
                    details = it
                    render(it)
                }
                .onFailure {
                    summary.removeAllViews()
                    summary.addView(message(it.message ?: "تعذر تحميل تفاصيل الطلب"))
                }
            progress.visibility = View.GONE
        }
    }

    private fun render(details: OrderGroupDetails) {
        summary.removeAllViews()
        merchants.removeAllViews()
        val group = details.group
        summary.addView(TextView(requireContext()).apply {
            text = buildString {
                append("الحالة: ${CommerceUi.statusLabel(group.status)}\n")
                append("قيمة المنتجات: ${MarketplaceUi.formatPrice(group.subtotal)}\n")
                append("التوصيل: ${MarketplaceUi.formatPrice(group.delivery_total)}\n")
                append("الإجمالي: ${MarketplaceUi.formatPrice(group.grand_total)}\n")
                append("الدفع: عند الاستلام\n")
                append("التاريخ: ${CommerceUi.formatDate(group.created_at)}")
                group.customer_note?.takeIf { it.isNotBlank() }?.let { append("\nملاحظتك: $it") }
            }
            textSize = 16f
            setPadding(16, 16, 16, 16)
            background = requireContext().getDrawable(R.drawable.bg_card)
        })

        details.orders.forEach { order ->
            merchants.addView(orderCard(order, details.itemsByOrder[order.id].orEmpty(), details.historyByOrder[order.id].orEmpty()),
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 14 })
        }

        cancelButton.visibility = if (details.orders.isNotEmpty() && details.orders.all { it.status == "pending" }) View.VISIBLE else View.GONE
    }

    private fun orderCard(
        order: Order,
        items: List<OrderItem>,
        history: List<com.tani.app.data.OrderStatusHistory>
    ): LinearLayout = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(16, 16, 16, 16)
        background = requireContext().getDrawable(R.drawable.bg_card)

        addView(TextView(requireContext()).apply {
            text = order.store_name_snapshot ?: "المتجر"
            textSize = 19f
            setTextColor(requireContext().getColor(R.color.text_dark))
        })
        addView(TextView(requireContext()).apply {
            text = "${CommerceUi.statusLabel(order.status)} • ${MarketplaceUi.formatPrice(order.total)}\nالتوصيل: ${MarketplaceUi.formatPrice(order.delivery_fee)}\n${order.address}\nهاتف: ${order.phone}"
            textSize = 14f
            setPadding(0, 6, 0, 8)
            setTextColor(requireContext().getColor(R.color.text_muted))
        })
        items.forEach { item ->
            addView(TextView(requireContext()).apply {
                val name = item.product_name_snapshot ?: "منتج"
                val line = item.line_total ?: item.unit_price * item.quantity
                text = "• $name — ${item.quantity} × ${MarketplaceUi.formatPrice(item.unit_price)} = ${MarketplaceUi.formatPrice(line)}"
                textSize = 14f
                setPadding(0, 3, 0, 3)
            })
        }
        addView(Button(requireContext()).apply {
            text = "مشكلة في هذا الطلب"
            setOnClickListener {
                (activity as? MainActivity)?.show(SupportCenterFragment.newComplaint(order.id, order.seller_id))
            }
        })

        if (order.status == "delivered" && !order.seller_id.isNullOrBlank()) {
            addView(Button(requireContext()).apply {
                text = "قيّمي الطلب والمتجر"
                setOnClickListener { showReviewDialog(order, items) }
            })
        }

        if (history.isNotEmpty()) {
            addView(TextView(requireContext()).apply {
                text = "تتبع الحالة"
                textSize = 16f
                setPadding(0, 12, 0, 4)
                setTextColor(requireContext().getColor(R.color.text_dark))
            })
            history.forEach { event ->
                addView(TextView(requireContext()).apply {
                    text = "• ${CommerceUi.statusLabel(event.to_status)} — ${CommerceUi.formatDate(event.created_at)}${event.note?.takeIf { it.isNotBlank() }?.let { "\n  $it" } ?: ""}"
                    textSize = 13f
                    setPadding(0, 3, 0, 3)
                    setTextColor(requireContext().getColor(R.color.text_muted))
                })
            }
        }
    }

    private fun showReviewDialog(order: Order, items: List<OrderItem>) {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 10, 32, 0)
        }
        val rating = RatingBar(requireContext(), null, android.R.attr.ratingBarStyleSmall).apply {
            numStars = 5; stepSize = 1f; this.rating = 5f
        }
        val comment = EditText(requireContext()).apply { hint = "ملاحظتك (اختياري)"; minLines = 3; maxLines = 6 }
        container.addView(rating); container.addView(comment)
        AlertDialog.Builder(requireContext())
            .setTitle("تقييم بعد التسليم")
            .setMessage("التقييم متاح فقط للطلبات التي تم تسليمها فعلياً.")
            .setView(container)
            .setPositiveButton("إرسال") { _, _ ->
                lifecycleScope.launch {
                    runCatching {
                        trustRepository.submitDeliveredOrderReviews(
                            orderId = order.id,
                            sellerId = order.seller_id ?: error("التاجر غير متاح"),
                            productIds = items.map { it.product_id },
                            rating = rating.rating.toInt().coerceIn(1, 5),
                            comment = comment.text.toString()
                        )
                    }.onSuccess {
                        Toast.makeText(requireContext(), "شكراً لتقييمك", Toast.LENGTH_SHORT).show()
                    }.onFailure {
                        val msg = it.message.orEmpty()
                        val friendly = if (msg.contains("duplicate", true) || msg.contains("unique", true)) "تم تقييم هذا الطلب من قبل" else (msg.ifBlank { "تعذر إرسال التقييم" })
                        Toast.makeText(requireContext(), friendly, Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun confirmCancel(groupId: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("إلغاء الطلب")
            .setMessage("يمكن الإلغاء الآن فقط لأن جميع طلبات التجار ما زالت بانتظار القبول. هل تريدين المتابعة؟")
            .setPositiveButton("إلغاء الطلب") { _, _ ->
                lifecycleScope.launch {
                    progress.visibility = View.VISIBLE
                    runCatching { repository.cancelOrderGroup(groupId) }
                        .onSuccess {
                            Toast.makeText(requireContext(), "تم إلغاء الطلب", Toast.LENGTH_SHORT).show()
                            load(groupId)
                        }
                        .onFailure {
                            Toast.makeText(requireContext(), it.message ?: "تعذر إلغاء الطلب", Toast.LENGTH_LONG).show()
                        }
                    progress.visibility = View.GONE
                }
            }
            .setNegativeButton("رجوع", null)
            .show()
    }

    private fun message(value: String) = TextView(requireContext()).apply {
        text = value
        textSize = 16f
        setPadding(8, 20, 8, 20)
    }

    companion object {
        private const val ARG_GROUP_ID = "group_id"
        fun newInstance(groupId: String) = OrderDetailsFragment().apply {
            arguments = Bundle().apply { putString(ARG_GROUP_ID, groupId) }
        }
    }
}
