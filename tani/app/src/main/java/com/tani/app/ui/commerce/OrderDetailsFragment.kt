package com.tani.app.ui.commerce

import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.tani.app.MainActivity
import com.tani.app.R
import com.tani.app.data.Order
import com.tani.app.data.OrderGroupDetails
import com.tani.app.data.OrderItem
import com.tani.app.data.Repository
import com.tani.app.data.repository.TrustRepository
import com.tani.app.ui.marketplace.MarketplaceUi
import com.tani.app.ui.trust.SupportCenterFragment
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

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
        view.findViewById<TextView>(R.id.order_details_title).text = "طلب #${groupId.take(8).uppercase()}"
        view.findViewById<Button>(R.id.order_details_refresh).setOnClickListener { load(groupId) }
        cancelButton.setOnClickListener { confirmCancel(groupId) }
        load(groupId)
    }

    private fun load(groupId: String) {
        progress.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
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

        summary.addView(sectionCard().apply {
            addView(LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL

                addView(TextView(requireContext()).apply {
                    text = "ملخص الطلب"
                    textSize = 20f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(requireContext().getColor(R.color.text_dark))
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

                addView(statusChip(group.status))
            })

            addView(TextView(requireContext()).apply {
                text = CommerceUi.formatDate(group.created_at)
                textSize = 14.5f
                setPadding(0, dp(7), 0, dp(13))
                setTextColor(requireContext().getColor(R.color.text_muted))
            })

            addView(divider())
            addView(valueRow("قيمة المنتجات", MarketplaceUi.formatPrice(group.subtotal)))
            addView(valueRow("التوصيل", MarketplaceUi.formatPrice(group.delivery_total)))
            if (group.discount_total > 0) {
                addView(valueRow("الخصم", MarketplaceUi.formatPrice(group.discount_total)))
            }
            addView(valueRow("الإجمالي", MarketplaceUi.formatPrice(group.grand_total), emphasis = true))
            addView(valueRow("الدفع", "عند الاستلام"))

            group.customer_note?.takeIf { it.isNotBlank() }?.let { note ->
                addView(divider())
                addView(TextView(requireContext()).apply {
                    text = "ملاحظتك"
                    textSize = 15f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(requireContext().getColor(R.color.text_dark))
                })
                addView(TextView(requireContext()).apply {
                    text = note
                    textSize = 15f
                    setPadding(0, dp(5), 0, 0)
                    setTextColor(requireContext().getColor(R.color.text_muted))
                })
            }
        })

        details.orders.forEach { order ->
            merchants.addView(
                orderCard(
                    order,
                    details.itemsByOrder[order.id].orEmpty(),
                    details.historyByOrder[order.id].orEmpty()
                ),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(16) }
            )
        }

        cancelButton.visibility =
            if (details.orders.isNotEmpty() && details.orders.all { it.status == "pending" }) View.VISIBLE else View.GONE
    }

    private fun orderCard(
        order: Order,
        items: List<OrderItem>,
        history: List<com.tani.app.data.OrderStatusHistory>
    ): LinearLayout = sectionCard().apply {
        val context = requireContext()

        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL

            addView(TextView(context).apply {
                text = order.store_name_snapshot ?: "المتجر"
                textSize = 21f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(context.getColor(R.color.text_dark))
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            addView(statusChip(order.status))
        })

        addView(TextView(context).apply {
            text = "إجمالي هذا المتجر: ${MarketplaceUi.formatPrice(order.total)}"
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(10), 0, 0)
            setTextColor(context.getColor(R.color.text_dark))
        })
        addView(TextView(context).apply {
            text = "التوصيل: ${MarketplaceUi.formatPrice(order.delivery_fee)}"
            textSize = 15f
            setPadding(0, dp(5), 0, 0)
            setTextColor(context.getColor(R.color.text_muted))
        })

        addView(divider())
        addView(sectionLabel("بيانات التوصيل"))
        addView(TextView(context).apply {
            text = order.address
            textSize = 15.5f
            setPadding(0, dp(7), 0, 0)
            setTextColor(context.getColor(R.color.text_dark))
        })
        addView(TextView(context).apply {
            text = "هاتف: ${order.phone}"
            textSize = 15f
            setPadding(0, dp(5), 0, 0)
            setTextColor(context.getColor(R.color.text_muted))
        })

        if (items.isNotEmpty()) {
            addView(divider())
            addView(sectionLabel("المنتجات"))
            items.forEach { item ->
                val name = item.product_name_snapshot ?: "منتج"
                val variant = item.variant_snapshot?.get("name")?.jsonPrimitive?.contentOrNull
                val line = item.line_total ?: item.unit_price * item.quantity
                addView(TextView(context).apply {
                    text = buildString {
                        append(name)
                        variant?.let { append(" • $it") }
                        append("\n${item.quantity} × ${MarketplaceUi.formatPrice(item.unit_price)} = ${MarketplaceUi.formatPrice(line)}")
                    }
                    textSize = 15f
                    setLineSpacing(0f, 1.15f)
                    setPadding(dp(10), dp(9), dp(10), dp(9))
                    setTextColor(context.getColor(R.color.text_dark))
                    background = context.getDrawable(R.drawable.bg_chip)
                }, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(8) })
            }
        }

        if (history.isNotEmpty()) {
            addView(divider())
            addView(sectionLabel("تتبع الحالة"))
            history.forEachIndexed { index, event ->
                addView(timelineRow(event, index == history.lastIndex))
            }
        }

        addView(divider())
        addView(Button(context).apply {
            text = "مشكلة في هذا الطلب"
            textSize = 15.5f
            setOnClickListener {
                (activity as? MainActivity)?.show(SupportCenterFragment.newComplaint(order.id, order.seller_id))
            }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(52)
        ))

        if (order.status == "delivered" && !order.seller_id.isNullOrBlank()) {
            addView(Button(context).apply {
                text = "قيّمي الطلب والمتجر"
                textSize = 15.5f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(context.getColor(R.color.white))
                backgroundTintList = ColorStateList.valueOf(context.getColor(R.color.tani_primary))
                setOnClickListener { showReviewDialog(order, items) }
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(54)
            ).apply { topMargin = dp(9) })
        }
    }

    private fun sectionCard() = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(18))
        background = requireContext().getDrawable(R.drawable.bg_card)
    }

    private fun sectionLabel(value: String) = TextView(requireContext()).apply {
        text = value
        textSize = 18f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(requireContext().getColor(R.color.text_dark))
    }

    private fun valueRow(label: String, value: String, emphasis: Boolean = false) =
        LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(0, dp(if (emphasis) 11 else 8), 0, 0)

            addView(TextView(requireContext()).apply {
                text = label
                textSize = if (emphasis) 16f else 15f
                if (emphasis) setTypeface(typeface, Typeface.BOLD)
                setTextColor(requireContext().getColor(if (emphasis) R.color.text_dark else R.color.text_muted))
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            addView(TextView(requireContext()).apply {
                text = value
                textSize = if (emphasis) 20f else 15.5f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(requireContext().getColor(if (emphasis) R.color.tani_primary else R.color.text_dark))
            })
        }

    private fun statusChip(status: String) = TextView(requireContext()).apply {
        text = CommerceUi.statusLabel(status)
        textSize = 13.5f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(requireContext().getColor(statusColor(status)))
        setBackgroundResource(R.drawable.bg_chip)
        setPadding(dp(11), dp(7), dp(11), dp(7))
    }

    private fun timelineRow(
        event: com.tani.app.data.OrderStatusHistory,
        current: Boolean
    ): View = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.TOP
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        setPadding(0, dp(10), 0, dp(2))

        addView(TextView(requireContext()).apply {
            text = if (current) "●" else "○"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(requireContext().getColor(if (current) statusColor(event.to_status) else R.color.text_muted))
        }, LinearLayout.LayoutParams(dp(28), LinearLayout.LayoutParams.WRAP_CONTENT))

        addView(LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(requireContext()).apply {
                text = CommerceUi.statusLabel(event.to_status)
                textSize = 15.5f
                setTypeface(typeface, if (current) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(requireContext().getColor(R.color.text_dark))
            })
            addView(TextView(requireContext()).apply {
                text = CommerceUi.formatDate(event.created_at)
                textSize = 13.5f
                setPadding(0, dp(2), 0, 0)
                setTextColor(requireContext().getColor(R.color.text_muted))
            })
            event.note?.takeIf { it.isNotBlank() }?.let { note ->
                addView(TextView(requireContext()).apply {
                    text = note
                    textSize = 14f
                    setPadding(0, dp(3), 0, 0)
                    setTextColor(requireContext().getColor(R.color.text_muted))
                })
            }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
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

    private fun statusColor(status: String): Int = when (status) {
        "delivered" -> R.color.success
        "cancelled", "rejected", "failed" -> R.color.error
        "out_for_delivery", "ready", "preparing" -> R.color.warning
        else -> R.color.tani_primary
    }

    private fun showReviewDialog(order: Order, items: List<OrderItem>) {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 10, 32, 0)
        }
        val rating = RatingBar(requireContext(), null, android.R.attr.ratingBarStyleSmall).apply {
            numStars = 5
            stepSize = 1f
            this.rating = 5f
        }
        val comment = EditText(requireContext()).apply {
            hint = "ملاحظتك (اختياري)"
            minLines = 3
            maxLines = 6
        }
        container.addView(rating)
        container.addView(comment)
        AlertDialog.Builder(requireContext())
            .setTitle("تقييم بعد التسليم")
            .setMessage("التقييم متاح فقط للطلبات التي تم تسليمها فعلياً.")
            .setView(container)
            .setPositiveButton("إرسال") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
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
                        val friendly = if (msg.contains("duplicate", true) || msg.contains("unique", true)) {
                            "تم تقييم هذا الطلب من قبل"
                        } else {
                            msg.ifBlank { "تعذر إرسال التقييم" }
                        }
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
                viewLifecycleOwner.lifecycleScope.launch {
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
        gravity = Gravity.CENTER
        setPadding(dp(8), dp(24), dp(8), dp(24))
        setTextColor(requireContext().getColor(R.color.text_muted))
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val ARG_GROUP_ID = "group_id"
        fun newInstance(groupId: String) = OrderDetailsFragment().apply {
            arguments = Bundle().apply { putString(ARG_GROUP_ID, groupId) }
        }
    }
}
