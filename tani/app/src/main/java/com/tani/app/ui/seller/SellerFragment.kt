package com.tani.app.ui.seller

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.tani.app.MainActivity
import com.tani.app.R
import com.tani.app.data.*
import com.tani.app.data.merchant.MerchantDashboardPolicy
import com.tani.app.data.merchant.MerchantOperationalState
import com.tani.app.data.network.CustomerErrorMessages
import com.tani.app.ui.growth.MerchantInsightsFragment
import com.tani.app.ui.monetization.MonetizationFragment
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class SellerFragment : Fragment() {
    private val repository = Repository()
    private lateinit var root: LinearLayout
    private var storeToggleInFlight = false

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
        storeToggleInFlight = false
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
                    coroutineScope {
                        val storeRequest = async { runCatching { repository.merchantStore() }.getOrNull() }
                        val summaryRequest = async { runCatching { repository.merchantDashboardSummary() }.getOrNull() }
                        val zonesRequest = async { runCatching { repository.merchantDeliveryZones(seller.id) }.getOrDefault(emptyList()) }
                        val deliveryRequest = async { runCatching { repository.merchantDeliverySettings(seller.id) }.getOrNull() }
                        val zones = zonesRequest.await()
                        DashboardData(
                            profile = profile,
                            seller = seller,
                            store = storeRequest.await(),
                            summary = summaryRequest.await(),
                            deliverySettings = deliveryRequest.await(),
                            deliveryZoneCount = zones.size,
                            activeDeliveryZoneCount = zones.count { it.is_active }
                        )
                    }
                } else {
                    DashboardData(profile, seller, null, null, null, 0, 0)
                }
            }.onSuccess { data ->
                if (data.profile?.verification_status == "approved" && data.seller != null) {
                    renderHub(data)
                } else {
                    renderStatus(data.profile)
                }
            }.onFailure { error ->
                showError(CustomerErrorMessages.from(error, "تعذر فتح لوحة المتجر. حاولي مرة أخرى."))
            }
        }
    }

    private fun renderHub(data: DashboardData) {
        val context = requireContext()
        val profile = data.profile ?: return
        val seller = data.seller ?: return
        val store = data.store
        val summary = data.summary
        val delivery = data.deliverySettings

        root.removeAllViews()
        root.addView(MerchantUi.title(context, store?.name ?: profile.store_name ?: seller.store_name.ifBlank { profile.business_name }))
        root.addView(MerchantUi.subtitle(context, "إدارة سريعة للطلبات والمتجر والمنتجات من مكان واحد."))

        val operationalState = MerchantDashboardPolicy.operationalState(
            storeExists = store != null,
            storeActive = store?.is_active == true,
            storeOpen = store?.is_open == true,
            deliveryConfigured = delivery != null,
            deliveryActive = delivery?.is_active == true,
            totalDeliveryZones = data.deliveryZoneCount,
            activeDeliveryZones = data.activeDeliveryZoneCount
        )
        val (storeStateTitle, storePill, storeTone) = when (operationalState) {
            MerchantOperationalState.MISSING_STORE -> Triple("بيانات المتجر تحتاج مراجعة", "إعداد ناقص", MerchantUi.Tone.WARNING)
            MerchantOperationalState.HIDDEN -> Triple("المتجر غير ظاهر للعملاء", "مخفي", MerchantUi.Tone.WARNING)
            MerchantOperationalState.CLOSED -> Triple("المتجر مغلق مؤقتاً", "مغلق", MerchantUi.Tone.NEUTRAL)
            MerchantOperationalState.DELIVERY_UNCONFIGURED -> Triple("التوصيل غير مكتمل", "توصيل ناقص", MerchantUi.Tone.WARNING)
            MerchantOperationalState.DELIVERY_PAUSED -> Triple("التوصيل متوقف حالياً", "التوصيل متوقف", MerchantUi.Tone.NEUTRAL)
            MerchantOperationalState.NO_ACTIVE_DELIVERY_ZONES -> Triple("لا توجد منطقة توصيل مفعّلة", "المناطق موقوفة", MerchantUi.Tone.WARNING)
            MerchantOperationalState.READY -> Triple("المتجر جاهز لاستقبال الطلبات ✓", "جاهز", MerchantUi.Tone.SUCCESS)
        }

        root.addView(MerchantUi.card(context, soft = true).apply {
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(MerchantUi.text(context, storeStateTitle, 17f, true))
                    addView(MerchantUi.muted(context, if (profile.trust_badge) "شارة الثقة مفعلة" else "الحساب معتمد — شارة الثقة تُبنى مع جودة الأداء", 12f).apply {
                        setPadding(0, MerchantUi.dp(context, 4), 0, 0)
                    })
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(MerchantUi.pill(context, storePill, storeTone))
            })
            addView(MerchantUi.muted(
                context,
                "الظهور: ${if (store?.is_active == true) "ظاهر" else "غير ظاهر"} • " +
                    "التوصيل: ${if (delivery?.is_active == true) "متاح" else "متوقف"} • " +
                    "المناطق: ${data.activeDeliveryZoneCount} مفعلة من ${data.deliveryZoneCount}",
                12f
            ).apply { setPadding(0, MerchantUi.dp(context, 7), 0, 0) })
            if (store != null && store.is_active) {
                addView(MerchantUi.compactButton(context, if (store.is_open) "إغلاق مؤقت" else "فتح المتجر") {
                    toggleStoreOpen(store)
                }.apply { setPadding(MerchantUi.dp(context, 12), 0, MerchantUi.dp(context, 12), 0) })
            }
        })

        val readiness = MerchantDashboardPolicy.readiness(
            storeExists = store != null,
            storeVisible = store?.is_active == true,
            hasLogo = !store?.logo_url.isNullOrBlank(),
            hasCover = !store?.cover_url.isNullOrBlank(),
            deliveryConfigured = delivery?.is_active == true,
            hasActiveDeliveryZone = data.activeDeliveryZoneCount > 0,
            hasActiveProduct = (summary?.active_products ?: 0) > 0
        )
        root.addView(MerchantUi.card(context).apply {
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                addView(MerchantUi.text(context, "جاهزية المتجر", 15.5f, true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(MerchantUi.pill(
                    context,
                    "${readiness.completed}/${readiness.total}",
                    if (readiness.completed == readiness.total) MerchantUi.Tone.SUCCESS else MerchantUi.Tone.BRAND
                ))
            })
            addView(MerchantUi.muted(
                context,
                if (readiness.completed == readiness.total) "الإعداد الأساسي مكتمل. راقبي الطلبات والمخزون يومياً."
                else "أكملي العناصر الناقصة تحت «يحتاج انتباهك» حتى تكون تجربة العميل مكتملة.",
                12.5f
            ).apply { setPadding(0, MerchantUi.dp(context, 6), 0, 0) })
        })

        val setupIssues = buildList {
            if (store == null) add("تعذر العثور على سجل المتجر")
            if (store != null && !store.is_active) add("فعّلي ظهور المتجر للعملاء")
            if (store?.logo_url.isNullOrBlank()) add("أضيفي شعار المتجر")
            if (store?.cover_url.isNullOrBlank()) add("أضيفي غلاف المتجر")
            if (delivery == null) add("أكملي إعدادات التوصيل")
            else if (!delivery.is_active) add("فعّلي التوصيل عندما تكوني جاهزة لاستقبال الطلبات")
            if (data.activeDeliveryZoneCount == 0) add("فعّلي منطقة توصيل واحدة على الأقل")
            if (summary != null && summary.active_products == 0) add("أضيفي أو فعّلي منتجاً واحداً على الأقل")
        }
        if (setupIssues.isNotEmpty()) {
            root.addView(MerchantUi.sectionTitle(context, "يحتاج انتباهك"))
            root.addView(MerchantUi.card(context).apply {
                addView(MerchantUi.text(context, "كمّلي إعداد المتجر", 15.5f, true))
                setupIssues.forEach { issue ->
                    addView(MerchantUi.muted(context, "• $issue", 13f).apply {
                        setPadding(0, MerchantUi.dp(context, 5), 0, 0)
                    })
                }
                if (store != null && (store.logo_url.isNullOrBlank() || store.cover_url.isNullOrBlank() || !store.is_active)) {
                    addView(MerchantUi.compactButton(context, "فتح بيانات المتجر") { open(MerchantStoreFragment()) })
                } else if (store == null) {
                    addView(MerchantUi.compactButton(context, "إعادة التحقق من المتجر") { load() })
                }
                if (delivery == null || !delivery.is_active || data.activeDeliveryZoneCount == 0) {
                    addView(MerchantUi.compactButton(context, "إعداد التوصيل") { open(MerchantDeliveryFragment()) })
                }
                if (summary != null && summary.active_products == 0) {
                    addView(MerchantUi.compactButton(context, "إدارة المنتجات") { open(MerchantProductsFragment()) })
                }
            })
        }

        root.addView(MerchantUi.sectionTitle(context, "نظرة عامة على الأداء"))
        if (summary != null) {
            val activeOrders = MerchantDashboardPolicy.activeOrderCount(summary.orders, summary.delivered_orders, summary.cancelled_orders)
            root.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                addView(MerchantUi.metricCard(context, "الطلبات", summary.orders.toString(), "كل الطلبات"), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = MerchantUi.dp(context, 4) })
                addView(MerchantUi.metricCard(context, "مبيعات مكتملة", "${money(summary.gmv)} جنيه", "طلبات تم تسليمها"), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = MerchantUi.dp(context, 4) })
            })
            root.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                addView(MerchantUi.metricCard(context, "طلبات جارية", activeOrders.toString(), "ليست مسلّمة أو ملغاة"), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = MerchantUi.dp(context, 4) })
                addView(MerchantUi.metricCard(context, "منتجات نشطة", summary.active_products.toString(), "من ${summary.products} منتج"), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = MerchantUi.dp(context, 4) })
            })
            root.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                addView(MerchantUi.metricCard(context, "العملاء", summary.customers.toString(), "عملاء مختلفون"), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = MerchantUi.dp(context, 4) })
                addView(MerchantUi.metricCard(context, "التقييم", String.format(java.util.Locale.US, "%.1f", summary.average_rating), "متوسط تقييم المنتجات"), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = MerchantUi.dp(context, 4) })
            })
        } else {
            root.addView(MerchantUi.card(context, soft = true).apply {
                addView(MerchantUi.muted(context, "تعذر تحديث مؤشرات الأداء الآن، لكن أدوات إدارة المتجر ما زالت متاحة.", 13f))
                addView(MerchantUi.compactButton(context, "إعادة تحديث اللوحة") { load() })
            })
        }

        root.addView(MerchantUi.sectionTitle(context, "الشغل اليومي"))
        val activeOrdersBadge = summary?.let {
            MerchantDashboardPolicy.activeOrderCount(it.orders, it.delivered_orders, it.cancelled_orders)
                .takeIf { count -> count > 0 }
                ?.let { count -> "$count جاري" }
        }
        root.addView(MerchantUi.navCard(context, "📦", "الطلبات", "استلام الطلبات وتحديث حالة التجهيز والتوصيل", activeOrdersBadge) {
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
    }

    private fun toggleStoreOpen(store: MerchantStore) {
        if (storeToggleInFlight) return
        storeToggleInFlight = true
        val newOpen = !store.is_open
        Toast.makeText(requireContext(), if (newOpen) "جاري فتح المتجر…" else "جاري إغلاق المتجر مؤقتاً…", Toast.LENGTH_SHORT).show()
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { repository.setMerchantStoreOpen(store.id, newOpen) }
                .onSuccess {
                    storeToggleInFlight = false
                    Toast.makeText(requireContext(), if (newOpen) "المتجر مفتوح الآن ✓" else "تم إغلاق المتجر مؤقتاً", Toast.LENGTH_SHORT).show()
                    load()
                }
                .onFailure { error ->
                    storeToggleInFlight = false
                    Toast.makeText(
                        requireContext(),
                        CustomerErrorMessages.from(error, "تعذر تحديث حالة المتجر. حاولي مرة أخرى."),
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
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

    private data class DashboardData(
        val profile: MerchantProfile?,
        val seller: Seller?,
        val store: MerchantStore?,
        val summary: MerchantDashboardSummary?,
        val deliverySettings: DeliverySettings?,
        val deliveryZoneCount: Int,
        val activeDeliveryZoneCount: Int
    )
}
