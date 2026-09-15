package com.tani.app.ui.legal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.fragment.app.Fragment
import com.tani.app.BuildConfig
import com.tani.app.R
import com.tani.app.ui.common.ScreenUi

class AboutFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        val content = ScreenUi.root(context).apply {
            addView(ScreenUi.title(context, "من نحن"))
            addView(ScreenUi.subtitle(context, "تاني — من محلاتنا لبابك"))
            addSection(
                "عن تاني",
                "تاني سوق إلكتروني سوداني يجمع المتاجر والمشاريع المحلية في مكان واحد، عشان تكتشفي المنتجات وتطلبيها بصورة أسهل وأوضح وأكثر ثقة."
            )
            addSection(
                "رسالتنا",
                "نسهّل الشراء من التجار المحليين، ونساعد أصحاب المشاريع على عرض منتجاتهم وتنظيم طلباتهم والوصول لعملاء جدد."
            )
            addSection(
                "كيف نعمل؟",
                "تاني ينظم عرض المنتجات والطلبات والمتابعة. التاجر مسؤول عن صحة بيانات المنتج وتجهيز الطلب والتوصيل حسب المناطق والرسوم الظاهرة قبل تأكيد الطلب."
            )
            addSection(
                "الثقة أولاً",
                "طلبات التجار تُراجع قبل الاعتماد، ويمكن للعملاء متابعة حالة الطلب والوصول للدعم والإبلاغ عن أي مشكلة من داخل التطبيق."
            )
            addView(ScreenUi.muted(context, "الإصدار ${BuildConfig.VERSION_NAME}"))
        }
        return ScrollView(context).apply {
            setBackgroundColor(context.getColor(R.color.tani_background))
            addView(content)
        }
    }

    private fun LinearLayout.addSection(title: String, body: String) {
        val context = this.context
        addView(ScreenUi.card(context).apply {
            addView(ScreenUi.text(context, title, 18f, true))
            addView(ScreenUi.text(context, body, 15f))
        })
    }
}
