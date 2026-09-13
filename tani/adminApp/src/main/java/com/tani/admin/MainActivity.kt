package com.tani.admin

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.tani.admin.data.AdminApi
import com.tani.admin.data.string
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.net.URLConnection
import java.time.Instant

class MainActivity : AppCompatActivity(), DashboardRenderer.Actions {
    private var role: String? = null
    private lateinit var renderer: DashboardRenderer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AdminApi.init(this)
        if (AdminApi.hasSession()) restoreSession() else showLogin()
    }

    private fun restoreSession() {
        showLogin(loading = true)
        lifecycleScope.launch {
            runCatching { AdminApi.getMyRole() }
                .onSuccess { enterDashboard(it) }
                .onFailure {
                    AdminApi.clearSession()
                    showLogin(error = it.message ?: "انتهت جلسة الدخول")
                }
        }
    }

    private fun showLogin(loading: Boolean = false, error: String? = null) {
        setContentView(R.layout.activity_login)
        val email = findViewById<TextInputEditText>(R.id.emailInput)
        val password = findViewById<TextInputEditText>(R.id.passwordInput)
        val button = findViewById<MaterialButton>(R.id.loginButton)
        val progress = findViewById<ProgressBar>(R.id.loginProgress)
        val status = findViewById<TextView>(R.id.loginStatus)
        status.text = error.orEmpty()
        progress.visibility = if (loading) View.VISIBLE else View.GONE
        button.isEnabled = !loading

        button.setOnClickListener {
            val mail = email.text?.toString()?.trim().orEmpty()
            val pass = password.text?.toString().orEmpty()
            if (mail.isBlank() || pass.isBlank()) {
                status.text = "أدخلي البريد وكلمة المرور"
                return@setOnClickListener
            }
            status.text = ""
            progress.visibility = View.VISIBLE
            button.isEnabled = false
            lifecycleScope.launch {
                runCatching {
                    AdminApi.signIn(mail, pass)
                    AdminApi.getMyRole()
                }.onSuccess { enterDashboard(it) }
                    .onFailure {
                        AdminApi.clearSession()
                        status.text = it.message ?: "تعذر تسجيل الدخول"
                        progress.visibility = View.GONE
                        button.isEnabled = true
                    }
            }
        }
    }

    private fun enterDashboard(newRole: String) {
        role = newRole
        setContentView(R.layout.activity_dashboard)
        renderer = DashboardRenderer(this, { role }, this)
        findViewById<TextView>(R.id.roleBadge).text = if (role == "admin") "Admin" else "Support"
        findViewById<MaterialButton>(R.id.refreshButton).setOnClickListener { loadDashboard() }
        findViewById<MaterialButton>(R.id.logoutButton).setOnClickListener {
            lifecycleScope.launch {
                AdminApi.signOut()
                role = null
                showLogin()
            }
        }
        loadDashboard()
    }

    private fun loadDashboard() {
        val progress = findViewById<ProgressBar>(R.id.dashboardProgress)
        val status = findViewById<TextView>(R.id.dashboardStatus)
        progress.visibility = View.VISIBLE
        status.text = ""

        lifecycleScope.launch {
            runCatching {
                coroutineScope {
                    val metrics = async { loadMetrics() }
                    val merchants = async { safeRows("merchant_profiles", "select=id,business_name,store_name,phone,phone_verified_at,verification_status,review_note,submitted_at,created_at&order=created_at.desc&limit=50") }
                    val complaints = async { safeRows("complaints", "select=id,subject,status,priority,created_at&order=created_at.desc&limit=20") }
                    val tickets = async { safeRows("support_tickets", "select=id,subject,status,priority,user_id,created_at&order=created_at.desc&limit=30") }
                    val subscriptions = async { safeRows("subscription_requests", "select=*&order=created_at.desc&limit=30") }
                    val featured = async { safeRows("featured_requests", "select=*&order=created_at.desc&limit=30") }
                    val reports = async { safeRows("review_reports", "select=*&order=created_at.desc&limit=30") }
                    val trust = async { safeRows("merchant_trust_scores", "select=*&order=trust_score.desc&limit=30") }
                    val cities = async { safeRows("market_cities", "select=*&order=sort_order.asc,name.asc&limit=50") }
                    val delivery = async { safeRows("delivery_providers", "select=*&order=display_name.asc&limit=50") }
                    val errors = async { safeRows("app_errors", "select=source,error_type,message,created_at&order=created_at.desc&limit=20") }
                    val audit = async { safeRows("audit_logs", "select=action,entity_type,entity_id,source,created_at&order=created_at.desc&limit=30") }

                    renderer.renderMetrics(metrics.await())
                    renderer.renderMerchants(merchants.await())
                    renderer.renderComplaints(complaints.await())
                    renderer.renderTickets(tickets.await())
                    renderer.renderSubscriptions(subscriptions.await())
                    renderer.renderFeatured(featured.await())
                    renderer.renderReviewReports(reports.await())
                    renderer.renderTrust(trust.await())
                    renderer.renderCities(cities.await())
                    renderer.renderDelivery(delivery.await())
                    renderer.renderErrors(errors.await())
                    renderer.renderAudit(audit.await())
                }
            }.onFailure { status.text = it.message ?: "تعذر تحميل لوحة الإدارة" }
            progress.visibility = View.GONE
        }
    }

    private suspend fun safeRows(table: String, query: String): JsonArray =
        runCatching { AdminApi.rows(table, query) }.getOrDefault(JsonArray(emptyList()))

    private suspend fun loadMetrics(): List<Pair<String, String>> = coroutineScope {
        listOf(
            async { "الطلبات" to AdminApi.exactCount("orders").toString() },
            async { "المكتملة" to AdminApi.exactCount("orders", "status=eq.delivered").toString() },
            async { "الملغاة" to AdminApi.exactCount("orders", "status=eq.cancelled").toString() },
            async { "تجار معلّقون" to AdminApi.exactCount("merchant_profiles", "verification_status=eq.pending").toString() },
            async { "شكاوى مفتوحة" to AdminApi.exactCount("complaints", "status=neq.closed").toString() },
            async { "أخطاء" to AdminApi.exactCount("app_errors").toString() },
            async { "الحسابات" to AdminApi.exactCount("profiles").toString() }
        ).awaitAll()
    }

    override fun reviewMerchant(row: JsonObject, status: String) {
        val id = row.string("id") ?: return
        if (status == "approved") {
            mutate("تم تحديث حالة التاجر") {
                AdminApi.rpc("admin_review_merchant_application", buildJsonObject {
                    put("p_merchant_id", id); put("p_status", status); put("p_note", kotlinx.serialization.json.JsonNull)
                })
            }
        } else {
            val title = when (status) {
                "changes_requested" -> "ما التعديلات المطلوبة؟"
                "rejected" -> "سبب الرفض"
                else -> "سبب التعليق"
            }
            prompt(title) { note ->
                mutate("تم تحديث حالة التاجر") {
                    AdminApi.rpc("admin_review_merchant_application", buildJsonObject {
                        put("p_merchant_id", id); put("p_status", status); put("p_note", note)
                    })
                }
            }
        }
    }

    override fun updateComplaint(id: String, status: String) = mutate("تم تحديث الشكوى") {
        AdminApi.patchRow("complaints", id, buildJsonObject {
            put("status", status); put("updated_at", Instant.now().toString())
        })
    }

    override fun resolveComplaint(id: String) = prompt("اكتبي قرار/حل الشكوى") { resolution ->
        mutate("تم حل الشكوى") {
            AdminApi.patchRow("complaints", id, buildJsonObject {
                put("status", "resolved"); put("resolution", resolution)
                put("resolved_at", Instant.now().toString()); put("resolved_by", AdminApi.userId.orEmpty())
                put("updated_at", Instant.now().toString())
            })
        }
    }

    override fun updateTicket(id: String, status: String) = mutate("تم تحديث التذكرة") {
        AdminApi.patchRow("support_tickets", id, buildJsonObject {
            put("status", status); put("assigned_to", AdminApi.userId.orEmpty()); put("updated_at", Instant.now().toString())
        })
    }

    override fun reviewRequest(rpc: String, id: String, status: String) = mutate("تم تحديث الطلب") {
        AdminApi.rpc(rpc, buildJsonObject {
            put("p_request_id", id); put("p_status", status); put("p_note", kotlinx.serialization.json.JsonNull)
        })
    }

    override fun moderateReview(row: JsonObject, hide: Boolean) = mutate("تمت معالجة البلاغ") {
        val reportId = row.string("id") ?: return@mutate
        if (hide) {
            val reviewId = row.string("review_id") ?: return@mutate
            AdminApi.patchRow("reviews", reviewId, buildJsonObject {
                put("status", "hidden"); put("moderation_reason", row.string("reason") ?: "بلاغ إداري")
            })
            AdminApi.patchRow("review_reports", reportId, buildJsonObject { put("status", "actioned") })
        } else {
            AdminApi.patchRow("review_reports", reportId, buildJsonObject { put("status", "dismissed") })
        }
    }

    override fun openMerchantDocument(merchantId: String) {
        if (merchantId.isBlank()) return
        val status = findViewById<TextView>(R.id.dashboardStatus)
        lifecycleScope.launch {
            runCatching {
                val docs = AdminApi.rows("merchant_identity_documents", "select=storage_path,document_type,created_at&merchant_id=eq.${java.net.URLEncoder.encode(merchantId, "UTF-8")}&order=created_at.desc&limit=1")
                val path = docs.firstOrNull()?.jsonObject?.string("storage_path") ?: error("لا يوجد مستند هوية")
                val bytes = AdminApi.downloadPrivateObject(path)
                val ext = path.substringAfterLast('.', "bin").take(8)
                val file = File(File(cacheDir, "admin_docs").apply { mkdirs() }, "merchant_${merchantId.take(8)}.$ext")
                file.writeBytes(bytes)
                val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.files", file)
                startActivity(Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, URLConnection.guessContentTypeFromName(file.name) ?: "*/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                })
            }.onFailure { status.text = it.message ?: "تعذر فتح مستند الهوية" }
        }
    }

    private fun mutate(success: String, block: suspend () -> Unit) {
        val progress = findViewById<ProgressBar>(R.id.dashboardProgress)
        val status = findViewById<TextView>(R.id.dashboardStatus)
        progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            runCatching { block() }
                .onSuccess {
                    Toast.makeText(this@MainActivity, success, Toast.LENGTH_SHORT).show()
                    loadDashboard()
                }
                .onFailure { status.text = it.message ?: "تعذر تنفيذ العملية" }
            progress.visibility = View.GONE
        }
    }

    private fun prompt(title: String, submit: (String) -> Unit) {
        val input = EditText(this).apply { minLines = 2; setPadding(dp(16), dp(12), dp(16), dp(12)) }
        AlertDialog.Builder(this)
            .setTitle(title).setView(input).setNegativeButton("إلغاء", null)
            .setPositiveButton("حفظ") { _, _ -> input.text.toString().trim().takeIf { it.isNotBlank() }?.let(submit) }
            .show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
