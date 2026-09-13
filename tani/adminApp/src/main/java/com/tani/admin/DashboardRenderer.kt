package com.tani.admin

import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.tani.admin.data.bool
import com.tani.admin.data.numberText
import com.tani.admin.data.string
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

class DashboardRenderer(
    private val activity: AppCompatActivity,
    private val role: () -> String?,
    private val actions: Actions
) {
    interface Actions {
        fun openMerchantDocument(merchantId: String)
        fun reviewMerchant(row: JsonObject, status: String)
        fun updateComplaint(id: String, status: String)
        fun resolveComplaint(id: String)
        fun updateTicket(id: String, status: String)
        fun reviewRequest(rpc: String, id: String, status: String)
        fun moderateReview(row: JsonObject, hide: Boolean)
    }

    fun renderMetrics(items: List<Pair<String, String>>) {
        val grid = activity.findViewById<GridLayout>(R.id.metricsGrid)
        grid.removeAllViews()
        items.forEach { (label, value) ->
            val box = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                background = ContextCompat.getDrawable(activity, R.drawable.bg_metric)
                setPadding(dp(14), dp(14), dp(14), dp(14))
                addView(text(label, false, 13f))
                addView(text(value, true, 24f))
            }
            grid.addView(box, GridLayout.LayoutParams().apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(4), dp(4), dp(4), dp(4))
            })
        }
    }

    fun renderMerchants(rows: JsonArray) = renderList(R.id.merchantsContainer, rows) { row, h ->
        h.addText(row.string("business_name") ?: row.string("store_name") ?: "تاجر", true)
        h.addText("${row.string("store_name") ?: "—"} • ${row.string("phone") ?: "—"}")
        h.addText("الحالة: ${merchantStatus(row.string("verification_status"))} • ${fmt(row.string("submitted_at") ?: row.string("created_at"))}")
        h.addButton("فتح مستند الهوية") { actions.openMerchantDocument(row.string("id").orEmpty()) }
        if (role() == "admin") {
            when (row.string("verification_status")) {
                "pending", "changes_requested", "rejected" -> {
                    h.addButton("قبول") { actions.reviewMerchant(row, "approved") }
                    h.addButton("طلب تعديل", secondary = true) { actions.reviewMerchant(row, "changes_requested") }
                    h.addButton("رفض", danger = true) { actions.reviewMerchant(row, "rejected") }
                }
                "approved" -> h.addButton("تعليق", danger = true) { actions.reviewMerchant(row, "suspended") }
            }
        }
    }

    fun renderComplaints(rows: JsonArray) = renderList(R.id.complaintsContainer, rows) { row, h ->
        h.addText(row.string("subject") ?: "شكوى", true)
        h.addText("${row.string("status") ?: "—"} • ${row.string("priority") ?: "—"} • ${fmt(row.string("created_at"))}")
        h.addButton("قيد المعالجة", secondary = true) { actions.updateComplaint(row.string("id").orEmpty(), "in_progress") }
        h.addButton("حل") { actions.resolveComplaint(row.string("id").orEmpty()) }
    }

    fun renderTickets(rows: JsonArray) = renderList(R.id.ticketsContainer, rows) { row, h ->
        h.addText(row.string("subject") ?: "تذكرة", true)
        h.addText("${row.string("status") ?: "—"} • ${row.string("priority") ?: "—"} • ${fmt(row.string("created_at"))}")
        h.addButton("بدء المعالجة", secondary = true) { actions.updateTicket(row.string("id").orEmpty(), "in_progress") }
        h.addButton("إغلاق") { actions.updateTicket(row.string("id").orEmpty(), "closed") }
    }

    fun renderSubscriptions(rows: JsonArray) = renderList(R.id.subscriptionsContainer, rows) { row, h ->
        h.addText("طلب اشتراك ${row.string("plan_id")?.take(8) ?: "—"}", true)
        h.addText("${row.string("status") ?: "—"} • ${fmt(row.string("created_at"))}")
        if (role() == "admin" && row.string("status") == "pending") {
            h.addButton("قبول") { actions.reviewRequest("admin_review_subscription_request", row.string("id").orEmpty(), "approved") }
            h.addButton("رفض", danger = true) { actions.reviewRequest("admin_review_subscription_request", row.string("id").orEmpty(), "rejected") }
        }
    }

    fun renderFeatured(rows: JsonArray) = renderList(R.id.featuredContainer, rows) { row, h ->
        h.addText("${row.string("placement") ?: "ظهور ممول"} • ${if (row.string("product_id") != null) "منتج" else "متجر"}", true)
        h.addText("${row.string("status") ?: "—"} • ${fmt(row.string("created_at"))}")
        if (role() == "admin" && row.string("status") == "pending") {
            h.addButton("قبول") { actions.reviewRequest("admin_review_featured_request", row.string("id").orEmpty(), "approved") }
            h.addButton("رفض", danger = true) { actions.reviewRequest("admin_review_featured_request", row.string("id").orEmpty(), "rejected") }
        }
    }

    fun renderReviewReports(rows: JsonArray) = renderList(R.id.reviewReportsContainer, rows) { row, h ->
        h.addText("بلاغ تقييم ${row.string("review_id")?.take(8) ?: "—"}", true)
        h.addText("${row.string("reason") ?: "—"} • ${row.string("status") ?: "—"} • ${fmt(row.string("created_at"))}")
        if (row.string("status") == "pending") {
            h.addButton("إخفاء التقييم", danger = true) { actions.moderateReview(row, true) }
            h.addButton("رفض البلاغ", secondary = true) { actions.moderateReview(row, false) }
        }
    }

    fun renderTrust(rows: JsonArray) = renderList(R.id.trustContainer, rows) { row, h ->
        h.addText("Score ${row.numberText("trust_score")} — ${row.string("trust_level") ?: "—"}", true)
        h.addText("تقييم ${row.numberText("average_rating")} • إكمال ${row.numberText("completion_rate")}% • شكاوى ${row.numberText("open_complaints")}")
    }

    fun renderCities(rows: JsonArray) = renderList(R.id.citiesContainer, rows) { row, h ->
        h.addText("${row.string("name") ?: "مدينة"}${if (row.bool("is_active")) " ✓" else ""}", true)
        h.addText("${row.string("code") ?: "—"} • ${row.string("state_name") ?: "—"}")
    }

    fun renderDelivery(rows: JsonArray) = renderList(R.id.deliveryContainer, rows) { row, h ->
        h.addText("${row.string("display_name") ?: "مزود"}${if (row.bool("is_active")) " ✓" else ""}", true)
        h.addText(row.string("provider_type") ?: "—")
    }

    fun renderErrors(rows: JsonArray) = renderList(R.id.errorsContainer, rows) { row, h ->
        h.addText("${row.string("error_type") ?: "Error"} — ${row.string("source") ?: "—"}", true)
        h.addText("${row.string("message")?.take(180) ?: "—"} • ${fmt(row.string("created_at"))}")
    }

    fun renderAudit(rows: JsonArray) = renderList(R.id.auditContainer, rows) { row, h ->
        h.addText("${row.string("action") ?: "—"} — ${row.string("entity_type") ?: "—"}", true)
        h.addText("${row.string("entity_id") ?: "—"} • ${row.string("source") ?: "—"} • ${fmt(row.string("created_at"))}")
    }

    private fun renderList(id: Int, rows: JsonArray, bind: (JsonObject, ItemHolder) -> Unit) {
        val container = activity.findViewById<LinearLayout>(id)
        container.removeAllViews()
        if (rows.isEmpty()) {
            container.addView(text("لا توجد بيانات", false, 13f).apply { setPadding(dp(8), dp(12), dp(8), dp(12)) })
            return
        }
        rows.forEach { element ->
            val card = MaterialCardView(activity).apply {
                radius = dp(16).toFloat()
                cardElevation = dp(1).toFloat()
                strokeWidth = dp(1)
                strokeColor = ContextCompat.getColor(activity, R.color.border)
                setCardBackgroundColor(ContextCompat.getColor(activity, R.color.tani_surface))
            }
            val content = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(14), dp(14), dp(14))
            }
            card.addView(content)
            bind(element.jsonObject, ItemHolder(content))
            container.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(8)
            })
        }
    }

    inner class ItemHolder(private val root: LinearLayout) {
        fun addText(value: String, bold: Boolean = false) {
            root.addView(text(value, bold, if (bold) 16f else 13f).apply {
                if (!bold) setPadding(0, dp(4), 0, 0)
            })
        }

        fun addButton(label: String, danger: Boolean = false, secondary: Boolean = false, action: () -> Unit) {
            root.addView(MaterialButton(activity).apply {
                text = label
                isAllCaps = false
                setOnClickListener { action() }
                if (danger) setTextColor(ContextCompat.getColor(activity, R.color.error))
                if (secondary || danger) setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.END
                topMargin = dp(6)
            })
        }
    }

    private fun text(value: String, bold: Boolean, size: Float) = TextView(activity).apply {
        text = value
        textSize = size
        setTextColor(ContextCompat.getColor(activity, if (bold) R.color.text_dark else R.color.text_muted))
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun merchantStatus(value: String?): String = when (value) {
        "pending" -> "قيد المراجعة"
        "changes_requested" -> "تعديلات مطلوبة"
        "approved" -> "مقبول"
        "rejected" -> "مرفوض"
        "suspended" -> "موقوف"
        else -> value ?: "—"
    }

    private fun fmt(value: String?): String = value?.replace('T', ' ')?.take(16) ?: "—"
    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()
}
