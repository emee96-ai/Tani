package com.tani.app.ui.seller

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.tani.app.R

object MerchantUi {
    enum class Tone { SUCCESS, WARNING, ERROR, NEUTRAL, BRAND }

    fun decorateRoot(root: LinearLayout) {
        root.layoutDirection = View.LAYOUT_DIRECTION_RTL
        root.textDirection = View.TEXT_DIRECTION_LOCALE
        root.gravity = Gravity.TOP
    }

    fun title(context: Context, value: String) = text(context, value, 27f, true).apply { setPadding(0, 0, 0, dp(context, 4)) }
    fun subtitle(context: Context, value: String) = muted(context, value, 14f).apply { setPadding(0, 0, 0, dp(context, 14)) }
    fun sectionTitle(context: Context, value: String) = text(context, value, 19f, true).apply { setPadding(0, dp(context, 10), 0, dp(context, 8)) }

    fun text(context: Context, value: String, size: Float = 15f, bold: Boolean = false) = TextView(context).apply {
        text = value
        textSize = size
        setTextColor(context.getColor(R.color.text_dark))
        textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        gravity = Gravity.START
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    fun muted(context: Context, value: String, size: Float = 13f) = TextView(context).apply {
        text = value
        textSize = size
        setTextColor(context.getColor(R.color.text_muted))
        textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        gravity = Gravity.START
    }

    fun card(context: Context, soft: Boolean = false) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        gravity = Gravity.START
        setPadding(dp(context, 16), dp(context, 16), dp(context, 16), dp(context, 16))
        background = rounded(context, if (soft) R.color.brand_soft else R.color.tani_surface, R.color.border, 18)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(context, 12)
        }
    }

    fun metricCard(context: Context, label: String, value: String, note: String? = null) = card(context).apply {
        minimumHeight = dp(context, 104)
        addView(muted(context, label, 12.5f))
        addView(text(context, value, 21f, true).apply { setPadding(0, dp(context, 4), 0, 0) })
        note?.takeIf { it.isNotBlank() }?.let { addView(muted(context, it, 11.5f).apply { setPadding(0, dp(context, 4), 0, 0) }) }
    }

    fun navCard(context: Context, icon: String, title: String, subtitle: String, badge: String? = null, onClick: () -> Unit) = card(context).apply {
        isClickable = true
        isFocusable = true
        foreground = context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground)).let { a ->
            val d = a.getDrawable(0); a.recycle(); d
        }
        setOnClickListener { onClick() }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(TextView(context).apply { text = icon; textSize = 22f; gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(context, 42), dp(context, 42)))
        val copy = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.START }
        copy.addView(text(context, title, 16.5f, true))
        copy.addView(muted(context, subtitle, 12.5f).apply { setPadding(0, dp(context, 3), 0, 0) })
        row.addView(copy, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(context, 10) })
        badge?.takeIf { it.isNotBlank() }?.let { row.addView(pill(context, it, Tone.BRAND)) }
        row.addView(text(context, "‹", 25f, true).apply { gravity = Gravity.CENTER })
        addView(row)
    }

    fun pill(context: Context, label: String, tone: Tone = Tone.NEUTRAL) = TextView(context).apply {
        text = label
        textSize = 11.5f
        setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER
        setPadding(dp(context, 10), dp(context, 5), dp(context, 10), dp(context, 5))
        val (fill, textColor) = when (tone) {
            Tone.SUCCESS -> R.color.success to R.color.tani_surface
            Tone.WARNING -> R.color.warning to R.color.tani_surface
            Tone.ERROR -> R.color.error to R.color.tani_surface
            Tone.BRAND -> R.color.brand_soft to R.color.tani_primary
            Tone.NEUTRAL -> R.color.brand_soft to R.color.text_muted
        }
        background = rounded(context, fill, null, 50)
        setTextColor(context.getColor(textColor))
    }

    fun primaryButton(context: Context, label: String, onClick: () -> Unit) = baseButton(context, label, onClick).apply {
        backgroundTintList = ColorStateList.valueOf(context.getColor(R.color.tani_primary))
        setTextColor(context.getColor(R.color.tani_surface))
    }
    fun secondaryButton(context: Context, label: String, onClick: () -> Unit) = baseButton(context, label, onClick).apply {
        backgroundTintList = ColorStateList.valueOf(context.getColor(R.color.brand_soft))
        setTextColor(context.getColor(R.color.tani_primary))
    }
    fun dangerButton(context: Context, label: String, onClick: () -> Unit) = baseButton(context, label, onClick).apply {
        backgroundTintList = ColorStateList.valueOf(context.getColor(R.color.error))
        setTextColor(context.getColor(R.color.tani_surface))
    }
    fun compactButton(context: Context, label: String, onClick: () -> Unit) = secondaryButton(context, label, onClick).apply {
        minHeight = dp(context, 42); minimumHeight = dp(context, 42); textSize = 12.5f
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    }

    fun emptyCard(context: Context, title: String, message: String) = card(context, soft = true).apply {
        addView(text(context, title, 16f, true))
        addView(muted(context, message, 13f).apply { setPadding(0, dp(context, 5), 0, 0) })
    }
    fun row(context: Context) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        gravity = Gravity.CENTER_VERTICAL
    }
    fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private fun baseButton(context: Context, label: String, onClick: () -> Unit) = MaterialButton(context).apply {
        text = label; isAllCaps = false; textSize = 14f; cornerRadius = dp(context, 14)
        minHeight = dp(context, 50); minimumHeight = dp(context, 50); setOnClickListener { onClick() }
    }
    private fun rounded(context: Context, fillColor: Int, strokeColor: Int?, radiusDp: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(context.getColor(fillColor))
        cornerRadius = dp(context, radiusDp).toFloat()
        strokeColor?.let { setStroke(dp(context, 1), context.getColor(it)) }
    }
}
