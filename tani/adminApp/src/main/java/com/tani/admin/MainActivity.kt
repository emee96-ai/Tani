package com.tani.admin

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.FileProvider
import androidx.core.view.GravityCompat
import androidx.core.widget.NestedScrollView
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.navigation.NavigationView
import com.google.android.material.textfield.TextInputEditText
import com.tani.admin.data.AdminApi
import com.tani.admin.data.string
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.net.URLConnection
import java.time.Instant
import java.time.temporal.ChronoUnit

class MainActivity : AppCompatActivity(), DashboardRenderer.Actions {
    private var role: String? = null
    private lateinit var renderer: DashboardRenderer
    private lateinit var drawer: DrawerLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var navigationView: NavigationView
    private var currentSection = AdminSection.DASHBOARD
    private var page = 0
    private var searchQuery = ""
    private var supportStatus = "all"
    private var loadJob: Job? = null

    private val filters = mutableMapOf(
        AdminSection.ORDERS to "all",
        AdminSection.MERCHANTS to "pending",
        AdminSection.SUPPORT to "complaints",
        AdminSection.REPORTS to "7",
        AdminSection.CUSTOMERS to "all",
        AdminSection.PRODUCTS to "all",
        AdminSection.MONETIZATION to "subscriptions",
        AdminSection.CONFIGURATION to "categories",
        AdminSection.SYSTEM to "health"
    )

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
        loadJob?.cancel()
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
        drawer = findViewById(R.id.adminDrawerLayout)
        toolbar = findViewById(R.id.adminToolbar)
        bottomNavigation = findViewById(R.id.adminBottomNavigation)
        navigationView = findViewById(R.id.adminNavigationView)
        renderer = DashboardRenderer(this, { role }, this)

