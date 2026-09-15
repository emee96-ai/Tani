package com.tani.app.ui.seller

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.tani.app.R
import com.tani.app.data.MerchantDashboardSummary
import com.tani.app.data.Repository
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MerchantAnalyticsFragment : Fragment() {
    private val repository = Repository()
    private lateinit var root: LinearLayout

    override fun onCreateView(inflater: android.view.LayoutInflater, container: android.view.ViewGroup?, state: Bundle?): View {
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

    override fun onViewCreated(view: View, state: Bundle?) { load() }

    private fun load() {
        val context = requireContext(); root.removeAllViews()
        root.addView(MerchantUi.title(context, "أداء المتجر"))
        root.addView(MerchantUi.card(context, soft = true).apply { addView(MerchantUi.text(context, "جاري حساب المؤشرات…", 14f, true)) })
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { repository.merchantDashboardSummary() }
                .onSuccess(::render)
                .onFailure { showError(it.message ?: "تعذر تحميل المؤشرات") }
        }
    }

    private fun render(m: MerchantDashboardSummary) {
        val context = requireContext(); root.removeAllViews()
        root.addView(MerchantUi.title(context, "أداء المتجر"))
        root.addView(MerchantUi.subtitle(context, "أرقام بسيطة وواضحة تساعدك تعرفي شغلك ماشي كيف."))

        root.addView(MerchantUi.sectionTitle(context, "المبيعات والطلبات"))
        addMetricPair("كل الطلبات", m.orders.toString(), "تم التسليم", m.delivered_orders.toString())
        root.addView(MerchantUi.metricCard(context, "إجمالي المبيعات", "${money(m.gmv)} جنيه", "قيمة الطلبات المسجلة على المتجر"))

        root.addView(MerchantUi.sectionTitle(context, "العملاء والوصول"))
        addMetricPair("العملاء", m.customers.toString(), "عملاء عادوا للشراء", m.repeat_customers.toString())
        root.addView(MerchantUi.metricCard(context, "مشاهدات المنتجات", m.product_views.toString()))

        root.addView(MerchantUi.sectionTitle(context, "جودة الأداء"))
        root.addView(rateCard("معدل التحويل", m.conversion_rate, "نسبة المشاهدات التي تحولت إلى طلب"))
        root.addView(rateCard("إتمام الطلبات", m.completion_rate, "نسبة الطلبات التي وصلت للتسليم"))
        root.addView(rateCard("إلغاء الطلبات", m.cancellation_rate, "كلما قلت النسبة كان أفضل"))
        root.addView(MerchantUi.card(context).apply {
            addView(MerchantUi.text(context, "متوسط التقييم", 14.5f, true))
            addView(MerchantUi.text(context, if (m.average_rating > 0) "${money(m.average_rating)} / 5" else "—", 21f, true).apply {
                setPadding(0, MerchantUi.dp(context, 5), 0, 0)
            })
        })
        root.addView(MerchantUi.card(context).apply {
            addView(MerchantUi.text(context, "متوسط الاستجابة", 14.5f, true))
            addView(MerchantUi.text(context, m.average_response_seconds?.let { "${(it / 60.0).roundToInt()} دقيقة" } ?: "—", 21f, true).apply {
                setPadding(0, MerchantUi.dp(context, 5), 0, 0)
            })
        })

        root.addView(MerchantUi.sectionTitle(context, "المنتجات"))
        root.addView(MerchantUi.metricCard(context, "المنتجات النشطة", "${m.active_products} من ${m.products}"))
        root.addView(MerchantUi.sectionTitle(context, "الأكثر مبيعاً"))
        if (m.best_sellers.isEmpty()) {
            root.addView(MerchantUi.emptyCard(context, "لسه ما في بيانات كفاية", "بعد أول مبيعات حتظهر المنتجات الأفضل هنا."))
        } else {
            m.best_sellers.forEachIndexed { index, item ->
                root.addView(MerchantUi.card(context).apply {
                    addView(LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL
                        addView(MerchantUi.pill(context, "${index + 1}", MerchantUi.Tone.BRAND))
                        addView(MerchantUi.text(context, item.name, 15.5f, true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = MerchantUi.dp(context, 10) })
                    })
                    addView(MerchantUi.muted(context, "${item.units} وحدة • ${money(item.revenue)} جنيه • ${item.views} مشاهدة", 12f).apply {
                        setPadding(0, MerchantUi.dp(context, 7), 0, 0)
                    })
                })
            }
        }
    }

    private fun addMetricPair(label1: String, value1: String, label2: String, value2: String) {
        val context = requireContext()
        root.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL
            addView(MerchantUi.metricCard(context, label1, value1), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = MerchantUi.dp(context, 4) })
            addView(MerchantUi.metricCard(context, label2, value2), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = MerchantUi.dp(context, 4) })
        })
    }

    private fun rateCard(label: String, value: Double, note: String) = MerchantUi.card(requireContext()).apply {
        val context = requireContext()
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = android.view.Gravity.CENTER_VERTICAL
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(MerchantUi.text(context, label, 14.5f, true))
                addView(MerchantUi.muted(context, note, 11.5f).apply { setPadding(0, MerchantUi.dp(context, 3), 0, 0) })
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(MerchantUi.text(context, "${money(value)}%", 21f, true))
        })
    }

    private fun showError(message: String) {
        val context = requireContext(); root.removeAllViews()
        root.addView(MerchantUi.title(context, "أداء المتجر"))
        root.addView(MerchantUi.card(context).apply {
            addView(MerchantUi.text(context, "تعذر تحميل المؤشرات", 16f, true))
            addView(MerchantUi.muted(context, message).apply { setPadding(0, MerchantUi.dp(context, 5), 0, MerchantUi.dp(context, 10)) })
            addView(MerchantUi.secondaryButton(context, "إعادة المحاولة") { load() })
        })
    }
    private fun money(value: Double) = if (value % 1.0 == 0.0) value.toInt().toString() else String.format(java.util.Locale.US, "%.2f", value)
}
