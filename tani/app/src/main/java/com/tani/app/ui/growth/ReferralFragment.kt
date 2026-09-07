package com.tani.app.ui.growth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.tani.app.R
import com.tani.app.data.repository.GrowthRepository
import com.tani.app.ui.common.ScreenUi
import com.tani.app.ui.share.ShareHelper
import kotlinx.coroutines.launch

class ReferralFragment : Fragment() {
    private val repository=GrowthRepository(); private lateinit var root:LinearLayout
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View { val c=requireContext();root=ScreenUi.root(c);return ScrollView(c).apply{setBackgroundColor(c.getColor(R.color.tani_background));addView(root)} }
    override fun onViewCreated(view: View, state: Bundle?){load()}
    private fun load(){val c=requireContext();root.removeAllViews();root.addView(ScreenUi.title(c,"دعوة الأصدقاء"));root.addView(ScreenUi.subtitle(c,"كود الإحالة يربط الدعوة بالحساب، وتكتمل الإحالة بعد أول طلب يتم تسليمه."));
        lifecycleScope.launch { runCatching { repository.referralCode() to repository.referrals() }.onSuccess { (code,rows)->
            root.addView(ScreenUi.card(c).apply{addView(ScreenUi.text(c,code,22f,true));addView(ScreenUi.button(c,"مشاركة الكود"){ShareHelper.referral(c,code)})})
            val input=ScreenUi.input(c,"عندك كود إحالة؟ أدخليه هنا");root.addView(input);root.addView(ScreenUi.button(c,"تطبيق الكود") { lifecycleScope.launch { runCatching{repository.applyReferralCode(input.text.toString())}.onSuccess{Toast.makeText(c,if(it)"تم تطبيق الكود" else "الكود مطبق مسبقاً",Toast.LENGTH_SHORT).show();load()}.onFailure{Toast.makeText(c,it.message?:"تعذر تطبيق الكود",Toast.LENGTH_LONG).show()} } })
            root.addView(ScreenUi.text(c,"إحالاتي",18f,true));if(rows.isEmpty())root.addView(ScreenUi.muted(c,"لا توجد إحالات بعد."));rows.forEach{r->root.addView(ScreenUi.muted(c,"${r.status} • ${r.created_at?:""}"))}
        }.onFailure{root.addView(ScreenUi.muted(c,it.message?:"تعذر تحميل الإحالات"))} }
    }
}
