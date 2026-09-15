package com.tani.admin

import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.tani.admin.data.AdminApi
import com.tani.admin.data.bool
import com.tani.admin.data.string
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.time.Instant

class AdminManagementActivity : AppCompatActivity() {
    private lateinit var listContainer: LinearLayout
    private lateinit var statusView: TextView
    private lateinit var progress: ProgressBar
    private lateinit var addButton: MaterialButton
    private var currentAdmins = JsonArray(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AdminApi.init(this)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        buildUi()
        loadAdmins()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.tani_background))
            setPadding(dp(18), dp(16), dp(18), dp(18))
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleBlock.addView(text("إدارة المشرفين", 24f, R.color.text_dark, bold = true))
        titleBlock.addView(text("إضافة أو سحب صلاحية مدير النظام", 13f, R.color.text_muted))
        topRow.addView(titleBlock)
        topRow.addView(MaterialButton(this).apply {
            text = "رجوع"
            setOnClickListener { finish() }
        })
        root.addView(topRow)

        addButton = MaterialButton(this).apply {
            text = "+ إضافة أدمن بالإيميل"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(18) }
            setOnClickListener { showAddAdminDialog() }
        }
        root.addView(addButton)

        progress = ProgressBar(this).apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(14)
            }
        }
        root.addView(progress)

        statusView = text("", 13f, R.color.error).apply {
            visibility = View.GONE
            setPadding(0, dp(8), 0, dp(8))
        }
        root.addView(statusView)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply { topMargin = dp(10) }
        }
        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(24))
        }
        scroll.addView(listContainer)
        root.addView(scroll)

        setContentView(root)
    }

    private fun loadAdmins() {
        setBusy(true)
        clearStatus()
        lifecycleScope.launch {
            runCatching {
                val myRole = AdminApi.getMyRole()
                if (myRole != "admin") error("هذه الشاشة متاحة لمدير النظام فقط")
                AdminApi.rows(
                    "profiles",
                    "select=id,name,email,role,is_active,admin_previous_role,created_at&role=eq.admin&order=created_at.asc"
                )
            }.onSuccess {
                currentAdmins = it
                renderAdmins(it)
            }.onFailure {
                showStatus(it.message ?: "تعذر تحميل المشرفين")
            }
            setBusy(false)
        }
    }

    private fun renderAdmins(rows: JsonArray) {
        listContainer.removeAllViews()
        listContainer.addView(text("المشرفون الحاليون (${rows.size})", 17f, R.color.text_dark, bold = true).apply {
            setPadding(0, 0, 0, dp(10))
        })

        if (rows.isEmpty()) {
            listContainer.addView(text("لا يوجد مشرفون", 14f, R.color.text_muted))
            return
        }

        rows.forEach { element ->
            val row = element.jsonObject
            val isCurrent = row.string("id") == AdminApi.userId
            val active = row.bool("is_active")

            val card = MaterialCardView(this).apply {
                radius = dp(18).toFloat()
                strokeWidth = dp(1)
                setStrokeColor(getColor(R.color.border))
                setCardBackgroundColor(getColor(R.color.tani_surface))
                cardElevation = 0f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(12) }
            }

            val body = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(15), dp(16), dp(15))
            }
            body.addView(text(row.string("email") ?: "بريد غير متوفر", 16f, R.color.text_dark, bold = true))
            row.string("name")?.takeIf { it.isNotBlank() }?.let {
                body.addView(text(it, 14f, R.color.text_muted).apply { setPadding(0, dp(4), 0, 0) })
            }
            body.addView(text(
                when {
                    isCurrent -> "حسابك الحالي • ${if (active) "نشط" else "موقوف"}"
                    active -> "مدير نظام • نشط"
                    else -> "مدير نظام • موقوف"
                },
                13f,
                if (active) R.color.success else R.color.error
            ).apply { setPadding(0, dp(5), 0, 0) })

            if (!isCurrent) {
                body.addView(MaterialButton(this).apply {
                    text = "إزالة صلاحية الأدمن"
                    setTextColor(getColor(R.color.error))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(10) }
                    setOnClickListener { confirmRemove(row) }
                })
            }

            card.addView(body)
            listContainer.addView(card)
        }
    }

    private fun showAddAdminDialog() {
        val input = EditText(this).apply {
            hint = "example@gmail.com"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            textDirection = View.TEXT_DIRECTION_LTR
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        AlertDialog.Builder(this)
            .setTitle("إضافة أدمن")
            .setMessage("الحساب لازم يكون مسجل مسبقًا في تاني.")
            .setView(input)
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("إضافة") { _, _ ->
                grantAdmin(input.text.toString())
            }
            .show()
    }

    private fun grantAdmin(rawEmail: String) {
        val email = rawEmail.trim().lowercase()
        if (email.isBlank() || !email.contains('@')) {
            showStatus("أدخلي بريدًا إلكترونيًا صحيحًا")
            return
        }

        setBusy(true)
        clearStatus()
        lifecycleScope.launch {
            runCatching {
                val rows = AdminApi.rows(
                    "profiles",
                    "select=id,name,email,role,is_active,admin_previous_role&email=eq.${AdminApi.enc(email)}&limit=1"
                )
                val row = rows.firstOrNull()?.jsonObject ?: error("لا يوجد حساب مسجل بهذا البريد")
                if (!row.bool("is_active")) error("الحساب موقوف؛ فعّليه أولًا من إدارة العملاء")
                val currentRole = row.string("role") ?: "customer"
                if (currentRole == "admin") error("هذا الحساب أدمن بالفعل")
                val previousRole = currentRole.takeIf { it in setOf("customer", "seller", "support") } ?: "customer"
                val id = row.string("id") ?: error("معرف الحساب غير صالح")
                AdminApi.patchRow("profiles", id, buildJsonObject {
                    put("admin_previous_role", previousRole)
                    put("role", "admin")
                    put("updated_at", Instant.now().toString())
                })
            }.onSuccess {
                Toast.makeText(this@AdminManagementActivity, "تمت إضافة الأدمن", Toast.LENGTH_SHORT).show()
                loadAdmins()
            }.onFailure {
                showStatus(it.message ?: "تعذر إضافة الأدمن")
                setBusy(false)
            }
        }
    }

    private fun confirmRemove(row: JsonObject) {
        val email = row.string("email") ?: "هذا الحساب"
        AlertDialog.Builder(this)
            .setTitle("إزالة صلاحية الأدمن")
            .setMessage("هل تريدين سحب صلاحية الإدارة من $email؟")
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("إزالة") { _, _ -> removeAdmin(row) }
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getColor(R.color.error))
                }
                dialog.show()
            }
    }

    private fun removeAdmin(row: JsonObject) {
        val id = row.string("id") ?: return
        if (id == AdminApi.userId) {
            showStatus("لا يمكنك سحب صلاحية الإدارة من حسابك الحالي")
            return
        }
        val activeAdmins = currentAdmins.count { it.jsonObject.bool("is_active") }
        if (row.bool("is_active") && activeAdmins <= 1) {
            showStatus("يجب أن يبقى أدمن نشط واحد على الأقل")
            return
        }

        setBusy(true)
        clearStatus()
        lifecycleScope.launch {
            runCatching {
                var restoreRole = row.string("admin_previous_role")
                    ?.takeIf { it in setOf("customer", "seller", "support") }
                if (restoreRole == null) {
                    val merchant = AdminApi.rows(
                        "merchant_profiles",
                        "select=id&user_id=eq.${AdminApi.enc(id)}&limit=1"
                    )
                    restoreRole = if (merchant.isNotEmpty()) "seller" else "customer"
                }
                AdminApi.patchRow("profiles", id, buildJsonObject {
                    put("role", restoreRole)
                    put("admin_previous_role", JsonNull)
                    put("updated_at", Instant.now().toString())
                })
            }.onSuccess {
                Toast.makeText(this@AdminManagementActivity, "تمت إزالة صلاحية الأدمن", Toast.LENGTH_SHORT).show()
                loadAdmins()
            }.onFailure {
                showStatus(it.message ?: "تعذر إزالة صلاحية الأدمن")
                setBusy(false)
            }
        }
    }

    private fun setBusy(busy: Boolean) {
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        addButton.isEnabled = !busy
    }

    private fun showStatus(message: String) {
        statusView.text = message
        statusView.visibility = View.VISIBLE
    }

    private fun clearStatus() {
        statusView.text = ""
        statusView.visibility = View.GONE
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(getColor(color))
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