        toolbar.title = "تاني — الإدارة"
        toolbar.subtitle = if (newRole == "admin") "مدير النظام" else "فريق الدعم"
        toolbar.setNavigationOnClickListener { drawer.openDrawer(GravityCompat.START) }
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_refresh -> { loadSection(); true }
                else -> false
            }
        }
        setupSearch()
        setupNavigation()

        val header = navigationView.getHeaderView(0)
        header.findViewById<TextView>(R.id.drawerRoleBadge).text =
            if (newRole == "admin") "مدير النظام — صلاحيات كاملة" else "موظف دعم — صلاحيات محدودة"

        onBackPressedDispatcher.addCallback(this) {
            when {
                drawer.isDrawerOpen(GravityCompat.START) -> drawer.closeDrawer(GravityCompat.START)
                currentSection != AdminSection.DASHBOARD -> showSection(AdminSection.DASHBOARD)
                else -> finish()
            }
        }
        showSection(AdminSection.DASHBOARD, force = true)
    }

    private fun setupNavigation() {
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> showSection(AdminSection.DASHBOARD)
                R.id.nav_orders -> showSection(AdminSection.ORDERS)
                R.id.nav_merchants -> showSection(AdminSection.MERCHANTS)
                R.id.nav_support -> showSection(AdminSection.SUPPORT)
                R.id.nav_more -> {
                    drawer.openDrawer(GravityCompat.START)
                    return@setOnItemSelectedListener false
                }
                else -> return@setOnItemSelectedListener false
            }
            true
        }
        navigationView.setNavigationItemSelectedListener { item ->
            val section = when (item.itemId) {
                R.id.drawer_dashboard -> AdminSection.DASHBOARD
                R.id.drawer_reports -> AdminSection.REPORTS
                R.id.drawer_customers -> AdminSection.CUSTOMERS
                R.id.drawer_products -> AdminSection.PRODUCTS
                R.id.drawer_monetization -> AdminSection.MONETIZATION
                R.id.drawer_configuration -> AdminSection.CONFIGURATION
                R.id.drawer_system -> AdminSection.SYSTEM
                R.id.drawer_logout -> {
                    confirm("تسجيل الخروج", "هل تريدين إنهاء جلسة الإدارة؟", "خروج", danger = true) { signOut() }
                    drawer.closeDrawer(GravityCompat.START)
                    return@setNavigationItemSelectedListener true
                }
                else -> null
            }
            section?.let { showSection(it) }
            drawer.closeDrawer(GravityCompat.START)
            section != null
        }
    }

    private fun setupSearch() {
        val searchItem = toolbar.menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        searchView.queryHint = "بحث في القسم"
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                searchQuery = query?.trim().orEmpty()
                page = 0
                loadSection()
                searchView.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                if (newText.isNullOrEmpty() && searchQuery.isNotEmpty()) {
                    searchQuery = ""
                    page = 0
                    loadSection()
                }
                return true
            }
        })
    }

    override fun navigate(section: AdminSection) = showSection(section)

    private fun showSection(section: AdminSection, force: Boolean = false) {
        if (!force && currentSection == section) return
        currentSection = section
        page = 0
        searchQuery = ""
        toolbar.menu.findItem(R.id.action_search).apply {
            isVisible = section.searchable
            collapseActionView()
        }
        syncNavigation(section)
        loadSection()
    }

    private fun syncNavigation(section: AdminSection) {
        val bottomId = when (section) {
            AdminSection.DASHBOARD -> R.id.nav_dashboard
            AdminSection.ORDERS -> R.id.nav_orders
            AdminSection.MERCHANTS -> R.id.nav_merchants
            AdminSection.SUPPORT -> R.id.nav_support
            else -> null
        }
        bottomId?.let { bottomNavigation.menu.findItem(it).isChecked = true }
        navigationView.menu.findItem(
            when (section) {
                AdminSection.DASHBOARD -> R.id.drawer_dashboard
                AdminSection.REPORTS -> R.id.drawer_reports
                AdminSection.CUSTOMERS -> R.id.drawer_customers
                AdminSection.PRODUCTS -> R.id.drawer_products
                AdminSection.MONETIZATION -> R.id.drawer_monetization
                AdminSection.CONFIGURATION -> R.id.drawer_configuration
                AdminSection.SYSTEM -> R.id.drawer_system
                else -> R.id.drawer_dashboard
            }
        )?.isChecked = section !in setOf(AdminSection.ORDERS, AdminSection.MERCHANTS, AdminSection.SUPPORT)
    }

    private fun loadSection() {
        loadJob?.cancel()
        renderer.loading(currentSection)
        findViewById<NestedScrollView>(R.id.adminScreenScroll).scrollTo(0, 0)
        setLoading(true)
        clearStatus()
        val requested = currentSection
        loadJob = lifecycleScope.launch {
            runCatching {
                when (requested) {
                    AdminSection.DASHBOARD -> loadDashboard()
                    AdminSection.ORDERS -> loadOrders()
                    AdminSection.MERCHANTS -> loadMerchants()
                    AdminSection.SUPPORT -> loadSupport()
                    AdminSection.REPORTS -> loadReports()
                    AdminSection.CUSTOMERS -> loadCustomers()
                    AdminSection.PRODUCTS -> loadProducts()
                    AdminSection.MONETIZATION -> loadMonetization()
                    AdminSection.CONFIGURATION -> loadConfiguration()
                    AdminSection.SYSTEM -> loadSystem()
                }
            }.onFailure { error ->
                if (requested == currentSection) showStatus(error.message ?: "تعذر تحميل هذا القسم")
            }
            if (requested == currentSection) setLoading(false)
        }
    }

    private suspend fun loadDashboard() = coroutineScope {
        val kpis = async { rpcObject("weekly_marketplace_kpis") }
        val pendingOrders = async { safeCount("orders", "status=eq.pending") }
        val pendingMerchants = async { safeCount("merchant_profiles", "verification_status=eq.pending") }
        val complaints = async { safeCount("complaints", "status=in.(open,in_progress)") }
        val openAlerts = async { safeCount("operational_alerts", "status=neq.resolved") }
        val orders = async { safeRows("orders", "select=id,status,total,payment_method,payment_status,customer_name_snapshot,store_name_snapshot,created_at&order=created_at.desc&limit=5") }
        val alerts = async { safeRows("operational_alerts", "select=id,severity,title,status,created_at&status=neq.resolved&order=created_at.desc&limit=3") }
        renderer.renderDashboard(
            kpis.await(),
            mapOf(
                "pending_orders" to pendingOrders.await(),
                "pending_merchants" to pendingMerchants.await(),
                "open_complaints" to complaints.await(),
                "open_alerts" to openAlerts.await()
            ),
            orders.await(),
            alerts.await()
        )
    }

    private suspend fun loadOrders() {
        val selected = filters[AdminSection.ORDERS] ?: "all"
        val status = when (selected) {
            "active" -> "status=in.(accepted,preparing,ready,out_for_delivery)"
            "cancelled" -> "status=in.(cancelled,rejected,failed)"
            "all" -> ""
            else -> "status=eq.${AdminApi.enc(selected)}"
        }
        val rows = AdminApi.rows(
            "orders",
            query("select=id,status,total,subtotal,delivery_fee,payment_method,payment_status,phone,customer_name_snapshot,store_name_snapshot,created_at,updated_at,order_items(product_name_snapshot,variant_snapshot,quantity,unit_price,line_total)", status)
        ).matching("id", "phone", "customer_name_snapshot", "store_name_snapshot")
        renderer.renderOrders(rows.pageItems(), selected, page, rows.size > PAGE_SIZE)
    }

    private suspend fun loadMerchants() {
        val selected = filters[AdminSection.MERCHANTS] ?: "pending"
        val status = if (selected == "all") "" else "verification_status=eq.${AdminApi.enc(selected)}"
        val rows = AdminApi.rows(
            "merchant_profiles",
            query("select=id,user_id,seller_id,business_name,store_name,phone,city,category_id,requested_category,delivery_zones,verification_status,review_note,submitted_at,created_at", status)
        ).matching("business_name", "store_name", "phone", "city", "requested_category")
        renderer.renderMerchants(rows.pageItems(), selected, page, rows.size > PAGE_SIZE)
    }

    private suspend fun loadSupport() {
        val mode = filters[AdminSection.SUPPORT] ?: "complaints"
        val table: String
        val select: String
        val filter = when {
            supportStatus == "all" -> ""
            mode == "reviews" && supportStatus == "open" -> "status=eq.pending"
            mode == "reviews" && supportStatus == "closed" -> "status=in.(dismissed,actioned)"
            supportStatus == "closed" -> "status=in.(resolved,closed)"
            else -> "status=eq.${AdminApi.enc(supportStatus)}"
        }
        when (mode) {
            "tickets" -> {
                table = "support_tickets"
                select = "select=id,user_id,subject,description,status,priority,assigned_to,created_at"
            }
            "reviews" -> {
                table = "review_reports"
                select = "select=id,review_id,reason,status,created_at"
            }
            else -> {
                table = "complaints"
                select = "select=id,user_id,order_id,seller_id,subject,description,status,priority,category,resolution,created_at"
            }
        }
        val rows = AdminApi.rows(table, query(select, filter, limit = 50))
        renderer.renderSupport(mode, rows, supportStatus)
    }

    private suspend fun loadReports() {
        val period = filters[AdminSection.REPORTS] ?: "7"
        val days = period.toLongOrNull() ?: 7L
        val end = Instant.now()
        val start = end.minus(days, ChronoUnit.DAYS)
        val kpis = rpcObject("weekly_marketplace_kpis", buildJsonObject {
            put("p_start", start.toString())
            put("p_end", end.toString())
        })
        renderer.renderReports(kpis, period)
    }

    private suspend fun loadCustomers() {
        val selected = filters[AdminSection.CUSTOMERS] ?: "all"
        val filter = when (selected) {
            "inactive" -> "is_active=eq.false"
            "all" -> ""
            else -> "role=eq.${AdminApi.enc(selected)}"
        }
        val rows = AdminApi.rows(
            "profiles",
            query("select=id,name,phone,role,is_active,created_at,updated_at", filter)
        ).matching("name", "phone", "role", "id")
        renderer.renderCustomers(rows.pageItems(), selected, page, rows.size > PAGE_SIZE)
    }

    private suspend fun loadProducts() {
        val selected = filters[AdminSection.PRODUCTS] ?: "all"
        val filter = when (selected) {
            "active" -> "is_active=eq.true"
            "inactive" -> "is_active=eq.false"
            "out_of_stock" -> "stock=eq.0"
            else -> ""
        }
        val rows = AdminApi.rows(
            "products",
            query("select=id,seller_id,category_id,name,price,stock,is_active,created_at,updated_at", filter)
        ).matching("name", "id")
        renderer.renderProducts(rows.pageItems(), selected, page, rows.size > PAGE_SIZE)
    }

    private suspend fun loadMonetization() {
        val mode = filters[AdminSection.MONETIZATION] ?: "subscriptions"
        val table = if (mode == "subscriptions") "subscription_requests" else "featured_requests"
        val rows = AdminApi.rows(table, "select=*&order=created_at.desc&limit=50")
        renderer.renderMonetization(mode, rows)
    }

    private suspend fun loadConfiguration() {
        val mode = filters[AdminSection.CONFIGURATION] ?: "categories"
        val (table, query) = when (mode) {
            "cities" -> "market_cities" to "select=id,code,name,state_name,is_active,sort_order,updated_at&order=sort_order.asc,name.asc&limit=100"
            "delivery" -> "delivery_providers" to "select=code,display_name,provider_type,is_active,supported_cities,updated_at&order=display_name.asc&limit=100"
            else -> "categories" to "select=id,name,slug,sort_order,is_active,updated_at&order=sort_order.asc,name.asc&limit=100"
        }
        renderer.renderConfiguration(mode, AdminApi.rows(table, query))
    }

    private suspend fun loadSystem() {
        val mode = filters[AdminSection.SYSTEM] ?: "health"
        if (mode == "health") {
            val since = AdminApi.enc(Instant.now().minus(24, ChronoUnit.HOURS).toString())
            val stats = coroutineScope {
                listOf(
                    async { "errors_24h" to safeCount("app_errors", "created_at=gte.$since") },
                    async { "open_alerts" to safeCount("operational_alerts", "status=neq.resolved") },
                    async { "pending_orders" to safeCount("orders", "status=eq.pending") },
                    async { "open_tickets" to safeCount("support_tickets", "status=in.(open,in_progress)") }
                ).awaitAll().toMap()
            }
            renderer.renderSystem(mode, JsonArray(emptyList()), stats)
            return
        }
        val (table, query) = when (mode) {
            "errors" -> "app_errors" to "select=id,source,error_type,message,app_version,platform,created_at&order=created_at.desc&limit=50"
            "audit" -> "audit_logs" to "select=id,action,entity_type,entity_id,source,created_at&order=created_at.desc&limit=50"
            else -> "operational_alerts" to "select=id,type,severity,title,status,entity_type,entity_id,created_at&order=created_at.desc&limit=50"
        }
        renderer.renderSystem(mode, AdminApi.rows(table, query))
    }

    override fun selectFilter(value: String) {
        if (currentSection == AdminSection.SUPPORT && value.startsWith("status:")) {
            supportStatus = value.substringAfter(':')
        } else {
            filters[currentSection] = value
        }
        page = 0
        loadSection()
    }

    override fun nextPage() { page += 1; loadSection() }
    override fun previousPage() { if (page > 0) { page -= 1; loadSection() } }

    override fun reviewMerchant(row: JsonObject, status: String) {
        val id = row.string("id") ?: return
        if (status == "approved") {
            confirm("اعتماد التاجر", "سيظهر متجر ${row.string("store_name") ?: "التاجر"} للعملاء.", "اعتماد") {
                mutate("تم اعتماد التاجر") {
                    AdminApi.rpc("admin_review_merchant_application", buildJsonObject {
                        put("p_merchant_id", id); put("p_status", status); put("p_note", JsonNull)
                    })
                }
            }
            return
        }
        val title = when (status) {
            "changes_requested" -> "التعديلات المطلوبة"
            "rejected" -> "سبب رفض التاجر"
            else -> "سبب تعليق التاجر"
        }
        prompt(title, "اكتبي سبباً واضحاً يظهر في سجل الإدارة") { note ->
            mutate("تم تحديث حالة التاجر") {
                AdminApi.rpc("admin_review_merchant_application", buildJsonObject {
                    put("p_merchant_id", id); put("p_status", status); put("p_note", note)
                })
            }
        }
    }

    override fun transitionOrder(row: JsonObject, status: String) {
        val id = row.string("id") ?: return
        val dangerous = status in setOf("cancelled", "rejected", "failed")
        val run: (String?) -> Unit = { note ->
            mutate("تم تحديث حالة الطلب") {
                AdminApi.rpc("transition_order_status", buildJsonObject {
                    put("p_order_id", id); put("p_to_status", status)
                    if (note == null) put("p_note", JsonNull) else put("p_note", note)
                })
            }
        }
        if (dangerous) {
            prompt("سبب ${AdminUiText.orderStatus(status)}", "السبب مطلوب لحفظ قرار التدخل") { run(it) }
        } else {
            confirm("تحديث حالة الطلب", "نقل الطلب إلى: ${AdminUiText.orderStatus(status)}؟", "تحديث") { run(null) }
        }
    }

    override fun updateComplaint(id: String, status: String) = mutate("تم تحديث الشكوى") {
        AdminApi.patchRow("complaints", id, buildJsonObject {
            put("status", status); put("assigned_to", AdminApi.userId.orEmpty()); put("updated_at", Instant.now().toString())
        })
    }

    override fun resolveComplaint(id: String) = prompt("حل الشكوى", "سجّلي القرار أو الإجراء الذي تم") { resolution ->
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

    override fun reviewRequest(rpc: String, id: String, status: String) {
        val label = if (status == "approved") "اعتماد" else "رفض"
        confirm("$label الطلب", "هل أنتِ متأكدة من القرار؟", label, danger = status == "rejected") {
            mutate("تم تحديث الطلب") {
                AdminApi.rpc(rpc, buildJsonObject {
                    put("p_request_id", id); put("p_status", status); put("p_note", JsonNull)
                })
            }
        }
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

    override fun setAccountActive(row: JsonObject, active: Boolean) {
        confirm(
            if (active) "تنشيط الحساب" else "إيقاف الحساب",
            "الحساب: ${row.string("name") ?: row.string("phone") ?: "مستخدم"}",
            if (active) "تنشيط" else "إيقاف",
            danger = !active
        ) {
            mutate("تم تحديث حالة الحساب") {
                AdminApi.rpc("admin_set_account_active", buildJsonObject {
                    put("p_user_id", row.string("id").orEmpty()); put("p_active", active)
                })
            }
        }
    }

    override fun setProductActive(row: JsonObject, active: Boolean) {
        val execute: (String?) -> Unit = { reason ->
            mutate("تم تحديث ظهور المنتج") {
                AdminApi.rpc("admin_set_product_active", buildJsonObject {
                    put("p_product_id", row.string("id").orEmpty()); put("p_active", active)
                    if (reason == null) put("p_reason", JsonNull) else put("p_reason", reason)
                })
            }
        }
        if (active) confirm("إظهار المنتج", "سيعود المنتج للظهور إذا كان مخزونه متاحاً.", "إظهار") { execute(null) }
        else prompt("سبب إخفاء المنتج", "مثال: منتج مخالف أو بيانات غير دقيقة") { execute(it) }
    }

    override fun setCategoryActive(row: JsonObject, active: Boolean) = confirm(
        if (active) "تفعيل الفئة" else "إيقاف الفئة",
        row.string("name") ?: "الفئة",
        if (active) "تفعيل" else "إيقاف",
        danger = !active
    ) {
        mutate("تم تحديث الفئة") {
            AdminApi.rpc("admin_set_category_active", buildJsonObject {
                put("p_category_id", row.string("id").orEmpty()); put("p_active", active)
            })
        }
    }

    override fun setCityActive(row: JsonObject, active: Boolean) = mutate("تم تحديث المدينة") {
        AdminApi.patchRow("market_cities", row.string("id").orEmpty(), buildJsonObject {
            put("is_active", active); put("updated_at", Instant.now().toString())
        })
    }

    override fun setDeliveryProviderActive(row: JsonObject, active: Boolean) = mutate("تم تحديث مزود التوصيل") {
        AdminApi.patchRowBy("delivery_providers", "code", row.string("code").orEmpty(), buildJsonObject {
            put("is_active", active); put("updated_at", Instant.now().toString())
        })
    }

    override fun showTechnicalDetails(row: JsonObject) {
        val details = buildString {
            appendLine("النوع: ${row.string("error_type") ?: "—"}")
            appendLine("المصدر: ${row.string("source") ?: "—"}")
            appendLine("الإصدار: ${row.string("app_version") ?: "—"}")
            appendLine("الوقت: ${row.string("created_at") ?: "—"}")
            append("الرسالة: ${row.string("message") ?: "—"}")
        }
        AlertDialog.Builder(this).setTitle("التفاصيل التقنية").setMessage(details).setPositiveButton("إغلاق", null).show()
    }

    override fun openMerchantDocument(merchantId: String) {
        if (merchantId.isBlank()) return
        setLoading(true)
        lifecycleScope.launch {
            runCatching {
                val docs = AdminApi.rows("merchant_identity_documents", "select=storage_path,document_type,created_at&merchant_id=eq.${AdminApi.enc(merchantId)}&order=created_at.desc&limit=1")
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
            }.onFailure { showStatus(it.message ?: "تعذر فتح مستند الهوية") }
            setLoading(false)
        }
    }

    private fun mutate(success: String, block: suspend () -> Unit) {
        setLoading(true)
        clearStatus()
        lifecycleScope.launch {
            runCatching { block() }
                .onSuccess {
                    Toast.makeText(this@MainActivity, success, Toast.LENGTH_SHORT).show()
                    loadSection()
                }
                .onFailure {
                    showStatus(it.message ?: "تعذر تنفيذ العملية")
                    setLoading(false)
                }
        }
    }

    private fun signOut() {
        lifecycleScope.launch {
            runCatching { AdminApi.signOut() }
            role = null
            showLogin()
        }
    }

    private fun prompt(title: String, hint: String, submit: (String) -> Unit) {
        val input = EditText(this).apply {
            minLines = 2
            this.hint = hint
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("حفظ", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = input.text.toString().trim()
                if (value.isBlank()) input.error = "اكتبي السبب أو القرار" else { dialog.dismiss(); submit(value) }
            }
        }
        dialog.show()
    }

    private fun confirm(title: String, message: String, positive: String, danger: Boolean = false, action: () -> Unit) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(title).setMessage(message).setNegativeButton("إلغاء", null)
            .setPositiveButton(positive) { _, _ -> action() }.create()
        dialog.setOnShowListener {
            if (danger) dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getColor(R.color.error))
        }
        dialog.show()
    }

    private suspend fun rpcObject(name: String, body: JsonObject = buildJsonObject { }): JsonObject {
        val value = AdminApi.rpc(name, body) ?: return JsonObject(emptyMap())
        return runCatching { value.jsonObject }.getOrDefault(JsonObject(emptyMap()))
    }

    private suspend fun safeRows(table: String, query: String): JsonArray =
        runCatching { AdminApi.rows(table, query) }.getOrDefault(JsonArray(emptyList()))

    private suspend fun safeCount(table: String, filter: String = ""): Int =
        runCatching { AdminApi.exactCount(table, filter) }.getOrDefault(0)

    private fun query(select: String, filter: String, limit: Int = PAGE_SIZE + 1): String = buildString {
        append(select)
        if (filter.isNotBlank()) append('&').append(filter)
        append("&order=created_at.desc&limit=").append(limit)
        append("&offset=").append(page * PAGE_SIZE)
    }

    private fun JsonArray.matching(vararg keys: String): JsonArray {
        if (searchQuery.isBlank()) return this
        val needle = searchQuery.lowercase()
        return JsonArray(filter { element ->
            val row = element.jsonObject
            keys.any { key -> row.string(key)?.lowercase()?.contains(needle) == true }
        })
    }

    private fun JsonArray.pageItems(): JsonArray = JsonArray(take(PAGE_SIZE))

    private fun setLoading(loading: Boolean) {
        findViewById<ProgressBar>(R.id.dashboardProgress)?.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun showStatus(message: String) {
        findViewById<TextView>(R.id.dashboardStatus)?.apply { text = message; visibility = View.VISIBLE }
    }

    private fun clearStatus() {
        findViewById<TextView>(R.id.dashboardStatus)?.apply { text = ""; visibility = View.GONE }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object { private const val PAGE_SIZE = 20 }
}
