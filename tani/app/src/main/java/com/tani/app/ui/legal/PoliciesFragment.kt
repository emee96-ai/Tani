package com.tani.app.ui.legal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.fragment.app.Fragment
import com.tani.app.R
import com.tani.app.ui.common.ScreenUi

class PoliciesFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val c=requireContext(); val root=ScreenUi.root(c)
        root.addView(ScreenUi.title(c,"السياسات والخصوصية"))
        root.addView(ScreenUi.subtitle(c,"ملخصات تشغيلية داخل التطبيق. النسخ القانونية الكاملة موجودة مع المشروع وتحتاج مراجعة قانونية محلية قبل الإطلاق التجاري."))
        policies.forEach { (title,body) -> root.addView(ScreenUi.card(c).apply { addView(ScreenUi.text(c,title,17f,true)); addView(ScreenUi.muted(c,body)) }) }
        return ScrollView(c).apply { setBackgroundColor(c.getColor(R.color.tani_background)); addView(root) }
    }
    private val policies=listOf(
        "شروط الاستخدام" to "استخدام تاني يتطلب بيانات صحيحة، احترام القوانين، وعدم إساءة المنصة أو التحايل على الطلبات أو التقييمات.",
        "سياسة الخصوصية" to "نستخدم بيانات الحساب والطلبات والتفاعل لتشغيل الخدمة والأمان والدعم، ولا تُعرض المستندات الخاصة للعامة.",
        "اتفاقية التاجر" to "التاجر مسؤول عن صحة المنتجات والأسعار والتوفر والتنفيذ والتوصيل، ويلتزم بسياسات المحتوى والثقة وعدم التلاعب.",
        "الاسترجاع والإلغاء" to "الإلغاء والاسترجاع يعتمدان على حالة الطلب وسياسة المتجر والحقوق النظامية. تاني يحتفظ بسجل الحالة لحل النزاعات.",
        "سياسة التوصيل" to "رسوم ومناطق وزمن التوصيل تظهر قبل الشراء. في الوضع الحالي يتحمل المتجر مسؤولية التوصيل ما لم يُعلن مزود آخر بوضوح.",
        "سياسة التقييمات" to "التقييمات المرتبطة بالتجربة الفعلية فقط، ويمكن إخفاء المحتوى المخالف أو المضلل بعد المراجعة.",
        "المنتجات المحظورة" to "يمنع عرض المنتجات غير القانونية أو الخطرة أو المضللة أو المخالفة لحقوق الآخرين وسياسات المنصة.",
        "الشكاوى وحفظ البيانات" to "يمكن فتح شكوى أو تذكرة دعم. عند حذف الحساب تُعطل بيانات العرض، مع الاحتفاظ بالحد الأدنى من السجلات اللازمة للطلبات والمحاسبة والأمان."
    )
}
