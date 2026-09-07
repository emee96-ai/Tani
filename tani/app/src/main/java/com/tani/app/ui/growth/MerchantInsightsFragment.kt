package com.tani.app.ui.growth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.tani.app.R
import com.tani.app.data.repository.GrowthRepository
import com.tani.app.data.repository.ScaleRepository
import com.tani.app.ui.common.ScreenUi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class MerchantInsightsFragment : Fragment() {
    private val growth=GrowthRepository(); private val scale=ScaleRepository(); private lateinit var root:LinearLayout
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View { val c=requireContext();root=ScreenUi.root(c);return ScrollView(c).apply{setBackgroundColor(c.getColor(R.color.tani_background));addView(root)} }
    override fun onViewCreated(view: View, state: Bundle?){load()}
    private fun load(){val sellerId=requireArguments().getString(ARG_SELLER)?:return; val c=requireContext();root.removeAllViews();root.addView(ScreenUi.title(c,"أداء المتجر"));root.addView(ScreenUi.subtitle(c,"مؤشرات تشغيل تساعد على تحسين الطلبات والمخزون قبل التوسع."));
        lifecycleScope.launch { runCatching { coroutineScope { async{growth.merchantMetrics(sellerId)}.await() to async{scale.inventoryHealth()}.await() } }.onSuccess{(m,h)->
            root.addView(ScreenUi.card(c).apply { addView(ScreenUi.text(c,"المبيعات والطلبات",18f,true)); addView(ScreenUi.muted(c,"الطلبات: ${m?.total_orders?:0} • المكتملة: ${m?.completed_orders?:0} • الملغاة: ${m?.cancelled_orders?:0}")); addView(ScreenUi.muted(c,"المبيعات: ${m?.total_sales?:0.0} • المشاهدات: ${m?.product_views?:0}")); addView(ScreenUi.muted(c,"التحويل: ${m?.conversion_rate?:0.0}% • تكرار العملاء: ${m?.repeat_customers?:0}")); addView(ScreenUi.muted(c,"متوسط التقييم: ${m?.average_rating?:0.0} • إلغاء: ${m?.cancellation_rate?:0.0}%")) })
            root.addView(ScreenUi.card(c).apply { addView(ScreenUi.text(c,"صحة المخزون",18f,true)); addView(ScreenUi.muted(c,"نفد المخزون: ${h.out_of_stock}")); addView(ScreenUi.muted(c,"مخزون منخفض (1–3): ${h.low_stock}")); addView(ScreenUi.muted(c,"منتجات متوقفة: ${h.inactive}")) })
        }.onFailure{root.addView(ScreenUi.muted(c,it.message?:"تعذر تحميل المؤشرات"))} }
    }
    companion object{private const val ARG_SELLER="seller_id";fun newInstance(id:String)=MerchantInsightsFragment().apply{arguments=Bundle().apply{putString(ARG_SELLER,id)}}}
}
