package com.tani.app.ui.seller

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tani.app.R
import com.tani.app.data.Order
import com.tani.app.data.Repository
import kotlinx.coroutines.launch

class MerchantOrdersFragment : Fragment() {
    private val repository = Repository()
    private lateinit var root: LinearLayout

    override fun onCreateView(inflater: android.view.LayoutInflater, container: android.view.ViewGroup?, state: Bundle?): View {
        val context = requireContext()
        root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(MerchantUi.dp(context, 16), MerchantUi.dp(context, 18), MerchantUi.dp(context, 16), MerchantUi.dp(context, 30))
            MerchantUi.decorateRoot(this)
        }
        return ScrollView(context).apply {
            isFillViewport = true
            setBackgroundColor(context.getColor(R.color.tani_background))
            addView(root)
        }
    }

    override fun onViewCreated(view: View, state: Bundle?) { load() }

    private fun load() {
        val context = requireContext(); root.removeAllViews()
        root.addView(MerchantUi.title(context, "طلبات المتجر"))
        root.addView(MerchantUi.card(context, soft = true).apply { addView(MerchantUi.text(context, "جاري تحميل الطلبات…", 14f, true)) })
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                val seller = repository.seller() ?: error("حساب التاجر غير مكتمل")
                repository.merchantOrders(seller.id)
            }.onSuccess(::render)
                .onFailure { showError(it.message ?: "تعذر تحميل الطلبات") }
        }
    }

    private fun render(orders: List<Order>) {
        val context = requireContext(); root.removeAllViews()
        root.addView(MerchantUi.title(context, "طلبات المتجر"))
        root.addView(MerchantUi.subtitle(context, "الطلبات الجديدة أولاً. حدّثي الحالة عشان العميل يعرف طلبه وصل وين."))

        val newCount = orders.count { it.status == "pending" }
        val progressCount = orders.count { it.status in setOf("accepted", "preparing", "ready", "out_for_delivery") }
        val delivered = orders.count { it.status == "delivered" }
        root.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL
            addView(MerchantUi.metricCard(context, "جديدة", newCount.toString()), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = MerchantUi.dp(context, 3) })
            addView(MerchantUi.metricCard(context, "قيد التنفيذ", progressCount.toString()), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = MerchantUi.dp(context, 3); marginEnd = MerchantUi.dp(context, 3) })
            addView(MerchantUi.metricCard(context, "تم التسليم", delivered.toString()), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = MerchantUi.dp(context, 3) })
        })

        root.addView(MerchantUi.sectionTitle(context, "كل الطلبات"))
        if (orders.isEmpty()) {
            root.addView(MerchantUi.emptyCard(context, "ما في طلبات لسه", "أول طلب حيظهر هنا ومعاه كل التفاصيل المطلوبة للتنفيذ."))
            return
        }

        orders.forEach { order -> root.addView(orderCard(order)) }
    }

    private fun orderCard(order: Order): LinearLayout {
        val context = requireContext()
        return MerchantUi.card(context).apply {
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(MerchantUi.text(context, "طلب #${order.id.take(8)}", 16f, true))
                    order.created_at?.let { addView(MerchantUi.muted(context, it.take(16).replace('T', ' '), 11.5f).apply { setPadding(0, MerchantUi.dp(context, 3), 0, 0) }) }
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(MerchantUi.pill(context, statusText(order.status), statusTone(order.status)))
            })
            addView(MerchantUi.text(context, "${money(order.total)} جنيه", 18f, true).apply { setPadding(0, MerchantUi.dp(context, 9), 0, MerchantUi.dp(context, 6)) })
            addView(MerchantUi.muted(context, "العميل: ${order.customer_name_snapshot ?: "—"}", 12.5f))
            addView(MerchantUi.muted(context, "الهاتف: ${order.phone}", 12.5f).apply { setPadding(0, MerchantUi.dp(context, 3), 0, 0) })
            addView(MerchantUi.muted(context, "العنوان: ${order.address}", 12.5f).apply { setPadding(0, MerchantUi.dp(context, 3), 0, 0) })
            order.customer_note?.takeIf { it.isNotBlank() }?.let {
                addView(MerchantUi.card(context, soft = true).apply {
                    addView(MerchantUi.muted(context, "ملاحظة العميل", 11.5f))
                    addView(MerchantUi.text(context, it, 13f, true).apply { setPadding(0, MerchantUi.dp(context, 3), 0, 0) })
                }.apply { setPadding(MerchantUi.dp(context, 12), MerchantUi.dp(context, 10), MerchantUi.dp(context, 12), MerchantUi.dp(context, 10)) })
            }
            addView(MerchantUi.secondaryButton(context, "عرض المنتجات") { showItems(order) })
            val actions = nextActions(order.status)
            if (actions.isNotEmpty()) {
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL
                    setPadding(0, MerchantUi.dp(context, 7), 0, 0)
                    actions.forEach { action ->
                        val button = if (action.destructive) MerchantUi.dangerButton(context, action.label) { confirmTransition(order, action.status) }
                        else MerchantUi.primaryButton(context, action.label) { confirmTransition(order, action.status) }
                        addView(button, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                            marginStart = MerchantUi.dp(context, 3); marginEnd = MerchantUi.dp(context, 3)
                        })
                    }
                })
            }
        }
    }

    private fun showItems(order: Order) {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { repository.merchantOrderItems(order.id) }.onSuccess { items ->
                val context = requireContext()
                val box = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(MerchantUi.dp(context, 18), 0, MerchantUi.dp(context, 18), 0)
                }
                if (items.isEmpty()) box.addView(MerchantUi.muted(context, "لا توجد منتجات في الطلب"))
                items.forEach { item ->
                    box.addView(MerchantUi.card(context).apply {
                        addView(MerchantUi.text(context, item.product_name_snapshot ?: "منتج", 14.5f, true))
                        addView(MerchantUi.muted(context, "الكمية: ${item.quantity} • ${money(item.line_total ?: item.unit_price * item.quantity)} جنيه", 12f).apply {
                            setPadding(0, MerchantUi.dp(context, 4), 0, 0)
                        })
                    })
                }
                MaterialAlertDialogBuilder(context).setTitle("منتجات الطلب").setView(box).setPositiveButton("إغلاق", null).show()
            }.onFailure { toast(it.message ?: "تعذر تحميل المنتجات") }
        }
    }

    private fun confirmTransition(order: Order, status: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("تحديث حالة الطلب")
            .setMessage("تغيير الطلب إلى «${statusText(status)}»؟")
            .setPositiveButton("تأكيد") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    runCatching { repository.transitionMerchantOrder(order.id, status) }
                        .onSuccess { toast("تم تحديث الطلب ✓"); load() }
                        .onFailure { toast(it.message ?: "تعذر تحديث الطلب") }
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun nextActions(status: String): List<OrderAction> = when (status) {
        "pending" -> listOf(OrderAction("قبول الطلب", "accepted"), OrderAction("رفض", "rejected", true))
        "accepted" -> listOf(OrderAction("بدء التجهيز", "preparing"), OrderAction("إلغاء", "cancelled", true))
        "preparing" -> listOf(OrderAction("الطلب جاهز", "ready"), OrderAction("إلغاء", "cancelled", true))
        "ready" -> listOf(OrderAction("خرج للتوصيل", "out_for_delivery"), OrderAction("إلغاء", "cancelled", true))
        "out_for_delivery" -> listOf(OrderAction("تم التسليم", "delivered"), OrderAction("فشل التسليم", "failed", true))
        else -> emptyList()
    }

    private fun statusText(status: String): String = when (status) {
        "pending" -> "طلب جديد"
        "accepted" -> "تم القبول"
        "preparing" -> "جاري التجهيز"
        "ready" -> "جاهز"
        "out_for_delivery" -> "خرج للتوصيل"
        "delivered" -> "تم التسليم"
        "rejected" -> "مرفوض"
        "cancelled" -> "ملغي"
        "failed" -> "فشل التسليم"
        else -> status
    }

    private fun statusTone(status: String): MerchantUi.Tone = when (status) {
        "delivered" -> MerchantUi.Tone.SUCCESS
        "pending", "accepted", "preparing", "ready", "out_for_delivery" -> MerchantUi.Tone.WARNING
        "rejected", "cancelled", "failed" -> MerchantUi.Tone.ERROR
        else -> MerchantUi.Tone.NEUTRAL
    }

    private fun showError(message: String) {
        val context = requireContext(); root.removeAllViews()
        root.addView(MerchantUi.title(context, "طلبات المتجر"))
        root.addView(MerchantUi.card(context).apply {
            addView(MerchantUi.text(context, "تعذر تحميل الطلبات", 16f, true))
            addView(MerchantUi.muted(context, message).apply { setPadding(0, MerchantUi.dp(context, 5), 0, MerchantUi.dp(context, 10)) })
            addView(MerchantUi.secondaryButton(context, "إعادة المحاولة") { load() })
        })
    }
    private fun money(value: Double) = if (value % 1.0 == 0.0) value.toInt().toString() else String.format(java.util.Locale.US, "%.2f", value)
    private fun toast(value: String) = Toast.makeText(requireContext(), value, Toast.LENGTH_LONG).show()
    private data class OrderAction(val label: String, val status: String, val destructive: Boolean = false)
}
