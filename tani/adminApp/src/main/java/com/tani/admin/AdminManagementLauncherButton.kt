package com.tani.admin

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import com.google.android.material.button.MaterialButton

class AdminManagementLauncherButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.materialButtonStyle
) : MaterialButton(context, attrs, defStyleAttr) {

    init {
        setOnClickListener {
            context.startActivity(Intent(context, AdminManagementActivity::class.java))
        }
    }
}
