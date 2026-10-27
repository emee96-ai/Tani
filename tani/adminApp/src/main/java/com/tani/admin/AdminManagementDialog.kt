package com.tani.admin

import android.graphics.Typeface
import android.text.InputType
import android.util.Patterns
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
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
            text = activity.getString(R.string.admin_management_dialog_1)
            textSize = 13f
            setTextColor(color(R.color.text_muted))
            setPadding(0, 0, 0, dp(10))
        })

        root.addView(MaterialButton(activity).apply {
            text = activity.getString(R.string.admin_management_dialog_2)
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
            .setTitle(activity.getString(R.string.admin_management_dialog_3))
            .setView(root)
            .setNegativeButton(activity.getString(R.string.admin_management_dialog_4), null)
            .create()
        // نافذة الحوار لا ترث FLAG_SECURE من النشاط، وهي تعرض بيانات الحسابات.
        dialog?.window?.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

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
                if (dialog?.isShowing == true) showError(error.message ?: activity.getString(R.string.admin_management_dialog_5))
            }
            if (dialog?.isShowing == true) setLoading(false)
        }
    }

    private fun render(rows: JsonArray) {
        list.removeAllViews()
        if (rows.isEmpty()) {
            list.addView(TextView(activity).apply {
                text = activity.getString(R.string.admin_management_dialog_6)
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
                text = email.ifBlank { activity.getString(R.string.admin_management_dialog_7) }
                textSize = 16f
                setTextColor(color(R.color.text_dark))
                setTypeface(typeface, Typeface.BOLD)
            })
            body.addView(TextView(activity).apply {
                val name = row.string("name") ?: activity.getString(R.string.admin_management_dialog_8)
                val phone = row.string("phone") ?: activity.getString(R.string.admin_management_dialog_9)
                text = activity.getString(R.string.admin_management_dialog_29, name, phone)
                textSize = 13f
                setTextColor(color(R.color.text_muted))
                setPadding(0, dp(4), 0, 0)
            })
            body.addView(TextView(activity).apply {
                val roleLabel = AdminUiText.role(currentRole)
                text = buildString {
                    append(roleLabel)
                    append(if (active) activity.getString(R.string.admin_management_dialog_10) else activity.getString(R.string.admin_management_dialog_11))
                    if (self) append(activity.getString(R.string.admin_management_dialog_12))
                }
                textSize = 13f
                setTextColor(color(if (currentRole == "admin") R.color.tani_primary_dark else R.color.text_muted))
                setPadding(0, dp(5), 0, 0)
            })

            if (email.isNotBlank()) {
                body.addView(MaterialButton(activity).apply {
                    text = activity.getString(R.string.admin_management_dialog_13)
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
            .setTitle(activity.getString(R.string.admin_management_dialog_14))
            .setMessage(activity.getString(R.string.admin_management_dialog_15))
            .setView(input)
            .setNegativeButton(activity.getString(R.string.admin_management_dialog_16), null)
            .setPositiveButton(activity.getString(R.string.admin_management_dialog_17), null)
            .create()
        prompt.setOnShowListener {
            prompt.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val email = input.text.toString().trim().lowercase()
                if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    input.error = activity.getString(R.string.admin_management_dialog_18)
                    return@setOnClickListener
                }
                prompt.dismiss()
                changeRole(email, "admin", activity.getString(R.string.admin_management_dialog_19))
            }
        }
        prompt.show()
    }

    private fun showRolePicker(email: String, currentRole: String) {
        val roles = arrayOf("admin", "support", "customer", "seller")
        val labels = arrayOf(activity.getString(R.string.admin_management_dialog_20), activity.getString(R.string.admin_management_dialog_21), activity.getString(R.string.admin_management_dialog_22), activity.getString(R.string.admin_management_dialog_23))
        val checked = roles.indexOf(currentRole).coerceAtLeast(0)
        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.admin_management_dialog_13))
            .setSingleChoiceItems(labels, checked) { picker, which ->
                picker.dismiss()
                val target = roles[which]
                if (target == currentRole) return@setSingleChoiceItems
                confirmRoleChange(email, target, labels[which])
            }
            .setNegativeButton(activity.getString(R.string.admin_management_dialog_16), null)
            .show()
    }

    private fun confirmRoleChange(email: String, targetRole: String, label: String) {
        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.admin_management_dialog_24))
            .setMessage(activity.getString(R.string.admin_management_dialog_25, email, label))
            .setNegativeButton(activity.getString(R.string.admin_management_dialog_16), null)
            .setPositiveButton(activity.getString(R.string.admin_management_dialog_26)) { _, _ ->
                changeRole(email, targetRole, activity.getString(R.string.admin_management_dialog_27))
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
                showError(error.message ?: activity.getString(R.string.admin_management_dialog_28))
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
