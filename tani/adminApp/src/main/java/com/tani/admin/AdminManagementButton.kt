package com.tani.admin

import android.content.Context
import android.content.ContextWrapper
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.tani.admin.data.AdminApi
import kotlinx.coroutines.launch

class AdminManagementButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.materialButtonStyle
) : MaterialButton(context, attrs, defStyleAttr) {

    init {
        visibility = View.GONE
        isAllCaps = false
        text = "إدارة المشرفين"
        setIconResource(R.drawable.ic_admin_customers)
        iconGravity = ICON_GRAVITY_TEXT_START
        setOnClickListener {
            val activity = context.findActivity() ?: return@setOnClickListener
            activity.findViewById<DrawerLayout>(R.id.adminDrawerLayout)?.closeDrawer(GravityCompat.START)
            AdminManagementDialog(activity).show()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val activity = context.findActivity() ?: return
        activity.lifecycleScope.launch {
            val isAdmin = runCatching { AdminApi.getMyRole() }.getOrNull() == "admin"
            visibility = if (isAdmin) View.VISIBLE else View.GONE
        }
    }
}

private tailrec fun Context.findActivity(): AppCompatActivity? = when (this) {
    is AppCompatActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
