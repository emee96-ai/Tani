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
        addEmpty("جاري تحميل البيانات…")
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
                Metric("طلبات 7 أيام", orders.asCount(), section = AdminSection.ORDERS),
                Metric("المكتملة", completed.asCount(), AdminUiText.percent(completed, orders), AdminSection.ORDERS),
                Metric("قيمة المبيعات", AdminUiText.money(kpis.double("gmv")), "للطلبات المكتملة", AdminSection.REPORTS),
                Metric("الملغاة", cancelled.asCount(), AdminUiText.percent(cancelled, orders), AdminSection.ORDERS)
            )
        )

        addSectionTitle("تحتاج تدخلك الآن")
        val cards = listOf(
            Triple("طلبات تنتظر القبول", attention["pending_orders"] ?: 0, AdminSection.ORDERS),
            Triple("طلبات تجار للمراجعة", attention["pending_merchants"] ?: 0, AdminSection.MERCHANTS),
            Triple("شكاوى مفتوحة", attention["open_complaints"] ?: 0, AdminSection.SUPPORT),
            Triple("تنبيهات تشغيلية", attention["open_alerts"] ?: 0, AdminSection.SYSTEM)
        )
        cards.forEach { (label, count, section) ->
            actionCard(label, if (count == 0) "لا يوجد إجراء مطلوب" else "$count عنصر يحتاج مراجعة", count > 0) {
                actions.navigate(section)
            }
        }

        if (alerts.isNotEmpty()) {
            addSectionTitle("تنبيهات مهمة")
            alerts.take(3).forEach { element ->
                val row = element.jsonObject
                itemCard {
                    addStatus(row.string("severity").orEmpty(), severityLabel(row.string("severity")))
                    addTitle(row.string("title") ?: "تنبيه تشغيلي")
                    addMeta(formatDate(row.string("created_at")))
                    addActions(Action("فتح حالة النظام", secondary = true) { actions.navigate(AdminSection.SYSTEM) })
                }
            }
        }

        addSectionTitle("آخر الطلبات", "أحدث خمسة طلبات في المنصة")
        if (recentOrders.isEmpty()) addEmpty("لا توجد طلبات حتى الآن")
        recentOrders.forEach { element -> orderCard(element.jsonObject, compact = true) }
        textAction("عرض جميع الطلبات") { actions.navigate(AdminSection.ORDERS) }
    }

    fun renderOrders(rows: JsonArray, selected: String, page: Int, hasNext: Boolean) {
        clear(AdminSection.ORDERS)
        addFilters(
            listOf("all" to "الكل", "pending" to "جديدة", "active" to "قيد التنفيذ", "delivered" to "مكتملة", "cancelled" to "ملغاة"),
            selected
        )
        if (rows.isEmpty()) addEmpty("لا توجد طلبات مطابقة")
        rows.forEach { orderCard(it.jsonObject, compact = false) }
        addPagination(page, hasNext)
    }

    fun renderMerchants(rows: JsonArray, selected: String, page: Int, hasNext: Boolean) {
        clear(AdminSection.MERCHANTS)
        addFilters(
            listOf("all" to "الكل", "pending" to "للمراجعة", "approved" to "معتمدون", "changes_requested" to "تعديل مطلوب", "suspended" to "موقوفون", "rejected" to "مرفوضون"),
            selected
        )
        if (rows.isEmpty()) addEmpty("لا يوجد تجار مطابقون")
        rows.forEach { element ->
            val row = element.jsonObject
            itemCard {
                addStatus(row.string("verification_status").orEmpty(), AdminUiText.merchantStatus(row.string("verification_status")))
                addTitle(row.string("store_name") ?: row.string("business_name") ?: "تاجر")
                addMeta(row.string("business_name") ?: "—")
                addMeta("${row.string("phone") ?: "بلا رقم"} • ${row.string("city") ?: "—"}")
                row.string("requested_category")?.takeIf { it.isNotBlank() }?.let {
                    addNote("فئة جديدة مطلوبة: $it — تُراجع وتُفعّل من قسم الإعدادات")
                }
                (row["delivery_zones"] as? JsonArray)?.takeIf { it.isNotEmpty() }?.let { zones ->
                    val summary = zones.joinToString(" • ") { element ->
                        val zone = element.jsonObject
                        "${zone.string("area") ?: "—"}: ${AdminUiText.money(zone.double("fee"))}"
                    }
                    addMeta("التوصيل: $summary")
                }
                addMeta("أُرسل: ${formatDate(row.string("submitted_at") ?: row.string("created_at"))}")
                row.string("review_note")?.takeIf { it.isNotBlank() }?.let { addNote("ملاحظة: $it") }
                addActions(Action("فتح مستند الهوية") { actions.openMerchantDocument(row.string("id").orEmpty()) })
                if (role() == "admin") {
                    when (row.string("verification_status")) {
                        "pending", "changes_requested", "rejected" -> addActions(
                            Action("اعتماد") { actions.reviewMerchant(row, "approved") },
                            Action("طلب تعديل", secondary = true) { actions.reviewMerchant(row, "changes_requested") },
                            Action("رفض", danger = true) { actions.reviewMerchant(row, "rejected") }
                        )
                        "approved" -> addActions(Action("تعليق التاجر", danger = true) { actions.reviewMerchant(row, "suspended") })
                        "suspended" -> addActions(Action("إعادة الاعتماد") { actions.reviewMerchant(row, "approved") })
                    }
                }
            }
        }
        addPagination(page, hasNext)
    }

    fun renderSupport(mode: String, rows: JsonArray, selectedStatus: String) {
        clear(AdminSection.SUPPORT)
        addFilters(listOf("complaints" to "الشكاوى", "tickets" to "التذاكر", "reviews" to "بلاغات التقييم"), mode)
        addFilters(listOf("all" to "كل الحالات", "open" to "المفتوحة", "in_progress" to "قيد المعالجة", "closed" to "المغلقة"), selectedStatus, prefix = "status:")
        if (rows.isEmpty()) addEmpty("لا توجد عناصر مطابقة")
        when (mode) {
            "tickets" -> rows.forEach { renderTicket(it.jsonObject) }
            "reviews" -> rows.forEach { renderReviewReport(it.jsonObject) }
            else -> rows.forEach { renderComplaint(it.jsonObject) }
        }
    }

    fun renderReports(kpis: JsonObject, period: String) {
        clear(AdminSection.REPORTS)
        addFilters(listOf("7" to "7 أيام", "30" to "30 يوماً", "90" to "90 يوماً"), period)
        val orders = kpis.double("orders")
        val completed = kpis.double("completed_orders")
        val cancelled = kpis.double("cancelled_orders")
        renderMetrics(
            listOf(
                Metric("الطلبات", orders.asCount(), "خلال الفترة"),
                Metric("الطلبات المكتملة", completed.asCount(), AdminUiText.percent(completed, orders)),
                Metric("قيمة المبيعات GMV", AdminUiText.money(kpis.double("gmv"))),
                Metric("الإلغاء والفشل", cancelled.asCount(), AdminUiText.percent(cancelled, orders)),
                Metric("عملاء نشطون", kpis.double("active_customers").asCount()),
                Metric("تجار نشطون", kpis.double("active_merchants").asCount()),
                Metric("عملاء متكررون", kpis.double("repeat_customers").asCount()),
                Metric("مشاهدات المنتجات", kpis.double("product_views").asCount()),
                Metric("عمليات البحث", kpis.double("searches").asCount()),
                Metric("الشكاوى", kpis.double("complaints").asCount()),
                Metric("أخطاء التطبيق", kpis.double("app_errors").asCount())
            )
        )
        addInfo("تُحسب قيمة المبيعات من الطلبات المكتملة فقط، حتى لا تعطي الطلبات الملغاة رقماً مضللاً.")
    }

    fun renderCustomers(rows: JsonArray, selected: String, page: Int, hasNext: Boolean) {
        clear(AdminSection.CUSTOMERS)
        addFilters(listOf("all" to "الكل", "customer" to "العملاء", "seller" to "التجار", "inactive" to "الموقوفون"), selected)
        if (rows.isEmpty()) addEmpty("لا توجد حسابات مطابقة")
        rows.forEach { element ->
            val row = element.jsonObject
            itemCard {
                addStatus(if (row.bool("is_active")) "active" else "inactive", if (row.bool("is_active")) "نشط" else "موقوف")
                addTitle(row.string("name") ?: "مستخدم")
                addMeta("${AdminUiText.role(row.string("role"))} • ${row.string("phone") ?: "بلا رقم"}")
                addMeta("أنشئ: ${formatDate(row.string("created_at"))}")
                if (role() == "admin" && row.string("id") != com.tani.admin.data.AdminApi.userId) {
                    if (row.bool("is_active")) addActions(Action("إيقاف الحساب", danger = true) { actions.setAccountActive(row, false) })
                    else addActions(Action("إعادة تنشيط الحساب") { actions.setAccountActive(row, true) })
                }
            }
        }
        addPagination(page, hasNext)
    }

    fun renderProducts(rows: JsonArray, selected: String, page: Int, hasNext: Boolean) {
        clear(AdminSection.PRODUCTS)
        addFilters(listOf("all" to "الكل", "active" to "ظاهرة", "inactive" to "مخفية", "out_of_stock" to "نفد المخزون"), selected)
        if (rows.isEmpty()) addEmpty("لا توجد منتجات مطابقة")
        rows.forEach { element ->
            val row = element.jsonObject
            itemCard {
                val available = row.bool("is_active")
                addStatus(if (available) "active" else "inactive", if (available) "ظاهر" else "مخفي")
                addTitle(row.string("name") ?: "منتج")
                addMeta("السعر: ${AdminUiText.money(row.double("price"))} • المخزون: ${row.numberText("stock")}")
                addMeta("أضيف: ${formatDate(row.string("created_at"))}")
                if (role() == "admin") {
                    if (available) addActions(Action("إخفاء المنتج", danger = true) { actions.setProductActive(row, false) })
                    else addActions(Action("إعادة إظهار المنتج") { actions.setProductActive(row, true) })
                }
            }
        }
        addPagination(page, hasNext)
    }

    fun renderMonetization(mode: String, rows: JsonArray) {
        clear(AdminSection.MONETIZATION)
        addFilters(listOf("subscriptions" to "طلبات الاشتراك", "featured" to "الظهور الممول"), mode)
        if (rows.isEmpty()) addEmpty("لا توجد طلبات في هذا القسم")
        rows.forEach { element ->
            val row = element.jsonObject
            itemCard {
                addStatus(row.string("status").orEmpty(), requestStatus(row.string("status")))
                if (mode == "subscriptions") {
                    addTitle("طلب اشتراك")
                    addMeta("الخطة: ${row.string("plan_id")?.take(8) ?: "—"}")
                } else {
                    addTitle(row.string("placement") ?: "ظهور ممول")
                    addMeta(if (row.string("product_id") == null) "ترويج متجر" else "ترويج منتج")
                }
                addMeta(formatDate(row.string("created_at")))
                if (role() == "admin" && row.string("status") == "pending") {
                    val rpc = if (mode == "subscriptions") "admin_review_subscription_request" else "admin_review_featured_request"
                    addActions(
                        Action("اعتماد") { actions.reviewRequest(rpc, row.string("id").orEmpty(), "approved") },
                        Action("رفض", danger = true) { actions.reviewRequest(rpc, row.string("id").orEmpty(), "rejected") }
                    )
                }
            }
        }
    }

    fun renderConfiguration(mode: String, rows: JsonArray) {
        clear(AdminSection.CONFIGURATION)
        addFilters(listOf("categories" to "الفئات", "cities" to "المدن", "delivery" to "التوصيل"), mode)
        if (rows.isEmpty()) addEmpty("لا توجد إعدادات في هذا القسم")
        rows.forEach { element ->
            val row = element.jsonObject
            itemCard {
                val active = row.bool("is_active")
                addStatus(if (active) "active" else "inactive", if (active) "مفعّل" else "متوقف")
                when (mode) {
                    "cities" -> {
                        addTitle(row.string("name") ?: "مدينة")
                        addMeta("${row.string("state_name") ?: "—"} • ${row.string("code") ?: "—"}")
                    }
                    "delivery" -> {
                        addTitle(row.string("display_name") ?: "مزود توصيل")
                        addMeta(providerType(row.string("provider_type")))
                    }
                    else -> {
                        addTitle(row.string("name") ?: "فئة")
                        addMeta("الترتيب: ${row.numberText("sort_order")}")
                    }
                }
                if (role() == "admin") {
                    val label = if (active) "إيقاف" else "تفعيل"
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
        addFilters(listOf("health" to "الحالة", "alerts" to "التنبيهات", "errors" to "الأخطاء", "audit" to "سجل الإجراءات"), mode)
        if (mode == "health") {
            renderMetrics(
                listOf(
                    Metric("أخطاء آخر 24 ساعة", (stats["errors_24h"] ?: 0).toString()),
                    Metric("تنبيهات مفتوحة", (stats["open_alerts"] ?: 0).toString()),
                    Metric("طلبات معلقة", (stats["pending_orders"] ?: 0).toString()),
                    Metric("تذاكر مفتوحة", (stats["open_tickets"] ?: 0).toString())
                )
            )
            val healthy = stats.values.all { it == 0 }
            addInfo(if (healthy) "لا توجد مشكلات تشغيلية ظاهرة حالياً." else "راجعي التنبيهات والأخطاء الحديثة. السجلات القديمة لا تعني أن العطل ما زال قائماً.")
            return
        }
        if (rows.isEmpty()) addEmpty("لا توجد بيانات في هذا القسم")
        when (mode) {
            "errors" -> rows.forEach { element ->
                val row = element.jsonObject
                itemCard {
                    addStatus("error", "خطأ")
                    addTitle(AdminUiText.friendlyError("${row.string("error_type")} ${row.string("message")}"))
                    addMeta("${sourceLabel(row.string("source"))} • ${formatDate(row.string("created_at"))}")
                    addActions(Action("عرض التفاصيل التقنية", secondary = true) { actions.showTechnicalDetails(row) })
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
                    addTitle(row.string("title") ?: "تنبيه")
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
                    variant?.let { append(" • الخيار: $it") }
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
        addTitle(row.string("subject") ?: "شكوى")
        addMeta("${AdminUiText.supportStatus(row.string("status"))} • ${formatDate(row.string("created_at"))}")
        row.string("description")?.let { addNote(it) }
        if (row.string("status") in setOf("open", "in_progress")) addActions(
            Action("بدء المعالجة", secondary = true) { actions.updateComplaint(row.string("id").orEmpty(), "in_progress") },
            Action("تسجيل الحل") { actions.resolveComplaint(row.string("id").orEmpty()) }
        )
    }

    private fun renderTicket(row: JsonObject) = itemCard {
        addStatus(row.string("priority").orEmpty(), AdminUiText.priority(row.string("priority")))
        addTitle(row.string("subject") ?: "تذكرة دعم")
        addMeta("${AdminUiText.supportStatus(row.string("status"))} • ${formatDate(row.string("created_at"))}")
        row.string("description")?.let { addNote(it) }
        if (row.string("status") in setOf("open", "in_progress")) addActions(
            Action("بدء المعالجة", secondary = true) { actions.updateTicket(row.string("id").orEmpty(), "in_progress") },
            Action("إغلاق") { actions.updateTicket(row.string("id").orEmpty(), "closed") }
        )
    }

    private fun renderReviewReport(row: JsonObject) = itemCard {
        addStatus(row.string("status").orEmpty(), requestStatus(row.string("status")))
        addTitle("بلاغ عن تقييم #${row.string("review_id")?.take(8) ?: "—"}")
        addMeta("${row.string("reason") ?: "دون سبب"} • ${formatDate(row.string("created_at"))}")
        if (row.string("status") == "pending") addActions(
            Action("إخفاء التقييم", danger = true) { actions.moderateReview(row, true) },
            Action("رفض البلاغ", secondary = true) { actions.moderateReview(row, false) }
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
            text = "السابق"
            isEnabled = page > 0
            setOnClickListener { actions.previousPage() }
        })
        row.addView(meta("صفحة ${page + 1}").apply { setPadding(dp(16), 0, dp(16), 0) })
        row.addView(MaterialButton(activity).apply {
            text = "التالي"
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
        "pending" -> "بانتظار المراجعة"
        "approved", "actioned" -> "معتمد"
        "rejected", "dismissed" -> "مرفوض"
        "cancelled" -> "ملغي"
        "expired" -> "منتهي"
        else -> value ?: "غير معروف"
    }

    private fun paymentStatus(value: String?): String = when (value) {
        "paid" -> "مدفوع"
        "pending" -> "بانتظار الدفع"
        "failed" -> "فشل الدفع"
        "refunded" -> "مسترد"
        else -> value ?: "—"
    }

    private fun providerType(value: String?): String = when (value) {
        "merchant" -> "توصيل بواسطة التاجر"
        "partner" -> "شريك توصيل"
        "platform" -> "توصيل المنصة"
        else -> value ?: "—"
    }

    private fun severityLabel(value: String?): String = when (value) {
        "critical" -> "حرج"
        "warning" -> "تحذير"
        "info" -> "معلومة"
        else -> "تنبيه"
    }

    private fun alertStatus(value: String?): String = when (value) {
        "open" -> "مفتوح"
        "acknowledged" -> "تم الاطلاع"
        "resolved" -> "تم الحل"
        else -> value ?: "—"
    }

    private fun sourceLabel(value: String?): String = when (value) {
        "supabase_http" -> "اتصال قاعدة البيانات"
        "android" -> "تطبيق أندرويد"
        "repository" -> "طبقة البيانات"
        else -> value ?: "التطبيق"
    }

    private fun auditAction(value: String?): String = when (value) {
        "merchant_review" -> "مراجعة طلب تاجر"
        "set_account_active" -> "تغيير حالة حساب"
        "order_status_change" -> "تغيير حالة طلب"
        "product_moderation" -> "مراجعة منتج"
        "category_status_change" -> "تغيير حالة فئة"
        "soft_delete" -> "حذف حساب"
        else -> value?.replace('_', ' ') ?: "إجراء إداري"
    }

    private fun entityLabel(value: String?): String = when (value) {
        "merchant_profile" -> "تاجر"
        "profile" -> "حساب"
        "order" -> "طلب"
        "product" -> "منتج"
        "category" -> "فئة"
        else -> value ?: "عنصر"
    }

    private fun formatDate(value: String?): String = value?.replace('T', ' ')?.take(16) ?: "—"
    private fun JsonObject.double(key: String): Double = this[key]?.jsonPrimitive?.doubleOrNull ?: 0.0
    private fun Double.asCount(): String = toLong().toString()
    private fun color(id: Int): Int = ContextCompat.getColor(activity, id)
    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()
    private fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
}
