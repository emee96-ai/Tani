package com.tani.app.ui.growth

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.tani.app.R
import com.tani.app.data.repository.GrowthRepository
import com.tani.app.data.repository.ScaleRepository
import com.tani.app.ui.seller.MerchantUi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class MerchantInsightsFragment : Fragment() {
    private val growth = GrowthRepository()
    private val scale = ScaleRepository()
    private lateinit var root: LinearLayout

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        state: Bundle?
    ): View {
        val context = requireContext()
        root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(MerchantUi.dp(context, 16), MerchantUi.dp(context, 18), MerchantUi.dp(context, 16), MerchantUi.dp(context, 28))
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
        val sellerId = requireArguments().getString(ARG_SELLER) ?: return
        val context = requireContext()
        root.removeAllViews()
        root.addView(MerchantUi.title(context, "صحة المتجر"))
        root.addView(MerchantUi.subtitle(context, "المؤشرات التي تساعدك تعرفي أين يحتاج المتجر انتباهك."))
        root.addView(MerchantUi.card(context, soft = true).apply {
            addView(MerchantUi.text(context, "جاري تجهيز المؤشرات…", 14f, true))
        })

        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                coroutineScope {
                    async { growth.merchantMetrics(sellerId) }.await() to async { scale.inventoryHealth() }.await()
                }
            }.onSuccess { (metrics, health) ->
                root.removeAllViews()
                root.addView(MerchantUi.title(context, "صحة المتجر"))
                root.addView(MerchantUi.subtitle(context, "ركّزي على التنبيهات أولاً، وبعدها تابعي النمو."))

                root.addView(MerchantUi.sectionTitle(context, "الطلبات والمبيعات"))
                addMetricPair("كل الطلبات", (metrics?.total_orders ?: 0).toString(), "الطلبات المكتملة", (metrics?.completed_orders ?: 0).toString())
                addMetricPair("الطلبات الملغاة", (metrics?.cancelled_orders ?: 0).toString(), "المبيعات", money(metrics?.total_sales ?: 0.0) + " جنيه")
                addMetricPair("مشاهدات المنتجات", (metrics?.product_views ?: 0).toString(), "عملاء عادوا للشراء", (metrics?.repeat_customers ?: 0).toString())

                root.addView(MerchantUi.sectionTitle(context, "جودة الأداء"))
                root.addView(rateCard("معدل التحويل", metrics?.conversion_rate ?: 0.0, "من المشاهدة إلى طلب"))
                root.addView(rateCard("إلغاء الطلبات", metrics?.cancellation_rate ?: 0.0, "كلما قلّ كان أفضل"))
                root.addView(MerchantUi.card(context).apply {
                    addView(MerchantUi.text(context, "متوسط التقييم", 15f, true))
                    val rating = metrics?.average_rating ?: 0.0
                    addView(MerchantUi.text(context, if (rating > 0) "${money(rating)} / 5" else "—", 21f, true).apply {
                        setPadding(0, MerchantUi.dp(context, 5), 0, 0)
                    })
                })

                root.addView(MerchantUi.sectionTitle(context, "المخزون"))
                val alerts = health.out_of_stock + health.low_stock + health.inactive
                root.addView(MerchantUi.card(context, soft = alerts > 0).apply {
                    addView(LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        layoutDirection = View.LAYOUT_DIRECTION_RTL
                        addView(MerchantUi.text(context, "حالة المخزون", 16f, true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                        addView(MerchantUi.pill(context, if (alerts == 0) "ممتاز" else "$alerts تنبيه", if (alerts == 0) MerchantUi.Tone.SUCCESS else MerchantUi.Tone.WARNING))
                    })
                    addView(inventoryLine("نفد المخزون", health.out_of_stock, MerchantUi.Tone.ERROR))
                    addView(inventoryLine("مخزون منخفض (1–3)", health.low_stock, MerchantUi.Tone.WARNING))
                    addView(inventoryLine("منتجات متوقفة", health.inactive, MerchantUi.Tone.NEUTRAL))
                })
            }.onFailure {
                root.removeAllViews()
                root.addView(MerchantUi.title(context, "صحة المتجر"))
                root.addView(MerchantUi.card(context).apply {
                    addView(MerchantUi.text(context, "تعذر تحميل المؤشرات", 16f, true))
                    addView(MerchantUi.muted(context, it.message ?: "حاولي مرة أخرى لاحقاً").apply {
                        setPadding(0, MerchantUi.dp(context, 5), 0, 0)
                    })
                })
            }
        }
    }

    private fun addMetricPair(label1: String, value1: String, label2: String, value2: String) {
        val context = requireContext()
        root.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            addView(MerchantUi.metricCard(context, label1, value1), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = MerchantUi.dp(context, 4) })
            addView(MerchantUi.metricCard(context, label2, value2), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = MerchantUi.dp(context, 4) })
        })
    }

    private fun rateCard(label: String, rate: Double, note: String) = MerchantUi.card(requireContext()).apply {
        val context = requireContext()
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = android.view.Gravity.CENTER_VERTICAL
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(MerchantUi.text(context, label, 15f, true))
                addView(MerchantUi.muted(context, note, 12f).apply { setPadding(0, MerchantUi.dp(context, 3), 0, 0) })
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(MerchantUi.text(context, "${money(rate)}%", 20f, true))
        })
    }

    private fun inventoryLine(label: String, value: Int, tone: MerchantUi.Tone) = LinearLayout(requireContext()).apply {
        val context = requireContext()
        orientation = LinearLayout.HORIZONTAL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        gravity = android.view.Gravity.CENTER_VERTICAL
        setPadding(0, MerchantUi.dp(context, 10), 0, 0)
        addView(MerchantUi.text(context, label, 13.5f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(MerchantUi.pill(context, value.toString(), if (value == 0) MerchantUi.Tone.SUCCESS else tone))
    }

    private fun money(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else String.format(java.util.Locale.US, "%.2f", value)

    companion object {
        private const val ARG_SELLER = "seller_id"
        fun newInstance(id: String) = MerchantInsightsFragment().apply {
            arguments = Bundle().apply { putString(ARG_SELLER, id) }
        }
    }
}
