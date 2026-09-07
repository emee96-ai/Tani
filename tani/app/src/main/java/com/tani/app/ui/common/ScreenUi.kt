package com.tani.app.ui.common

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.tani.app.R

object ScreenUi {
    fun root(context: Context) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(context,18), dp(context,18), dp(context,18), dp(context,28))
    }

    fun title(context: Context, text: String) = TextView(context).apply {
        this.text = text; textSize = 26f; setTextColor(context.getColor(R.color.text_dark)); setTypeface(typeface, Typeface.BOLD)
    }

    fun subtitle(context: Context, text: String) = TextView(context).apply {
        this.text = text; textSize = 14f; setTextColor(context.getColor(R.color.text_muted)); setPadding(0, dp(context,4), 0, dp(context,12))
    }

    fun card(context: Context) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = context.getDrawable(R.drawable.bg_card)
        setPadding(dp(context,16), dp(context,16), dp(context,16), dp(context,16))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(context,12)
        }
    }

    fun text(context: Context, value: String, size: Float = 15f, bold: Boolean = false) = TextView(context).apply {
        text = value; textSize = size; setTextColor(context.getColor(R.color.text_dark)); if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    fun muted(context: Context, value: String) = TextView(context).apply {
        text = value; textSize = 13f; setTextColor(context.getColor(R.color.text_muted)); setPadding(0, dp(context,4), 0, 0)
    }

    fun input(context: Context, hint: String, multiline: Boolean = false) = EditText(context).apply {
        this.hint = hint; background = context.getDrawable(R.drawable.bg_field); setPadding(dp(context,14), dp(context,10), dp(context,14), dp(context,10))
        minHeight = dp(context,52); if (multiline) { minLines = 3; maxLines = 6 } else isSingleLine = true
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(context,10) }
    }

    fun button(context: Context, label: String, onClick: () -> Unit) = Button(context).apply {
        text = label; minHeight = dp(context,50); setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(context,8) }
    }

    fun spacer(context: Context, height: Int = 8) = View(context).apply { layoutParams = LinearLayout.LayoutParams(1, dp(context,height)) }

    fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()
}
