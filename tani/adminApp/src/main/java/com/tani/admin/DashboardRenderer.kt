package com.tani.admin

import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.tani.admin.data.bool
import com.tani.admin.data.numberText
import com.tani.admin.data.string
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class DashboardRenderer(
    private val activity: AppCompatActivity,
    private val role: () -> String?,
    private val actions: Actions
) {
    data class Metric(
        val label: String,
        val value: String,
        val hint: String? = null,
        val section: AdminSection? = null
    )

    interface Actions {
        fun navigate(section: AdminSection)
        fun selectFilter(value: String)
        fun nextPage()
        fun previousPage()
        fun openMerchantDocument(merchantId: String)
        fun reviewMerchant(row: JsonObject, status: String)
        fun verifyMerchantPhone(row: JsonObject)
        fun transitionOrder(row: JsonObject, status: String)
        fun updateComplaint(id: String, status: String)
        fun resolveComplaint(id: String)
        fun updateTicket(id: String, status: String)
        fun reviewRequest(rpc: String, id: String, status: String)
        fun moderateReview(row: JsonObject, hide: Boolean)
        fun setAccountActive(row: JsonObject, active: Boolean)
        fun setProductActive(row: JsonObject, active: Boolean)
        fun setCategoryActive(row: JsonObject, active: Boolean)
        fun setCityActive(row: JsonObject, active: Boolean)
        fun setDeliveryProviderActive(row: JsonObject, active: Boolean)
        fun showTechnicalDetails(row: JsonObject)
    }

    private val root: LinearLayout
        get() = activity.findViewById(R.id.adminScreenContainer)

    fun loading(section: AdminSection) {
        root.removeAllViews()
        addScreenHeader(section.title, section.subtitle)
        addEmpty(activity.getString(R.string.dashboard_renderer_1))
    }

    fun renderDashboard(
        kpis: JsonObject,
        attention: Map<String, Int>,
        recentOrders: JsonArray,
        alerts: JsonArray
    ) {
        clear(AdminSection.DASHBOARD)
        val orders = kpis.double("orders")
        val completed = kpis.double("completed_orders")
        val cancelled = kpis.double("cancelled_orders")
        renderMetrics(
            listOf(
                Metric(activity.getString(R.string.dashboard_renderer_2), orders.asCount(), section = AdminSection.ORDERS),
                Metric(activity.getString(R.string.dashboard_renderer_3), completed.asCount(), AdminUiText.percent(completed, orders), AdminSection.ORDERS),
                Metric(activity.getString(R.string.dashboard_renderer_4), AdminUiText.money(kpis.double("gmv")), activity.getString(R.string.dashboard_renderer_5), AdminSection.REPORTS),
                Metric(activity.getString(R.string.dashboard_renderer_6), cancelled.asCount(), AdminUiText.percent(cancelled, orders), AdminSection.ORDERS)
            )
        )

        addSectionTitle(activity.getString(R.string.dashboard_renderer_7))
        val cards = listOf(
            Triple(activity.getString(R.string.dashboard_renderer_8), attention["pending_orders"] ?: 0, AdminSection.ORDERS),
            Triple(activity.getString(R.string.dashboard_renderer_9), attention["pending_merchants"] ?: 0, AdminSection.MERCHANTS),
            Triple(activity.getString(R.string.dashboard_renderer_10), attention["open_complaints"] ?: 0, AdminSection.SUPPORT),
            Triple(activity.getString(R.string.dashboard_renderer_11), attention["open_alerts"] ?: 0, AdminSection.SYSTEM)
        )
        cards.forEach { (label, count, section) ->
            actionCard(label, if (count == 0) activity.getString(R.string.dashboard_renderer_12) else activity.getString(R.string.dashboard_renderer_13, count), count > 0) {
                actions.navigate(section)
            }
        }

        if (alerts.isNotEmpty()) {
            addSectionTitle(activity.getString(R.string.dashboard_renderer_14))
            alerts.take(3).forEach { element ->
                val row = element.jsonObject
                itemCard {
                    addStatus(row.string("severity").orEmpty(), severityLabel(row.string("severity")))
                    addTitle(row.string("title") ?: activity.getString(R.string.dashboard_renderer_15))
                    addMeta(formatDate(row.string("created_at")))
                    addActions(Action(activity.getString(R.string.dashboard_renderer_16), secondary = true) { actions.navigate(AdminSection.SYSTEM) })
                }
            }
        }

        addSectionTitle(activity.getString(R.string.dashboard_renderer_17), activity.getString(R.string.dashboard_renderer_18))
        if (recentOrders.isEmpty()) addEmpty(activity.getString(R.string.dashboard_renderer_19))
        recentOrders.forEach { element -> orderCard(element.jsonObject, compact = true) }
        textAction(activity.getString(R.string.dashboard_renderer_20)) { actions.navigate(AdminSection.ORDERS) }
    }

    fun renderOrders(rows: JsonArray, selected: String, page: Int, hasNext: Boolean) {
        clear(AdminSection.ORDERS)
        addFilters(
            listOf("all" to activity.getString(R.string.dashboard_renderer_21), "pending" to activity.getString(R.string.dashboard_renderer_22), "active" to activity.getString(R.string.dashboard_renderer_23), "delivered" to activity.getString(R.string.dashboard_renderer_24), "cancelled" to activity.getString(R.string.dashboard_renderer_25)),
            selected
        )
        if (rows.isEmpty()) addEmpty(activity.getString(R.string.dashboard_renderer_26))
        rows.forEach { orderCard(it.jsonObject, compact = false) }
        addPagination(page, hasNext)
    }

    fun renderMerchants(rows: JsonArray, selected: String, page: Int, hasNext: Boolean) {
        clear(AdminSection.MERCHANTS)
        addFilters(
            listOf("all" to activity.getString(R.string.dashboard_renderer_21), "pending" to activity.getString(R.string.dashboard_renderer_27), "approved" to activity.getString(R.string.dashboard_renderer_28), "changes_requested" to activity.getString(R.string.dashboard_renderer_29), "suspended" to activity.getString(R.string.dashboard_renderer_30), "rejected" to activity.getString(R.string.dashboard_renderer_31)),
            selected
        )
        if (rows.isEmpty()) addEmpty(activity.getString(R.string.dashboard_renderer_32))
        rows.forEach { element ->
            val row = element.jsonObject
            itemCard {
                addStatus(row.string("verification_status").orEmpty(), AdminUiText.merchantStatus(row.string("verification_status")))
                addTitle(row.string("store_name") ?: row.string("business_name") ?: activity.getString(R.string.admin_management_dialog_23))
                addMeta(row.string("business_name") ?: "—")
                addMeta("${row.string("phone") ?: "بلا رقم"} • ${row.string("city") ?: "—"}")
                row.string("phone_verified_at")?.let { addMeta(activity.getString(R.string.dashboard_renderer_33, (formatDate(it)))) }
                row.string("requested_category")?.takeIf { it.isNotBlank() }?.let {
                    addNote(activity.getString(R.string.dashboard_renderer_34, it))
                }
                (row["delivery_zones"] as? JsonArray)?.takeIf { it.isNotEmpty() }?.let { zones ->
                    val summary = zones.joinToString(" • ") { element ->
                        val zone = element.jsonObject
                        "${zone.string("area") ?: "—"}: ${AdminUiText.money(zone.double("fee"))}"
                    }
                    addMeta(activity.getString(R.string.dashboard_renderer_35, summary))
                }
                addMeta("أُرسل: ${formatDate(row.string("submitted_at") ?: row.string("created_at"))}")
                row.string("review_note")?.takeIf { it.isNotBlank() }?.let { addNote(activity.getString(R.string.dashboard_renderer_36, it)) }
                addActions(Action(activity.getString(R.string.dashboard_renderer_37)) { actions.openMerchantDocument(row.string("id").orEmpty()) })
                if (role() == "admin") {
                    if (row.string("verification_status") in setOf("pending", "changes_requested", "rejected", "approved", "suspended")) {
                        addActions(Action(activity.getString(R.string.dashboard_renderer_38), secondary = true) { actions.verifyMerchantPhone(row) })
                    }
                    when (row.string("verification_status")) {
                        "pending", "changes_requested", "rejected" -> addActions(
                            Action(activity.getString(R.string.dashboard_renderer_39)) { actions.reviewMerchant(row, "approved") },
                            Action(activity.getString(R.string.dashboard_renderer_40), secondary = true) { actions.reviewMerchant(row, "changes_requested") },
                            Action(activity.getString(R.string.dashboard_renderer_41), danger = true) { actions.reviewMerchant(row, "rejected") }
                        )
                        "approved" -> addActions(Action(activity.getString(R.string.dashboard_renderer_42), danger = true) { actions.reviewMerchant(row, "suspended") })
                        "suspended" -> addActions(Action(activity.getString(R.string.dashboard_renderer_43)) { actions.reviewMerchant(row, "approved") })
                    }
                }
            }
        }
        addPagination(page, hasNext)
    }

    fun renderSupport(mode: String, rows: JsonArray, selectedStatus: String) {
        clear(AdminSection.SUPPORT)
        addFilters(listOf("complaints" to activity.getString(R.string.dashboard_renderer_44), "tickets" to activity.getString(R.string.dashboard_renderer_45), "reviews" to activity.getString(R.string.dashboard_renderer_46)), mode)
        addFilters(listOf("all" to activity.getString(R.string.dashboard_renderer_47), "open" to activity.getString(R.string.dashboard_renderer_48), "in_progress" to activity.getString(R.string.dashboard_renderer_49), "closed" to activity.getString(R.string.dashboard_renderer_50)), selectedStatus, prefix = "status:")
        if (rows.isEmpty()) addEmpty(activity.getString(R.string.dashboard_renderer_51))
        when (mode) {
            "tickets" -> rows.forEach { renderTicket(it.jsonObject) }
            "reviews" -> rows.forEach { renderReviewReport(it.jsonObject) }
            else -> rows.forEach { renderComplaint(it.jsonObject) }
        }
    }

    fun renderReports(kpis: JsonObject, period: String) {
        clear(AdminSection.REPORTS)
        addFilters(listOf("7" to activity.getString(R.string.dashboard_renderer_52), "30" to activity.getString(R.string.dashboard_renderer_53), "90" to activity.getString(R.string.dashboard_renderer_54)), period)
        val orders = kpis.double("orders")
        val completed = kpis.double("completed_orders")
        val cancelled = kpis.double("cancelled_orders")
        renderMetrics(
            listOf(
                Metric(activity.getString(R.string.dashboard_renderer_55), orders.asCount(), activity.getString(R.string.dashboard_renderer_56)),
                Metric(activity.getString(R.string.dashboard_renderer_57), completed.asCount(), AdminUiText.percent(completed, orders)),
                Metric(activity.getString(R.string.dashboard_renderer_58), AdminUiText.money(kpis.double("gmv"))),
                Metric(activity.getString(R.string.dashboard_renderer_59), cancelled.asCount(), AdminUiText.percent(cancelled, orders)),
                Metric(activity.getString(R.string.dashboard_renderer_60), kpis.double("active_customers").asCount()),
                Metric(activity.getString(R.string.dashboard_renderer_61), kpis.double("active_merchants").asCount()),
                Metric(activity.getString(R.string.dashboard_renderer_62), kpis.double("repeat_customers").asCount()),
                Metric(activity.getString(R.string.dashboard_renderer_63), kpis.double("product_views").asCount()),
                Metric(activity.getString(R.string.dashboard_renderer_64), kpis.double("searches").asCount()),
                Metric(activity.getString(R.string.dashboard_renderer_44), kpis.double("complaints").asCount()),
                Metric(activity.getString(R.string.dashboard_renderer_65), kpis.double("app_errors").asCount())
            )
        )
        addInfo(activity.getString(R.string.dashboard_renderer_66))
    }

    fun renderCustomers(rows: JsonArray, selected: String, page: Int, hasNext: Boolean) {
        clear(AdminSection.CUSTOMERS)
        addFilters(listOf("all" to activity.getString(R.string.dashboard_renderer_21), "customer" to activity.getString(R.string.dashboard_renderer_67), "seller" to activity.getString(R.string.dashboard_renderer_68), "inactive" to activity.getString(R.string.dashboard_renderer_69)), selected)
        if (rows.isEmpty()) addEmpty(activity.getString(R.string.dashboard_renderer_70))
        rows.forEach { element ->
            val row = element.jsonObject
            itemCard {
                addStatus(if (row.bool("is_active")) "active" else "inactive", if (row.bool("is_active")) activity.getString(R.string.dashboard_renderer_71) else activity.getString(R.string.dashboard_renderer_72))
                addTitle(row.string("name") ?: activity.getString(R.string.dashboard_renderer_73))
                addMeta("${AdminUiText.role(row.string("role"))} • ${row.string("phone") ?: "بلا رقم"}")
                addMeta("أنشئ: ${formatDate(row.string("created_at"))}")
                if (role() == "admin" && row.string("id") != com.tani.admin.data.AdminApi.userId) {
                    if (row.bool("is_active")) addActions(Action(activity.getString(R.string.dashboard_renderer_74), danger = true) { actions.setAccountActive(row, false) })
                    else addActions(Action(activity.getString(R.string.dashboard_renderer_75)) { actions.setAccountActive(row, true) })
                }
            }
        }
        addPagination(page, hasNext)
    }

    fun renderProducts(rows: JsonArray, selected: String, page: Int, hasNext: Boolean) {
        clear(AdminSection.PRODUCTS)
        addFilters(listOf("all" to activity.getString(R.string.dashboard_renderer_21), "active" to activity.getString(R.string.dashboard_renderer_76), "inactive" to activity.getString(R.string.dashboard_renderer_77), "out_of_stock" to activity.getString(R.string.dashboard_renderer_78)), selected)
        if (rows.isEmpty()) addEmpty(activity.getString(R.string.dashboard_renderer_79))
        rows.forEach { element ->
            val row = element.jsonObject
            itemCard {
                val available = row.bool("is_active")
                addStatus(if (available) "active" else "inactive", if (available) activity.getString(R.string.dashboard_renderer_80) else activity.getString(R.string.dashboard_renderer_81))
                addTitle(row.string("name") ?: activity.getString(R.string.dashboard_renderer_82))
                addMeta("السعر: ${AdminUiText.money(row.double("price"))} • المخزون: ${row.numberText("stock")}")
                addMeta("أضيف: ${formatDate(row.string("created_at"))}")
                if (role() == "admin") {
                    if (available) addActions(Action(activity.getString(R.string.dashboard_renderer_83), danger = true) { actions.setProductActive(row, false) })
                    else addActions(Action(activity.getString(R.string.dashboard_renderer_84)) { actions.setProductActive(row, true) })
                }
            }
        }
        addPagination(page, hasNext)
    }

    fun renderMonetization(mode: String, rows: JsonArray) {
        clear(AdminSection.MONETIZATION)
        addFilters(listOf("subscriptions" to activity.getString(R.string.dashboard_renderer_85), "featured" to activity.getString(R.string.dashboard_renderer_86)), mode)
        if (rows.isEmpty()) addEmpty(activity.getString(R.string.dashboard_renderer_87))
        rows.forEach { element ->
            val row = element.jsonObject
            itemCard {
                addStatus(row.string("status").orEmpty(), requestStatus(row.string("status")))
                if (mode == "subscriptions") {
                    addTitle(activity.getString(R.string.dashboard_renderer_88))
                    addMeta("الخطة: ${row.string("plan_id")?.take(8) ?: "—"}")
                } else {
                    addTitle(row.string("placement") ?: activity.getString(R.string.dashboard_renderer_89))
                    addMeta(if (row.string("product_id") == null) activity.getString(R.string.dashboard_renderer_90) else activity.getString(R.string.dashboard_renderer_91))
                }
                addMeta(formatDate(row.string("created_at")))
                if (role() == "admin" && row.string("status") == "pending") {
                    val rpc = if (mode == "subscriptions") "admin_review_subscription_request" else "admin_review_featured_request"
                    addActions(
                        Action(activity.getString(R.string.dashboard_renderer_39)) { actions.reviewRequest(rpc, row.string("id").orEmpty(), "approved") },
                        Action(activity.getString(R.string.dashboard_renderer_41), danger = true) { actions.reviewRequest(rpc, row.string("id").orEmpty(), "rejected") }
                    )
                }
            }
        }
    }

    fun renderConfiguration(mode: String, rows: JsonArray) {
        clear(AdminSection.CONFIGURATION)
        addFilters(listOf("categories" to activity.getString(R.string.dashboard_renderer_92), "cities" to activity.getString(R.string.dashboard_renderer_93), "delivery" to activity.getString(R.string.dashboard_renderer_94)), mode)
        if (rows.isEmpty()) addEmpty(activity.getString(R.string.dashboard_renderer_95))
        rows.forEach { element ->
            val row = element.jsonObject
            itemCard {
                val active = row.bool("is_active")
                addStatus(if (active) "active" else "inactive", if (active) activity.getString(R.string.dashboard_renderer_96) else activity.getString(R.string.dashboard_renderer_97))
                when (mode) {
                    "cities" -> {
                        addTitle(row.string("name") ?: activity.getString(R.string.dashboard_renderer_98))
                        addMeta("${row.string("state_name") ?: "—"} • ${row.string("code") ?: "—"}")
                    }
                    "delivery" -> {
                        addTitle(row.string("display_name") ?: activity.getString(R.string.dashboard_renderer_99))
                        addMeta(providerType(row.string("provider_type")))
                    }
                    else -> {
                        addTitle(row.string("name") ?: activity.getString(R.string.dashboard_renderer_100))
                        addMeta("الترتيب: ${row.numberText("sort_order")}")
                    }
                }
                if (role() == "admin") {
                    val label = if (active) activity.getString(R.string.dashboard_renderer_101) else activity.getString(R.string.dashboard_renderer_102)
                    val action = when (mode) {
                        "cities" -> Action(label, danger = active) { actions.setCityActive(row, !active) }
                        "delivery" -> Action(label, danger = active) { actions.setDeliveryProviderActive(row, !active) }
                        else -> Action(label, danger = active) { actions.setCategoryActive(row, !active) }
                    }
                    addActions(action)
                }
            }
        }
    }

    fun renderSystem(mode: String, rows: JsonArray, stats: Map<String, Int> = emptyMap()) {
        clear(AdminSection.SYSTEM)
        addFilters(listOf("health" to activity.getString(R.string.dashboard_renderer_103), "alerts" to activity.getString(R.string.dashboard_renderer_104), "errors" to activity.getString(R.string.dashboard_renderer_105), "audit" to activity.getString(R.string.dashboard_renderer_106)), mode)
        if (mode == "health") {
            renderMetrics(
                listOf(
                    Metric(activity.getString(R.string.dashboard_renderer_107), (stats["errors_24h"] ?: 0).toString()),
                    Metric(activity.getString(R.string.dashboard_renderer_108), (stats["open_alerts"] ?: 0).toString()),
                    Metric(activity.getString(R.string.dashboard_renderer_109), (stats["pending_orders"] ?: 0).toString()),
                    Metric(activity.getString(R.string.dashboard_renderer_110), (stats["open_tickets"] ?: 0).toString()),
                    Metric(activity.getString(R.string.dashboard_renderer_111), if (stats["worker_missing"] == 0) activity.getString(R.string.dashboard_renderer_112) else activity.getString(R.string.dashboard_renderer_113)),
                    Metric(activity.getString(R.string.dashboard_renderer_114), if (stats["push_configuration_missing"] == 0) activity.getString(R.string.dashboard_renderer_115) else activity.getString(R.string.dashboard_renderer_116)),
                    Metric(activity.getString(R.string.dashboard_renderer_117), (stats["failed_push"] ?: 0).toString()),
                    Metric(activity.getString(R.string.dashboard_renderer_118), (stats["failed_erasure"] ?: 0).toString())
                )
            )
            val healthy = stats.values.all { it == 0 }
            addInfo(if (healthy) activity.getString(R.string.dashboard_renderer_119) else activity.getString(R.string.dashboard_renderer_120))
            return
        }
        if (rows.isEmpty()) addEmpty(activity.getString(R.string.dashboard_renderer_121))
        when (mode) {
            "errors" -> rows.forEach { element ->
                val row = element.jsonObject
                itemCard {
                    addStatus("error", activity.getString(R.string.dashboard_renderer_122))
                    addTitle(AdminUiText.friendlyError("${row.string("error_type")} ${row.string("message")}"))
                    addMeta("${sourceLabel(row.string("source"))} • ${formatDate(row.string("created_at"))}")
                    addActions(Action(activity.getString(R.string.dashboard_renderer_123), secondary = true) { actions.showTechnicalDetails(row) })
                }
            }
            "audit" -> rows.forEach { element ->
                val row = element.jsonObject
                itemCard {
                    addTitle(auditAction(row.string("action")))
                    addMeta("${entityLabel(row.string("entity_type"))} • ${row.string("entity_id")?.take(8) ?: "—"}")
                    addMeta("${row.string("source") ?: "النظام"} • ${formatDate(row.string("created_at"))}")
                }
            }
            else -> rows.forEach { element ->
                val row = element.jsonObject
                itemCard {
                    addStatus(row.string("severity").orEmpty(), severityLabel(row.string("severity")))
                    addTitle(row.string("title") ?: activity.getString(R.string.dashboard_renderer_124))
                    addMeta("الحالة: ${alertStatus(row.string("status"))} • ${formatDate(row.string("created_at"))}")
                }
            }
        }
    }

    private fun orderCard(row: JsonObject, compact: Boolean) = itemCard {
        val status = row.string("status")
        addStatus(status.orEmpty(), AdminUiText.orderStatus(status))
        addTitle("طلب #${row.string("id")?.take(8) ?: "—"}")
        addMeta("${row.string("store_name_snapshot") ?: "متجر"} • ${row.string("customer_name_snapshot") ?: "عميل"}")
        addMeta("${AdminUiText.money(row.double("total"))} • ${formatDate(row.string("created_at"))}")
        if (!compact) {
            addMeta("الدفع: ${if (row.string("payment_method") == "cod") "عند الاستلام" else row.string("payment_method") ?: "—"} • ${paymentStatus(row.string("payment_status"))}")
            (row["order_items"] as? JsonArray)?.forEach { element ->
                val item = element.jsonObject
                val variant = (item["variant_snapshot"] as? JsonObject)?.string("name")
                addMeta(buildString {
                    append("• ${item.string("product_name_snapshot") ?: "منتج"}")
                    variant?.let { append(activity.getString(R.string.dashboard_renderer_125, it)) }
                    append(" • ${item.numberText("quantity")} قطعة")
                })
            }
            if (role() == "admin") {
                val next = when (status) {
                    "pending" -> listOf("accepted", "rejected", "cancelled")
                    "accepted" -> listOf("preparing", "cancelled")
                    "preparing" -> listOf("ready", "cancelled")
                    "ready" -> listOf("out_for_delivery", "cancelled")
                    "out_for_delivery" -> listOf("delivered", "failed")
                    else -> emptyList()
                }
                if (next.isNotEmpty()) addActions(*next.map { value ->
                    Action(AdminUiText.orderStatus(value), danger = value in setOf("cancelled", "rejected", "failed")) {
                        actions.transitionOrder(row, value)
                    }
                }.toTypedArray())
            }
        }
    }

    private fun renderComplaint(row: JsonObject) = itemCard {
        addStatus(row.string("priority").orEmpty(), AdminUiText.priority(row.string("priority")))
        addTitle(row.string("subject") ?: activity.getString(R.string.dashboard_renderer_126))
        addMeta("${AdminUiText.supportStatus(row.string("status"))} • ${formatDate(row.string("created_at"))}")
        row.string("description")?.let { addNote(it) }
        if (row.string("status") in setOf("open", "in_progress")) addActions(
            Action(activity.getString(R.string.dashboard_renderer_127), secondary = true) { actions.updateComplaint(row.string("id").orEmpty(), "in_progress") },
            Action(activity.getString(R.string.dashboard_renderer_128)) { actions.resolveComplaint(row.string("id").orEmpty()) }
        )
    }

    private fun renderTicket(row: JsonObject) = itemCard {
        addStatus(row.string("priority").orEmpty(), AdminUiText.priority(row.string("priority")))
        addTitle(row.string("subject") ?: activity.getString(R.string.dashboard_renderer_129))
        addMeta("${AdminUiText.supportStatus(row.string("status"))} • ${formatDate(row.string("created_at"))}")
        row.string("description")?.let { addNote(it) }
        if (row.string("status") in setOf("open", "in_progress")) addActions(
            Action(activity.getString(R.string.dashboard_renderer_127), secondary = true) { actions.updateTicket(row.string("id").orEmpty(), "in_progress") },
            Action(activity.getString(R.string.admin_management_dialog_4)) { actions.updateTicket(row.string("id").orEmpty(), "closed") }
        )
    }

    private fun renderReviewReport(row: JsonObject) = itemCard {
        addStatus(row.string("status").orEmpty(), requestStatus(row.string("status")))
        addTitle("بلاغ عن تقييم #${row.string("review_id")?.take(8) ?: "—"}")
        addMeta("${row.string("reason") ?: "دون سبب"} • ${formatDate(row.string("created_at"))}")
        if (row.string("status") == "pending") addActions(
            Action(activity.getString(R.string.dashboard_renderer_130), danger = true) { actions.moderateReview(row, true) },
            Action(activity.getString(R.string.dashboard_renderer_131), secondary = true) { actions.moderateReview(row, false) }
        )
    }

    private fun clear(section: AdminSection) {
        root.removeAllViews()
        addScreenHeader(section.title, section.subtitle)
    }

    private fun addScreenHeader(title: String, subtitle: String) {
        root.addView(TextView(activity).apply {
            text = title
            textSize = 26f
            setTextColor(color(R.color.text_dark))
            setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(activity).apply {
            text = subtitle
            textSize = 14f
            setTextColor(color(R.color.text_muted))
            setPadding(0, dp(4), 0, dp(12))
        })
    }

    private fun renderMetrics(items: List<Metric>) {
        val grid = GridLayout(activity).apply { columnCount = 2 }
        items.forEach { metric ->
            val card = MaterialCardView(activity).apply {
                radius = dp(18).toFloat()
                strokeWidth = dp(1)
                strokeColor = color(R.color.border)
                cardElevation = 0f
                setCardBackgroundColor(color(R.color.tani_surface))
                isClickable = metric.section != null
                isFocusable = metric.section != null
                metric.section?.let { section -> setOnClickListener { actions.navigate(section) } }
            }
            card.addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(15), dp(14), dp(15), dp(14))
                addView(label(metric.label))
                addView(title(metric.value, 24f))
                metric.hint?.let { addView(meta(it)) }
            })
            grid.addView(card, GridLayout.LayoutParams().apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(4), dp(4), dp(4), dp(4))
            })
        }
        root.addView(grid, matchWrap().apply { bottomMargin = dp(8) })
    }

    private fun addFilters(items: List<Pair<String, String>>, selected: String, prefix: String = "") {
        val chips = ChipGroup(activity).apply {
            isSingleSelection = true
            isSelectionRequired = true
            setPadding(dp(2), 0, dp(2), dp(6))
        }
        items.forEach { (value, label) ->
            chips.addView(Chip(activity).apply {
                text = label
                isCheckable = true
                isChecked = value == selected
                setOnClickListener { actions.selectFilter(prefix + value) }
            })
        }
        root.addView(HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
            addView(chips)
        }, matchWrap())
    }

    private fun actionCard(title: String, description: String, urgent: Boolean, action: () -> Unit) {
        val card = MaterialCardView(activity).apply {
            radius = dp(16).toFloat()
            strokeWidth = dp(1)
            strokeColor = color(if (urgent) R.color.warning else R.color.border)
            setCardBackgroundColor(color(if (urgent) R.color.warning_soft else R.color.tani_surface))
            cardElevation = 0f
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }
        card.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(13), dp(15), dp(13))
            addView(title(title, 16f))
            addView(meta(description))
        })
        root.addView(card, matchWrap().apply { bottomMargin = dp(8) })
    }

    private fun addSectionTitle(value: String, subtitle: String? = null) {
        root.addView(title(value, 20f).apply { setPadding(0, dp(18), 0, dp(4)) })
        subtitle?.let { root.addView(meta(it).apply { setPadding(0, 0, 0, dp(8)) }) }
    }

    private fun addEmpty(message: String) {
        root.addView(MaterialCardView(activity).apply {
            radius = dp(16).toFloat()
            strokeWidth = dp(1)
            strokeColor = color(R.color.border)
            setCardBackgroundColor(color(R.color.tani_surface))
            addView(meta(message).apply { gravity = Gravity.CENTER; setPadding(dp(20), dp(28), dp(20), dp(28)) })
        }, matchWrap().apply { topMargin = dp(8) })
    }

    private fun addInfo(message: String) {
        root.addView(TextView(activity).apply {
            text = message
            textSize = 13f
            setTextColor(color(R.color.tani_secondary))
            setBackgroundColor(color(R.color.info_soft))
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }, matchWrap().apply { topMargin = dp(12) })
    }

    private fun addPagination(page: Int, hasNext: Boolean) {
        val row = LinearLayout(activity).apply { gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL }
        row.addView(MaterialButton(activity).apply {
            text = activity.getString(R.string.dashboard_renderer_132)
            isEnabled = page > 0
            setOnClickListener { actions.previousPage() }
        })
        row.addView(meta(activity.getString(R.string.dashboard_renderer_133, (page + 1))).apply { setPadding(dp(16), 0, dp(16), 0) })
        row.addView(MaterialButton(activity).apply {
            text = activity.getString(R.string.dashboard_renderer_134)
            isEnabled = hasNext
            setOnClickListener { actions.nextPage() }
        })
        root.addView(row, matchWrap().apply { topMargin = dp(14) })
    }

    private fun textAction(label: String, action: () -> Unit) {
        root.addView(MaterialButton(activity).apply {
            text = label
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setTextColor(color(R.color.tani_primary))
            setOnClickListener { action() }
        }, matchWrap())
    }

    private data class Action(
        val label: String,
        val danger: Boolean = false,
        val secondary: Boolean = false,
        val action: () -> Unit
    )

    private fun itemCard(bind: CardHolder.() -> Unit) {
        val card = MaterialCardView(activity).apply {
            radius = dp(18).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1)
            strokeColor = color(R.color.border)
            setCardBackgroundColor(color(R.color.tani_surface))
        }
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(15), dp(15), dp(15))
        }
        card.addView(content)
        CardHolder(content).bind()
        root.addView(card, matchWrap().apply { bottomMargin = dp(9) })
    }

    private inner class CardHolder(private val content: LinearLayout) {
        fun addStatus(code: String, value: String) {
            content.addView(TextView(activity).apply {
                text = value
                textSize = 12f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(color(statusColor(code)))
                setBackgroundColor(color(statusBackground(code)))
                setPadding(dp(10), dp(5), dp(10), dp(5))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.END
                bottomMargin = dp(7)
            })
        }

        fun addTitle(value: String) = content.addView(title(value, 17f))
        fun addMeta(value: String) = content.addView(meta(value).apply { setPadding(0, dp(4), 0, 0) })
        fun addNote(value: String) = content.addView(TextView(activity).apply {
            text = value
            textSize = 13f
            setTextColor(color(R.color.tani_secondary))
            setBackgroundColor(color(R.color.tani_background))
            setPadding(dp(10), dp(9), dp(10), dp(9))
        }, matchWrap().apply { topMargin = dp(9) })

        fun addActions(vararg actionsList: Action) {
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
            }
            actionsList.forEach { item ->
                row.addView(MaterialButton(activity).apply {
                    text = item.label
                    textSize = 13f
                    isAllCaps = false
                    minWidth = 0
                    minimumWidth = 0
                    setOnClickListener { item.action() }
                    if (item.danger) setTextColor(color(R.color.error))
                    if (item.secondary || item.danger) setBackgroundColor(android.graphics.Color.TRANSPARENT)
                })
            }
            content.addView(HorizontalScrollView(activity).apply {
                isHorizontalScrollBarEnabled = false
                addView(row)
            }, matchWrap().apply { topMargin = dp(8) })
        }
    }

    private fun title(value: String, size: Float) = TextView(activity).apply {
        text = value
        textSize = size
        setTextColor(color(R.color.text_dark))
        setTypeface(typeface, Typeface.BOLD)
    }

    private fun label(value: String) = TextView(activity).apply {
        text = value
        textSize = 13f
        setTextColor(color(R.color.text_muted))
    }

    private fun meta(value: String) = TextView(activity).apply {
        text = value
        textSize = 13f
        setTextColor(color(R.color.text_muted))
    }

    private fun statusColor(code: String): Int = when (code) {
        "active", "approved", "delivered", "resolved", "closed", "paid", "info" -> R.color.success
        "pending", "open", "normal", "warning", "changes_requested", "accepted", "preparing", "ready", "out_for_delivery" -> R.color.warning
        "inactive", "suspended", "rejected", "cancelled", "failed", "urgent", "high", "error", "critical" -> R.color.error
        else -> R.color.tani_primary_dark
    }

    private fun statusBackground(code: String): Int = when (statusColor(code)) {
        R.color.success -> R.color.success_soft
        R.color.error -> R.color.error_soft
        R.color.warning -> R.color.warning_soft
        else -> R.color.brand_soft
    }

    private fun requestStatus(value: String?): String = when (value) {
        "pending" -> activity.getString(R.string.dashboard_renderer_135)
        "approved", "actioned" -> activity.getString(R.string.dashboard_renderer_136)
        "rejected", "dismissed" -> activity.getString(R.string.dashboard_renderer_137)
        "cancelled" -> activity.getString(R.string.dashboard_renderer_138)
        "expired" -> activity.getString(R.string.dashboard_renderer_139)
        else -> value ?: activity.getString(R.string.dashboard_renderer_140)
    }

    private fun paymentStatus(value: String?): String = when (value) {
        "paid" -> activity.getString(R.string.dashboard_renderer_141)
        "pending" -> activity.getString(R.string.dashboard_renderer_142)
        "failed" -> activity.getString(R.string.dashboard_renderer_143)
        "refunded" -> activity.getString(R.string.dashboard_renderer_144)
        else -> value ?: "—"
    }

    private fun providerType(value: String?): String = when (value) {
        "merchant" -> activity.getString(R.string.dashboard_renderer_145)
        "partner" -> activity.getString(R.string.dashboard_renderer_146)
        "platform" -> activity.getString(R.string.dashboard_renderer_147)
        else -> value ?: "—"
    }

    private fun severityLabel(value: String?): String = when (value) {
        "critical" -> activity.getString(R.string.dashboard_renderer_148)
        "warning" -> activity.getString(R.string.dashboard_renderer_149)
        "info" -> activity.getString(R.string.dashboard_renderer_150)
        else -> activity.getString(R.string.dashboard_renderer_124)
    }

    private fun alertStatus(value: String?): String = when (value) {
        "open" -> activity.getString(R.string.dashboard_renderer_151)
        "acknowledged" -> activity.getString(R.string.dashboard_renderer_152)
        "resolved" -> activity.getString(R.string.dashboard_renderer_153)
        else -> value ?: "—"
    }

    private fun sourceLabel(value: String?): String = when (value) {
        "supabase_http" -> activity.getString(R.string.dashboard_renderer_154)
        "android" -> activity.getString(R.string.dashboard_renderer_155)
        "repository" -> activity.getString(R.string.dashboard_renderer_156)
        else -> value ?: activity.getString(R.string.dashboard_renderer_157)
    }

    private fun auditAction(value: String?): String = when (value) {
        "merchant_review" -> activity.getString(R.string.dashboard_renderer_158)
        "set_account_active" -> activity.getString(R.string.dashboard_renderer_159)
        "order_status_change" -> activity.getString(R.string.dashboard_renderer_160)
        "product_moderation" -> activity.getString(R.string.dashboard_renderer_161)
        "category_status_change" -> activity.getString(R.string.dashboard_renderer_162)
        "soft_delete" -> activity.getString(R.string.dashboard_renderer_163)
        else -> value?.replace('_', ' ') ?: activity.getString(R.string.dashboard_renderer_164)
    }

    private fun entityLabel(value: String?): String = when (value) {
        "merchant_profile" -> activity.getString(R.string.admin_management_dialog_23)
        "profile" -> activity.getString(R.string.dashboard_renderer_165)
        "order" -> activity.getString(R.string.dashboard_renderer_166)
        "product" -> activity.getString(R.string.dashboard_renderer_82)
        "category" -> activity.getString(R.string.dashboard_renderer_100)
        else -> value ?: activity.getString(R.string.dashboard_renderer_167)
    }

    private fun formatDate(value: String?): String = value?.replace('T', ' ')?.take(16) ?: "—"
    private fun JsonObject.double(key: String): Double = this[key]?.jsonPrimitive?.doubleOrNull ?: 0.0
    private fun Double.asCount(): String = toLong().toString()
    private fun color(id: Int): Int = ContextCompat.getColor(activity, id)
    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()
    private fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
}
