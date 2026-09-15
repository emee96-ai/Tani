package com.tani.app.ui.seller

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.tani.app.MainActivity
import com.tani.app.R
import com.tani.app.data.MerchantDashboardSummary
import com.tani.app.data.MerchantProfile
import com.tani.app.data.Repository
import com.tani.app.data.Seller
import com.tani.app.ui.growth.MerchantInsightsFragment
import com.tani.app.ui.monetization.MonetizationFragment
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class SellerFragment : Fragment() {
    private val repository = Repository()
    private lateinit var root: LinearLayout

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        state: Bundle?
    ): View {
        val context = requireContext()
        root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(MerchantUi.dp(context, 16), MerchantUi.dp(context, 18), MerchantUi.dp(context, 16), MerchantUi.dp(context, 30))
            MerchantUi.decorateRoot(this)
        }
        return ScrollView(context).apply {
            isFillViewport = true
            setBackgroundColor(context.getColor(R.color.tani_background))
            addView(root)
        }
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        load()
    }

    private fun load() {
        val context = requireContext()
        root.removeAllViews()
        root.addView(MerchantUi.title(context, "متجرك"))
        root.addView(MerchantUi.card(context, soft = true).apply {
            addView(MerchantUi.text(context, "جاري تجهيز لوحة المتجر…", 14f, true))
        })

        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                val profile = repository.merchantProfile()
                val seller = repository.seller()
                if (profile?.verification_status == "approved" && seller != null) {
                    val summary = runCatching { repository.merchantDashboardSummary() }.getOrNull()
                    Triple(profile, seller, summary)
                } else Triple(profile, seller, null)
            }.onSuccess { (profile, seller, summary) ->
                if (profile?.verification_status == "approved" && seller != null) renderHub(profile, seller, summary)
                else renderStatus(profile)
            }.onFailure { showError(it.message ?: "تعذر فتح لوحة المتجر") }
        }
    }

    private fun renderHub(profile: MerchantProfile, seller: Seller, summary: MerchantDashboardSummary?) {
        val context = requireContext()
        root.removeAllViews()
        root.addView(MerchantUi.title(context, profile.store_name ?: seller.store_name.ifBlank { profile.business_name }))
        root.addView(MerchantUi.subtitle(context, "كل شغل متجرك في مكان واحد."))

        root.addView(MerchantUi.card(context, soft = true).apply {
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(MerchantUi.text(context, "المتجر شغال ✓", 17f, true))
                    addView(MerchantUi.muted(context, if (profile.trust_badge) "شارة الثقة مفعلة" else "شارة الثقة تُبنى مع جودة الأداء", 12f).apply {
                        setPadding(0, MerchantUi.dp(context, 4), 0, 0)
                    })
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(MerchantUi.pill(context, "معتمد", MerchantUi.Tone.SUCCESS))
            })
        })

        summary?.let { m ->
            root.addView(MerchantUi.sectionTitle(context, "اليوم في متجرك"))
            root.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                addView(MerchantUi.metricCard(context, "الطلبات", m.orders.toString()), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = MerchantUi.dp(context, 4) })
                addView(MerchantUi.metricCard(context, "المبيعات", "${money(m.gmv)} جنيه"), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = MerchantUi.dp(context, 4) })
            })
            root.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                addView(MerchantUi.metricCard(context, "منتجات نشطة", m.active_products.toString()), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = MerchantUi.dp(context, 4) })
                addView(MerchantUi.metricCard(context, "العملاء", m.customers.toString()), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = MerchantUi.dp(context, 4) })
            })
        }

        root.addView(MerchantUi.sectionTitle(context, "الشغل اليومي"))
        root.addView(MerchantUi.navCard(context, "📦", "الطلبات", "استلام الطلبات وتحديث حالة التجهيز والتوصيل", summary?.orders?.takeIf { it > 0 }?.toString()) {
            open(MerchantOrdersFragment())
        })
        root.addView(MerchantUi.navCard(context, "🏷️", "المنتجات والمخزون", "إضافة المنتجات والأسعار والصور والمخزون") {
            open(MerchantProductsFragment())
        })
        root.addView(MerchantUi.navCard(context, "🏪", "بيانات المتجر", "الاسم والوصف والشعار والغلاف وحالة المتجر") {
            open(MerchantStoreFragment())
        })
        root.addView(MerchantUi.navCard(context, "🛵", "التوصيل", "رسوم مختلفة لكل منطقة وزمن الوصول") {
            open(MerchantDeliveryFragment())
        })

        root.addView(MerchantUi.sectionTitle(context, "الأداء والنمو"))
        root.addView(MerchantUi.navCard(context, "📊", "أداء المتجر", "المبيعات والتحويل والعملاء وأفضل المنتجات") {
            open(MerchantAnalyticsFragment())
        })
        root.addView(MerchantUi.navCard(context, "📈", "صحة المتجر", "تنبيهات المخزون ومؤشرات التشغيل") {
            open(MerchantInsightsFragment.newInstance(seller.id))
        })
        root.addView(MerchantUi.navCard(context, "✨", "النمو والظهور", "الاشتراكات والظهور الممول والحملات") {
            open(MonetizationFragment.newInstance(seller.id))
        })

        root.addView(MerchantUi.card(context, soft = true).apply {
            addView(MerchantUi.text(context, "الظهور الممول واضح دائماً", 13.5f, true))
            addView(MerchantUi.muted(context, "أي ظهور مدفوع للعملاء يظهر بوسم «ممول» ولا يختلط بالمحتوى العضوي.", 12f).apply {
                setPadding(0, MerchantUi.dp(context, 4), 0, 0)
            })
        })
    }

    private fun renderStatus(profile: MerchantProfile?) {
        val context = requireContext()
        root.removeAllViews()
        root.addView(MerchantUi.title(context, "حساب التاجر"))
        if (profile == null) {
            root.addView(MerchantUi.emptyCard(context, "لسه ما بدأتي تسجيل التاجر", "ابدئي التسجيل وأكملي بيانات النشاط والمتجر والتوصيل والهوية."))
            root.addView(MerchantUi.primaryButton(context, "ابدئي التسجيل") { open(MerchantOnboardingFragment()) })
            return
        }

        val (title, message, tone) = when (profile.verification_status) {
            "pending" -> Triple("طلبك قيد المراجعة", "بنراجع بيانات المتجر والهوية. حتظهر لوحة المتجر كاملة بعد الاعتماد.", MerchantUi.Tone.WARNING)
            "changes_requested" -> Triple("في تعديلات مطلوبة", profile.review_note ?: "راجعي بيانات الطلب وأعيدي الإرسال.", MerchantUi.Tone.WARNING)
            "rejected" -> Triple("الطلب يحتاج مراجعة", profile.review_note ?: "يمكنك تعديل البيانات وإعادة الإرسال.", MerchantUi.Tone.ERROR)
            "suspended" -> Triple("الحساب موقوف مؤقتاً", profile.review_note ?: "راجعي ملاحظة الإدارة.", MerchantUi.Tone.ERROR)
            else -> Triple("حالة حساب التاجر", "أكملي بيانات التسجيل.", MerchantUi.Tone.NEUTRAL)
        }
        root.addView(MerchantUi.card(context).apply {
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                addView(MerchantUi.text(context, title, 17f, true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(MerchantUi.pill(context, statusText(profile.verification_status), tone))
            })
            addView(MerchantUi.muted(context, message, 13f).apply { setPadding(0, MerchantUi.dp(context, 7), 0, 0) })
        })
        if (profile.verification_status in setOf("changes_requested", "rejected")) {
            root.addView(MerchantUi.primaryButton(context, "تعديل وإعادة الإرسال") { open(MerchantOnboardingFragment()) })
        } else {
            root.addView(MerchantUi.secondaryButton(context, "تحديث الحالة") { load() })
        }
    }

    private fun showError(message: String) {
        val context = requireContext()
        root.removeAllViews()
        root.addView(MerchantUi.title(context, "متجرك"))
        root.addView(MerchantUi.card(context).apply {
            addView(MerchantUi.text(context, "تعذر فتح لوحة المتجر", 16f, true))
            addView(MerchantUi.muted(context, message).apply { setPadding(0, MerchantUi.dp(context, 5), 0, MerchantUi.dp(context, 10)) })
            addView(MerchantUi.secondaryButton(context, "إعادة المحاولة") { load() })
        })
    }

    private fun open(fragment: Fragment) {
        (activity as? MainActivity)?.show(fragment)
    }

    private fun statusText(status: String): String = when (status) {
        "pending" -> "قيد المراجعة"
        "approved" -> "معتمد"
        "changes_requested" -> "تعديلات مطلوبة"
        "rejected" -> "مرفوض"
        "suspended" -> "موقوف"
        else -> status
    }

    private fun money(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else String.format(java.util.Locale.US, "%.2f", value)
}
