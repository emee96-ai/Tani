package com.tani.app.ui.seller

import androidx.core.net.toUri
import com.tani.app.data.network.CustomerErrorMessages

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
        root.addView(MerchantUi.title(context, getString(R.string.merchant_orders_1)))
        root.addView(
            MerchantUi.card(context, soft = true).apply {
                addView(MerchantUi.text(context, getString(R.string.merchant_orders_2), 14f, true))
            }
        )
        viewLifecycleOwner.lifecycleScope.launch {
            runCatchingCancellable {
                val seller = repository.seller() ?: error(getString(R.string.merchant_delivery_2))
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
        root.addView(MerchantUi.title(context, getString(R.string.merchant_orders_1)))
        root.addView(
            MerchantUi.subtitle(
                context,
                getString(R.string.merchant_orders_3)
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
                MerchantUi.metricCard(context, getString(R.string.merchant_orders_4), newCount.toString()),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = MerchantUi.dp(context, 3)
                }
            )
            addView(
                MerchantUi.metricCard(context, getString(R.string.merchant_orders_5), progressCount.toString()),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = MerchantUi.dp(context, 3)
                    marginEnd = MerchantUi.dp(context, 3)
                }
            )
            addView(
                MerchantUi.metricCard(context, getString(R.string.merchant_analytics_5), delivered.toString()),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = MerchantUi.dp(context, 3)
                }
            )
        })

        root.addView(MerchantUi.sectionTitle(context, getString(R.string.merchant_orders_6)))
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
                if (selectedFilter == OrderFilter.ALL) getString(R.string.merchant_insights_6) else selectedFilter.label
            )
        )

        if (visibleOrders.isEmpty()) {
            root.addView(
                MerchantUi.emptyCard(
                    context,
                    if (ordersSnapshot.isEmpty()) getString(R.string.merchant_orders_7) else getString(R.string.merchant_orders_8),
                    if (ordersSnapshot.isEmpty()) {
                        getString(R.string.merchant_orders_9)
                    } else {
                        getString(R.string.merchant_orders_10)
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
                    addView(MerchantUi.text(context, getString(R.string.order_details_1, (order.id.take(8).uppercase())), 16f, true))
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
                MerchantUi.text(context, getString(R.string.monetization_8, (money(order.total))), 18f, true).apply {
                    setPadding(0, MerchantUi.dp(context, 9), 0, MerchantUi.dp(context, 4))
                }
            )
            if (order.subtotal > 0 || order.delivery_fee > 0 || order.discount > 0) {
                addView(
                    MerchantUi.muted(
                        context,
                        buildString {
                            append(getString(R.string.merchant_orders_11, (money(order.subtotal))))
                            append(getString(R.string.merchant_orders_12, (money(order.delivery_fee))))
                            if (order.discount > 0) append(getString(R.string.merchant_orders_13, (money(order.discount))))
                        },
                        12f
                    )
                )
            }

            addView(MerchantUi.sectionTitle(context, getString(R.string.merchant_orders_14)))
            addView(MerchantUi.text(context, order.customer_name_snapshot ?: getString(R.string.merchant_orders_15), 14.5f, true))
            addView(
                MerchantUi.muted(context, getString(R.string.merchant_orders_16, order.phone), 12.5f).apply {
                    setPadding(0, MerchantUi.dp(context, 3), 0, 0)
                }
            )
            addView(
                MerchantUi.muted(context, getString(R.string.merchant_orders_17, order.address), 12.5f).apply {
                    setPadding(0, MerchantUi.dp(context, 3), 0, 0)
                }
            )

            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                setPadding(0, MerchantUi.dp(context, 8), 0, 0)
                addView(
                    MerchantUi.compactButton(context, getString(R.string.merchant_orders_18)) { callCustomer(order.phone) },
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginEnd = MerchantUi.dp(context, 3)
                    }
                )
                addView(
                    MerchantUi.compactButton(context, getString(R.string.merchant_orders_19)) { copyAddress(order.address) },
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = MerchantUi.dp(context, 3)
                    }
                )
            })

            order.customer_note?.takeIf { it.isNotBlank() }?.let { note ->
                addView(
                    MerchantUi.card(context, soft = true).apply {
                        addView(MerchantUi.muted(context, getString(R.string.merchant_orders_20), 11.5f))
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
                    MerchantUi.secondaryButton(context, getString(R.string.merchant_orders_21)) { showItems(order) },
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginEnd = MerchantUi.dp(context, 3)
                    }
                )
                addView(
                    MerchantUi.secondaryButton(context, getString(R.string.merchant_orders_22)) { showHistory(order) },
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = MerchantUi.dp(context, 3)
                    }
                )
            })

            val actions = nextActions(order.status)
            if (transitioningOrderIds.contains(order.id)) {
                addView(
                    MerchantUi.card(context, soft = true).apply {
                        addView(MerchantUi.text(context, getString(R.string.merchant_orders_23), 13.5f, true))
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
                        box.addView(MerchantUi.muted(context, getString(R.string.merchant_orders_24)))
                    }
                    items.forEach { item ->
                        box.addView(MerchantUi.card(context).apply {
                            val variant = item.variant_snapshot?.get("name")?.jsonPrimitive?.contentOrNull
                            addView(
                                MerchantUi.text(
                                    context,
                                    buildString {
                                        append(item.product_name_snapshot ?: getString(R.string.order_details_13))
                                        variant?.let { append(getString(R.string.merchant_orders_25, it)) }
                                    },
                                    14.5f,
                                    true
                                )
                            )
                            addView(
                                MerchantUi.muted(
                                    context,
                                    getString(R.string.merchant_orders_26, item.quantity.toString(), (money(item.unit_price)), (money(item.line_total ?: item.unit_price * item.quantity))),
                                    12f
                                ).apply { setPadding(0, MerchantUi.dp(context, 4), 0, 0) }
                            )
                        })
                    }
                    MaterialAlertDialogBuilder(context)
                        .setTitle(getString(R.string.merchant_orders_27))
                        .setView(box)
                        .setPositiveButton(getString(R.string.merchant_orders_28), null)
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
                        box.addView(MerchantUi.muted(context, getString(R.string.merchant_orders_29)))
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
                        .setTitle(getString(R.string.merchant_orders_30))
                        .setView(box)
                        .setPositiveButton(getString(R.string.merchant_orders_28), null)
                        .show()
                }
                .onFailure { toast(friendlyError(it.message)) }
        }
    }

    private fun confirmTransition(order: Order, status: String) {
        if (transitioningOrderIds.contains(order.id)) {
            toast(getString(R.string.merchant_orders_31))
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
                hint = getString(R.string.merchant_orders_32)
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
                    "rejected" -> getString(R.string.merchant_orders_33)
                    "cancelled" -> getString(R.string.merchant_orders_34)
                    "failed" -> getString(R.string.merchant_orders_35)
                    else -> getString(R.string.merchant_orders_36)
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
            .setTitle(getString(R.string.merchant_onboarding_81))
            .setPositiveButton(getString(R.string.merchant_orders_37), null)
            .setNegativeButton(getString(R.string.addresses_19), null)

        if (content != null) {
            builder.setMessage(
                if (requiresEta) {
                    getString(R.string.merchant_orders_38)
                } else {
                    getString(R.string.merchant_orders_39)
                }
            ).setView(content)
        } else {
            builder.setMessage(getString(R.string.merchant_orders_40, (statusText(status))))
        }

        val dialog = builder.create()
        dialog.setOnShowListener {
            val positive = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
            val negative = dialog.getButton(DialogInterface.BUTTON_NEGATIVE)
            positive.setOnClickListener {
                val note = noteField?.text?.toString()?.trim().orEmpty()
                val eta = etaField?.text?.toString()?.trim()?.toIntOrNull()

                if (requiresReason && note.length < 3) {
                    toast(getString(R.string.merchant_orders_41))
                    return@setOnClickListener
                }
                if (note.length > 350) {
                    toast(getString(R.string.merchant_orders_42))
                    return@setOnClickListener
                }
                if (requiresEta && (eta == null || eta !in 1..1440)) {
                    toast(getString(R.string.merchant_orders_43))
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
                        toast(getString(R.string.merchant_orders_44, (statusText(status))))
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
            OrderAction(getString(R.string.merchant_orders_45), "accepted"),
            OrderAction(getString(R.string.merchant_orders_46), "rejected", true)
        )
        "accepted" -> listOf(
            OrderAction(getString(R.string.merchant_orders_47), "preparing"),
            OrderAction(getString(R.string.addresses_19), "cancelled", true)
        )
        "preparing" -> listOf(
            OrderAction(getString(R.string.merchant_orders_48), "ready"),
            OrderAction(getString(R.string.addresses_19), "cancelled", true)
        )
        "ready" -> listOf(
            OrderAction(getString(R.string.merchant_orders_49), "out_for_delivery"),
            OrderAction(getString(R.string.addresses_19), "cancelled", true)
        )
        "out_for_delivery" -> listOf(
            OrderAction(getString(R.string.merchant_analytics_5), "delivered"),
            OrderAction(getString(R.string.merchant_orders_50), "failed", true)
        )
        else -> emptyList()
    }

    private fun callCustomer(phone: String) {
        val clean = phone.trim()
        if (clean.isBlank()) {
            toast(getString(R.string.merchant_orders_51))
            return
        }
        runCatchingCancellable {
            startActivity(Intent(Intent.ACTION_DIAL, "tel:${Uri.encode(clean)}".toUri()))
        }.onFailure { toast(getString(R.string.merchant_orders_52)) }
    }

    private fun copyAddress(address: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.merchant_orders_53), address))
        toast(getString(R.string.merchant_orders_54))
    }

    private fun statusRank(status: String): Int = when (status) {
        "pending" -> 0
        "accepted", "preparing", "ready", "out_for_delivery" -> 1
        else -> 2
    }

    private fun statusText(status: String): String = when (status) {
        "pending" -> getString(R.string.merchant_orders_55)
        "accepted" -> getString(R.string.merchant_orders_56)
        "preparing" -> getString(R.string.merchant_orders_57)
        "ready" -> getString(R.string.merchant_orders_58)
        "out_for_delivery" -> getString(R.string.merchant_orders_59)
        "delivered" -> getString(R.string.merchant_analytics_5)
        "rejected" -> getString(R.string.monetization_34)
        "cancelled" -> getString(R.string.monetization_35)
        "failed" -> getString(R.string.merchant_orders_50)
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
            lower.contains("final state") -> getString(R.string.merchant_orders_60)
            lower.contains("transition is not allowed") -> getString(R.string.merchant_orders_61)
            lower.contains("reason is required") -> getString(R.string.merchant_orders_62)
            lower.contains("delivery eta") -> getString(R.string.merchant_orders_63)
            lower.contains("only this order seller") || lower.contains("permission") || lower.contains("42501") ->
                getString(R.string.merchant_orders_64)
            lower.contains("order not found") -> getString(R.string.merchant_orders_65)
            lower.contains("timeout") || lower.contains("timed out") || lower.contains("failed to connect") ||
                lower.contains("unable to resolve") || lower.contains("network") || lower.contains("host") ->
                getString(R.string.merchant_orders_66)
            else -> CustomerErrorMessages.fromMessage(value, getString(R.string.merchant_orders_67))
        }
    }

    private fun showError(message: String) {
        val context = requireContext()
        root.removeAllViews()
        root.addView(MerchantUi.title(context, getString(R.string.merchant_orders_1)))
        root.addView(MerchantUi.card(context).apply {
            addView(MerchantUi.text(context, getString(R.string.orders_2), 16f, true))
            addView(
                MerchantUi.muted(context, message).apply {
                    setPadding(0, MerchantUi.dp(context, 5), 0, MerchantUi.dp(context, 10))
                }
            )
            addView(MerchantUi.secondaryButton(context, getString(R.string.notifications_5)) { load() })
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
