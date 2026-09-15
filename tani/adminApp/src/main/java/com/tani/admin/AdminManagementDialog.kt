package com.tani.admin

import android.graphics.Typeface
import android.text.InputType
import android.util.Patterns
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.tani.admin.data.AdminApi
import com.tani.admin.data.bool
import com.tani.admin.data.string
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

class AdminManagementDialog(private val activity: AppCompatActivity) {
    private var dialog: AlertDialog? = null
    private lateinit var progress: ProgressBar
    private lateinit var status: TextView
    private lateinit var list: LinearLayout

    fun show() {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(18), dp(8), dp(18), dp(6))
        }

        root.addView(TextView(activity).apply {
            text = "أضيفي أو عدّلي صلاحيات المشرفين من الحسابات المسجلة في تاني."
            textSize = 13f
            setTextColor(color(R.color.text_muted))
            setPadding(0, 0, 0, dp(10))
        })

        root.addView(MaterialButton(activity).apply {
            text = "إضافة أدمن بالبريد"
            isAllCaps = false
            setIconResource(R.drawable.ic_admin_customers)
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            setOnClickListener { promptAddAdmin() }
        }, matchWrap())

        status = TextView(activity).apply {
            visibility = View.GONE
            textSize = 13f
            setTextColor(color(R.color.error))
            setPadding(0, dp(8), 0, dp(8))
        }
        root.addView(status, matchWrap())

        progress = ProgressBar(activity).apply { visibility = View.GONE }
        root.addView(progress, LinearLayout.LayoutParams(dp(32), dp(32)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(8)
            bottomMargin = dp(8)
        })

        list = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        root.addView(ScrollView(activity).apply {
            isFillViewport = true
            addView(list)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(390)))

        dialog = AlertDialog.Builder(activity)
            .setTitle("إدارة المشرفين")
            .setView(root)
            .setNegativeButton("إغلاق", null)
            .create()

        dialog?.show()
        val metrics = activity.resources.displayMetrics
        dialog?.window?.setLayout((metrics.widthPixels * 0.94f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        reload()
    }

    private fun reload() {
        if (dialog?.isShowing != true) return
        setLoading(true)
        status.visibility = View.GONE
        list.removeAllViews()
        activity.lifecycleScope.launch {
            runCatching {
                val value = AdminApi.rpc("admin_list_staff")
                value?.jsonArray ?: JsonArray(emptyList())
            }.onSuccess { rows ->
                if (dialog?.isShowing == true) render(rows)
            }.onFailure { error ->
                if (dialog?.isShowing == true) showError(error.message ?: "تعذر تحميل المشرفين")
            }
            if (dialog?.isShowing == true) setLoading(false)
        }
    }

    private fun render(rows: JsonArray) {
        list.removeAllViews()
        if (rows.isEmpty()) {
            list.addView(TextView(activity).apply {
                text = "لا يوجد مشرفون حالياً"
                gravity = Gravity.CENTER
                textSize = 14f
                setTextColor(color(R.color.text_muted))
                setPadding(dp(12), dp(28), dp(12), dp(28))
            }, matchWrap())
            return
        }

        rows.forEach { element ->
            val row = element.jsonObject
            val email = row.string("email").orEmpty()
            val currentRole = row.string("role").orEmpty()
            val self = row.string("user_id") == AdminApi.userId
            val active = row.bool("is_active")

            val card = MaterialCardView(activity).apply {
                radius = dp(16).toFloat()
                strokeWidth = dp(1)
                strokeColor = color(R.color.border)
                cardElevation = 0f
                setCardBackgroundColor(color(R.color.tani_surface))
            }
            val body = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                setPadding(dp(14), dp(13), dp(14), dp(13))
            }

            body.addView(TextView(activity).apply {
                text = email.ifBlank { "حساب بلا بريد" }
                textSize = 16f
                setTextColor(color(R.color.text_dark))
                setTypeface(typeface, Typeface.BOLD)
            })
            body.addView(TextView(activity).apply {
                val name = row.string("name") ?: "بدون اسم"
                val phone = row.string("phone") ?: "بلا رقم"
                text = "$name • $phone"
                textSize = 13f
                setTextColor(color(R.color.text_muted))
                setPadding(0, dp(4), 0, 0)
            })
            body.addView(TextView(activity).apply {
                val roleLabel = AdminUiText.role(currentRole)
                text = buildString {
                    append(roleLabel)
                    append(if (active) " • نشط" else " • موقوف")
                    if (self) append(" • حسابك الحالي")
                }
                textSize = 13f
                setTextColor(color(if (currentRole == "admin") R.color.tani_primary_dark else R.color.text_muted))
                setPadding(0, dp(5), 0, 0)
            })

            if (email.isNotBlank()) {
                body.addView(MaterialButton(activity).apply {
                    text = "تعديل الصلاحية"
                    isAllCaps = false
                    setOnClickListener { showRolePicker(email, currentRole) }
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.END
                    topMargin = dp(8)
                })
            }

            card.addView(body)
            list.addView(card, matchWrap().apply { bottomMargin = dp(9) })
        }
    }

    private fun promptAddAdmin() {
        val input = EditText(activity).apply {
            hint = "name@example.com"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            isSingleLine = true
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val prompt = AlertDialog.Builder(activity)
            .setTitle("إضافة أدمن")
            .setMessage("لازم يكون البريد مسجلاً في تاني أولاً.")
            .setView(input)
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("إضافة", null)
            .create()
        prompt.setOnShowListener {
            prompt.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val email = input.text.toString().trim().lowercase()
                if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    input.error = "أدخلي بريداً صحيحاً"
                    return@setOnClickListener
                }
                prompt.dismiss()
                changeRole(email, "admin", "تمت إضافة الأدمن")
            }
        }
        prompt.show()
    }

    private fun showRolePicker(email: String, currentRole: String) {
        val roles = arrayOf("admin", "support", "customer", "seller")
        val labels = arrayOf("مدير النظام", "فريق الدعم", "عميل", "تاجر")
        val checked = roles.indexOf(currentRole).coerceAtLeast(0)
        AlertDialog.Builder(activity)
            .setTitle("تعديل الصلاحية")
            .setSingleChoiceItems(labels, checked) { picker, which ->
                picker.dismiss()
                val target = roles[which]
                if (target == currentRole) return@setSingleChoiceItems
                confirmRoleChange(email, target, labels[which])
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun confirmRoleChange(email: String, targetRole: String, label: String) {
        AlertDialog.Builder(activity)
            .setTitle("تأكيد تغيير الصلاحية")
            .setMessage("سيتم تغيير صلاحية $email إلى: $label")
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("تغيير") { _, _ ->
                changeRole(email, targetRole, "تم تحديث الصلاحية")
            }
            .show()
    }

    private fun changeRole(email: String, role: String, success: String) {
        setLoading(true)
        status.visibility = View.GONE
        activity.lifecycleScope.launch {
            runCatching {
                AdminApi.rpc("admin_set_user_role_by_email", buildJsonObject {
                    put("p_email", email)
                    put("p_role", role)
                })
            }.onSuccess {
                Toast.makeText(activity, success, Toast.LENGTH_SHORT).show()
                reload()
            }.onFailure { error ->
                showError(error.message ?: "تعذر تحديث الصلاحية")
                setLoading(false)
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        progress.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun showError(message: String) {
        status.text = message
        status.visibility = View.VISIBLE
    }

    private fun color(id: Int): Int = ContextCompat.getColor(activity, id)
    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()
    private fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
}
