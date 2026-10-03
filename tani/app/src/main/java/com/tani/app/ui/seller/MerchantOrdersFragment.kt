package com.tani.app.ui.seller

import com.tani.app.util.runCatchingCancellable

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tani.app.R
import com.tani.app.data.*
import com.tani.app.ui.commerce.CommerceUi
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

class MerchantOrdersFragment : Fragment() {
    private val repository = Repository()
    private lateinit var root: LinearLayout
    private var ordersSnapshot: List<Order> = emptyList()
    private var selectedFilter = OrderFilter.ALL
    private val transitioningOrderIds = mutableSetOf<String>()

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        state: Bundle?
    ): View {
        val context = requireContext()
        root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                MerchantUi.dp(context, 16),
                MerchantUi.dp(context, 18),
                MerchantUi.dp(context, 16),
                MerchantUi.dp(context, 30)
            )
            MerchantUi.decorateRoot(this)
        }
        return ScrollView(context).apply {
            isFillViewport = true
            setBackgroundColor(context.getColor(R.color.tani_background))
            addView(root)
        }
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        load()
    }

    private fun load() {
        val context = requireContext()
        root.removeAllViews()
        root.addView(MerchantUi.title(context, "طلبات المتجر"))
        root.addView(
            MerchantUi.card(context, soft = true).apply {
                addView(MerchantUi.text(context, "جاري تحميل الطلبات…", 14f, true))
            }
        )
        viewLifecycleOwner.lifecycleScope.launch {
            runCatchingCancellable {
                val seller = repository.seller() ?: error("حساب التاجر غير مكتمل")
                repository.merchantOrders(seller.id)
            }.onSuccess { orders ->
                ordersSnapshot = orders.sortedWith(
                    compareBy<Order> { statusRank(it.status) }
                        .thenByDescending { it.created_at.orEmpty() }
                )
                render()
            }.onFailure {
                showError(friendlyError(it.message))
            }
        }
    }

    private fun render() {
        val context = requireContext()
        root.removeAllViews()
        root.addView(MerchantUi.title(context, "طلبات المتجر"))
        root.addView(
            MerchantUi.subtitle(
                context,
                "الطلبات الجديدة أولاً. سجّلي كل خطوة عشان العميل يعرف طلبه وصل وين."
            )
        )

        val newCount = ordersSnapshot.count { it.status == "pending" }
        val progressCount = ordersSnapshot.count {
            it.status in setOf("accepted", "preparing", "ready", "out_for_delivery")
        }
        val delivered = ordersSnapshot.count { it.status == "delivered" }

        root.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            addView(
                MerchantUi.metricCard(context, "جديدة", newCount.toString()),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = MerchantUi.dp(context, 3)
                }
            )
            addView(
                MerchantUi.metricCard(context, "قيد التنفيذ", progressCount.toString()),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = MerchantUi.dp(context, 3)
                    marginEnd = MerchantUi.dp(context, 3)
                }
            )
            addView(
                MerchantUi.metricCard(context, "تم التسليم", delivered.toString()),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = MerchantUi.dp(context, 3)
                }
            )
        })

        root.addView(MerchantUi.sectionTitle(context, "تصفية الطلبات"))
        root.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            OrderFilter.entries.forEach { filter ->
                val button = MerchantUi.compactButton(context, filter.label) {
                    if (selectedFilter != filter) {
                        selectedFilter = filter
                        render()
                    }
                }
                button.isEnabled = selectedFilter != filter
                addView(
                    button,
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = MerchantUi.dp(context, 2)
                        marginEnd = MerchantUi.dp(context, 2)
                    }
                )
            }
        })

        val visibleOrders = ordersSnapshot.filter(selectedFilter::matches)
        root.addView(
            MerchantUi.sectionTitle(
                context,
                if (selectedFilter == OrderFilter.ALL) "كل الطلبات" else selectedFilter.label
            )
        )

        if (visibleOrders.isEmpty()) {
            root.addView(
                MerchantUi.emptyCard(
                    context,
                    if (ordersSnapshot.isEmpty()) "ما في طلبات لسه" else "ما في طلبات في القسم دا",
                    if (ordersSnapshot.isEmpty()) {
                        "أول طلب حيظهر هنا ومعاه كل التفاصيل المطلوبة للتنفيذ."
                    } else {
                        "اختاري قسم تاني أو ارجعي لكل الطلبات."
                    }
                )
            )
            return
        }

        visibleOrders.forEach { order -> root.addView(orderCard(order)) }
    }

    private fun orderCard(order: Order): LinearLayout {
        val context = requireContext()
        return MerchantUi.card(context).apply {
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(MerchantUi.text(context, "طلب #${order.id.take(8).uppercase()}", 16f, true))
                    order.created_at?.let {
                        addView(
                            MerchantUi.muted(context, CommerceUi.formatDate(it), 11.5f).apply {
                                setPadding(0, MerchantUi.dp(context, 3), 0, 0)
                            }
                        )
                    }
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(MerchantUi.pill(context, statusText(order.status), statusTone(order.status)))
            })

            addView(
                MerchantUi.text(context, "${money(order.total)} جنيه", 18f, true).apply {
                    setPadding(0, MerchantUi.dp(context, 9), 0, MerchantUi.dp(context, 4))
                }
            )
            if (order.subtotal > 0 || order.delivery_fee > 0 || order.discount > 0) {
                addView(
                    MerchantUi.muted(
                        context,
                        buildString {
                            append("المنتجات ${money(order.subtotal)}")
                            append(" • التوصيل ${money(order.delivery_fee)}")
                            if (order.discount > 0) append(" • الخصم ${money(order.discount)}")
                        },
                        12f
                    )
                )
            }

            addView(MerchantUi.sectionTitle(context, "بيانات العميل"))
            addView(MerchantUi.text(context, order.customer_name_snapshot ?: "العميل", 14.5f, true))
            addView(
                MerchantUi.muted(context, "الهاتف: ${order.phone}", 12.5f).apply {
                    setPadding(0, MerchantUi.dp(context, 3), 0, 0)
                }
            )
            addView(
                MerchantUi.muted(context, "العنوان: ${order.address}", 12.5f).apply {
                    setPadding(0, MerchantUi.dp(context, 3), 0, 0)
                }
            )

            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                setPadding(0, MerchantUi.dp(context, 8), 0, 0)
                addView(
                    MerchantUi.compactButton(context, "اتصال") { callCustomer(order.phone) },
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginEnd = MerchantUi.dp(context, 3)
                    }
                )
                addView(
                    MerchantUi.compactButton(context, "نسخ العنوان") { copyAddress(order.address) },
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = MerchantUi.dp(context, 3)
                    }
                )
            })

            order.customer_note?.takeIf { it.isNotBlank() }?.let { note ->
                addView(
                    MerchantUi.card(context, soft = true).apply {
                        addView(MerchantUi.muted(context, "ملاحظة العميل", 11.5f))
                        addView(
                            MerchantUi.text(context, note, 13f, true).apply {
                                setPadding(0, MerchantUi.dp(context, 3), 0, 0)
                            }
                        )
                    }.apply {
                        setPadding(
                            MerchantUi.dp(context, 12),
                            MerchantUi.dp(context, 10),
                            MerchantUi.dp(context, 12),
                            MerchantUi.dp(context, 10)
                        )
                    }
                )
            }

            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                setPadding(0, MerchantUi.dp(context, 8), 0, 0)
                addView(
                    MerchantUi.secondaryButton(context, "عرض المنتجات") { showItems(order) },
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginEnd = MerchantUi.dp(context, 3)
                    }
                )
                addView(
                    MerchantUi.secondaryButton(context, "سجل الحالة") { showHistory(order) },
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = MerchantUi.dp(context, 3)
                    }
                )
            })

            val actions = nextActions(order.status)
            if (transitioningOrderIds.contains(order.id)) {
                addView(
                    MerchantUi.card(context, soft = true).apply {
                        addView(MerchantUi.text(context, "جاري تحديث حالة الطلب…", 13.5f, true))
                    }
                )
            } else if (actions.isNotEmpty()) {
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutDirection = View.LAYOUT_DIRECTION_RTL
                    setPadding(0, MerchantUi.dp(context, 8), 0, 0)
                    actions.forEach { action ->
                        val button = if (action.destructive) {
                            MerchantUi.dangerButton(context, action.label) {
                                confirmTransition(order, action.status)
                            }
                        } else {
                            MerchantUi.primaryButton(context, action.label) {
                                confirmTransition(order, action.status)
                            }
                        }
                        addView(
                            button,
                            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                                marginStart = MerchantUi.dp(context, 3)
                                marginEnd = MerchantUi.dp(context, 3)
                            }
                        )
                    }
                })
            }
        }
    }

    private fun showItems(order: Order) {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatchingCancellable { repository.merchantOrderItems(order.id) }
                .onSuccess { items ->
                    if (!isAdded) return@onSuccess
                    val context = requireContext()
                    val box = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(MerchantUi.dp(context, 18), 0, MerchantUi.dp(context, 18), 0)
                    }
                    if (items.isEmpty()) {
                        box.addView(MerchantUi.muted(context, "لا توجد منتجات في الطلب"))
                    }
                    items.forEach { item ->
                        box.addView(MerchantUi.card(context).apply {
                            val variant = item.variant_snapshot?.get("name")?.jsonPrimitive?.contentOrNull
                            addView(
                                MerchantUi.text(
                                    context,
                                    buildString {
                                        append(item.product_name_snapshot ?: "منتج")
                                        variant?.let { append(" • الخيار: $it") }
                                    },
                                    14.5f,
                                    true
                                )
                            )
                            addView(
                                MerchantUi.muted(
                                    context,
                                    "الكمية: ${item.quantity} • سعر الوحدة: ${money(item.unit_price)} • الإجمالي: ${money(item.line_total ?: item.unit_price * item.quantity)} جنيه",
                                    12f
                                ).apply { setPadding(0, MerchantUi.dp(context, 4), 0, 0) }
                            )
                        })
                    }
                    MaterialAlertDialogBuilder(context)
                        .setTitle("منتجات الطلب")
                        .setView(box)
                        .setPositiveButton("إغلاق", null)
                        .show()
                }
                .onFailure { toast(friendlyError(it.message)) }
        }
    }

    private fun showHistory(order: Order) {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatchingCancellable { repository.merchantOrderHistory(order.id) }
                .onSuccess { history ->
                    if (!isAdded) return@onSuccess
                    val context = requireContext()
                    val box = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(MerchantUi.dp(context, 18), 0, MerchantUi.dp(context, 18), 0)
                    }
                    if (history.isEmpty()) {
                        box.addView(MerchantUi.muted(context, "لا يوجد سجل حالة لهذا الطلب."))
                    }
                    history.forEach { event ->
                        box.addView(MerchantUi.card(context, soft = true).apply {
                            addView(MerchantUi.text(context, statusText(event.to_status), 14f, true))
                            addView(
                                MerchantUi.muted(context, CommerceUi.formatDate(event.created_at), 11.5f).apply {
                                    setPadding(0, MerchantUi.dp(context, 3), 0, 0)
                                }
                            )
                            event.note?.takeIf { it.isNotBlank() }?.let { note ->
                                addView(
                                    MerchantUi.text(context, note, 12.5f).apply {
                                        setPadding(0, MerchantUi.dp(context, 5), 0, 0)
                                    }
                                )
                            }
                        })
                    }
                    MaterialAlertDialogBuilder(context)
                        .setTitle("سجل حالة الطلب")
                        .setView(box)
                        .setPositiveButton("إغلاق", null)
                        .show()
                }
                .onFailure { toast(friendlyError(it.message)) }
        }
    }

    private fun confirmTransition(order: Order, status: String) {
        if (transitioningOrderIds.contains(order.id)) {
            toast("جاري تحديث الطلب بالفعل")
            return
        }

        val context = requireContext()
        val requiresReason = status in setOf("rejected", "cancelled", "failed")
        val requiresEta = status == "out_for_delivery"
        val content = if (requiresReason || requiresEta) {
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(MerchantUi.dp(context, 20), 0, MerchantUi.dp(context, 20), 0)
            }
        } else null

        val etaField = if (requiresEta) {
            EditText(context).apply {
                hint = "زمن الوصول المتوقع بالدقائق — مثلاً 45"
                inputType = InputType.TYPE_CLASS_NUMBER
                setBackgroundResource(R.drawable.bg_field)
                setPadding(
                    MerchantUi.dp(context, 12), MerchantUi.dp(context, 10),
                    MerchantUi.dp(context, 12), MerchantUi.dp(context, 10)
                )
                content?.addView(
                    this,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = MerchantUi.dp(context, 10) }
                )
            }
        } else null

        val noteField = if (requiresReason || requiresEta) {
            EditText(context).apply {
                hint = when (status) {
                    "rejected" -> "سبب رفض الطلب *"
                    "cancelled" -> "سبب إلغاء الطلب *"
                    "failed" -> "سبب فشل التسليم *"
                    else -> "ملاحظة للمندوب/العميل (اختياري)"
                }
                minLines = if (requiresReason) 2 else 1
                maxLines = 4
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                setBackgroundResource(R.drawable.bg_field)
                setPadding(
                    MerchantUi.dp(context, 12), MerchantUi.dp(context, 10),
                    MerchantUi.dp(context, 12), MerchantUi.dp(context, 10)
                )
                content?.addView(this)
            }
        } else null

        val builder = MaterialAlertDialogBuilder(context)
            .setTitle("تحديث حالة الطلب")
            .setPositiveButton("تأكيد", null)
            .setNegativeButton("إلغاء", null)

        if (content != null) {
            builder.setMessage(
                if (requiresEta) {
                    "حددي الزمن المتوقع بعد تسليم الطلب للمندوب. سيظهر للعميل في سجل الطلب."
                } else {
                    "السبب سيظهر في سجل حالة الطلب، فاكتبيه بصورة واضحة."
                }
            ).setView(content)
        } else {
            builder.setMessage("تغيير الطلب إلى «${statusText(status)}»؟")
        }

        val dialog = builder.create()
        dialog.setOnShowListener {
            val positive = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
            val negative = dialog.getButton(DialogInterface.BUTTON_NEGATIVE)
            positive.setOnClickListener {
                val note = noteField?.text?.toString()?.trim().orEmpty()
                val eta = etaField?.text?.toString()?.trim()?.toIntOrNull()

                if (requiresReason && note.length < 3) {
                    toast("اكتبي سبباً واضحاً قبل المتابعة")
                    return@setOnClickListener
                }
                if (note.length > 350) {
                    toast("الملاحظة طويلة؛ اختصريها إلى 350 حرفاً")
                    return@setOnClickListener
                }
                if (requiresEta && (eta == null || eta !in 1..1440)) {
                    toast("حددي زمن وصول صحيح من دقيقة إلى 24 ساعة")
                    return@setOnClickListener
                }

                transitioningOrderIds += order.id
                positive.isEnabled = false
                negative.isEnabled = false
                viewLifecycleOwner.lifecycleScope.launch {
                    runCatchingCancellable {
                        repository.transitionMerchantOrderOperational(
                            orderId = order.id,
                            status = status,
                            note = note,
                            estimatedMinutes = eta
                        )
                    }.onSuccess {
                        transitioningOrderIds -= order.id
                        dialog.dismiss()
                        toast("تم تحديث الطلب إلى ${statusText(status)} ✓")
                        load()
                    }.onFailure {
                        transitioningOrderIds -= order.id
                        positive.isEnabled = true
                        negative.isEnabled = true
                        toast(friendlyError(it.message))
                    }
                }
            }
        }
        dialog.show()
    }

    private fun nextActions(status: String): List<OrderAction> = when (status) {
        "pending" -> listOf(
            OrderAction("قبول الطلب", "accepted"),
            OrderAction("رفض", "rejected", true)
        )
        "accepted" -> listOf(
            OrderAction("بدء التجهيز", "preparing"),
            OrderAction("إلغاء", "cancelled", true)
        )
        "preparing" -> listOf(
            OrderAction("الطلب جاهز", "ready"),
            OrderAction("إلغاء", "cancelled", true)
        )
        "ready" -> listOf(
            OrderAction("تسليم للمندوب", "out_for_delivery"),
            OrderAction("إلغاء", "cancelled", true)
        )
        "out_for_delivery" -> listOf(
            OrderAction("تم التسليم", "delivered"),
            OrderAction("فشل التسليم", "failed", true)
        )
        else -> emptyList()
    }

    private fun callCustomer(phone: String) {
        val clean = phone.trim()
        if (clean.isBlank()) {
            toast("رقم العميل غير متاح")
            return
        }
        runCatchingCancellable {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(clean)}")))
        }.onFailure { toast("تعذر فتح الاتصال") }
    }

    private fun copyAddress(address: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("عنوان الطلب", address))
        toast("تم نسخ العنوان")
    }

    private fun statusRank(status: String): Int = when (status) {
        "pending" -> 0
        "accepted", "preparing", "ready", "out_for_delivery" -> 1
        else -> 2
    }

    private fun statusText(status: String): String = when (status) {
        "pending" -> "طلب جديد"
        "accepted" -> "تم القبول"
        "preparing" -> "جاري التجهيز"
        "ready" -> "جاهز"
        "out_for_delivery" -> "مع المندوب"
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

    private fun friendlyError(raw: String?): String {
        val value = raw.orEmpty()
        val lower = value.lowercase()
        return when {
            lower.contains("final state") -> "الطلب اتحدث بالفعل إلى حالة نهائية. حدّثي القائمة."
            lower.contains("transition is not allowed") -> "الحالة الحالية للطلب اتغيرت. حدّثي القائمة وجربي من جديد."
            lower.contains("reason is required") -> "اكتبي سبباً واضحاً قبل المتابعة."
            lower.contains("delivery eta") -> "حددي زمن الوصول المتوقع بعد تسليم الطلب للمندوب."
            lower.contains("only this order seller") || lower.contains("permission") || lower.contains("42501") ->
                "ما عندك صلاحية لتحديث الطلب دا."
            lower.contains("order not found") -> "الطلب ما عاد موجوداً أو ما عندك صلاحية لعرضه."
            lower.contains("timeout") || lower.contains("timed out") || lower.contains("failed to connect") ||
                lower.contains("unable to resolve") || lower.contains("network") || lower.contains("host") ->
                "الشبكة ضعيفة أو مقطوعة. تأكدي من الاتصال وحاولي تاني."
            value.isBlank() -> "تعذر تنفيذ العملية. حاولي مرة أخرى."
            else -> value
        }
    }

    private fun showError(message: String) {
        val context = requireContext()
        root.removeAllViews()
        root.addView(MerchantUi.title(context, "طلبات المتجر"))
        root.addView(MerchantUi.card(context).apply {
            addView(MerchantUi.text(context, "تعذر تحميل الطلبات", 16f, true))
            addView(
                MerchantUi.muted(context, message).apply {
                    setPadding(0, MerchantUi.dp(context, 5), 0, MerchantUi.dp(context, 10))
                }
            )
            addView(MerchantUi.secondaryButton(context, "إعادة المحاولة") { load() })
        })
    }

    private fun money(value: Double) =
        if (value % 1.0 == 0.0) value.toInt().toString()
        else String.format(java.util.Locale.US, "%.2f", value)

    private fun toast(value: String) =
        Toast.makeText(requireContext(), value, Toast.LENGTH_LONG).show()

    private data class OrderAction(
        val label: String,
        val status: String,
        val destructive: Boolean = false
    )

    private enum class OrderFilter(val label: String) {
        ALL("الكل"),
        NEW("الجديدة"),
        ACTIVE("قيد التنفيذ"),
        DONE("المنتهية");

        fun matches(order: Order): Boolean = when (this) {
            ALL -> true
            NEW -> order.status == "pending"
            ACTIVE -> order.status in setOf("accepted", "preparing", "ready", "out_for_delivery")
            DONE -> order.status in setOf("delivered", "rejected", "cancelled", "failed")
        }
    }
}
