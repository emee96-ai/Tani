package com.tani.app.ui.seller

import com.tani.app.data.network.CustomerErrorMessages

import com.tani.app.util.runCatchingCancellable

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
        root.addView(MerchantUi.title(context, getString(R.string.merchant_analytics_1)))
        root.addView(MerchantUi.card(context, soft = true).apply { addView(MerchantUi.text(context, getString(R.string.merchant_analytics_2), 14f, true)) })
        viewLifecycleOwner.lifecycleScope.launch {
            runCatchingCancellable { repository.merchantDashboardSummary() }
                .onSuccess(::render)
                .onFailure { showError(CustomerErrorMessages.from(it, getString(R.string.merchant_insights_26))) }
        }
    }

    private fun render(m: MerchantDashboardSummary) {
        val context = requireContext(); root.removeAllViews()
        root.addView(MerchantUi.title(context, getString(R.string.merchant_analytics_1)))
        root.addView(MerchantUi.subtitle(context, getString(R.string.merchant_analytics_3)))

        root.addView(MerchantUi.sectionTitle(context, getString(R.string.merchant_analytics_4)))
        addMetricPair(getString(R.string.merchant_insights_6), m.orders.toString(), getString(R.string.merchant_analytics_5), m.delivered_orders.toString())
        root.addView(MerchantUi.metricCard(context, getString(R.string.merchant_analytics_6), getString(R.string.monetization_8, (money(m.gmv))), getString(R.string.merchant_analytics_7)))

        root.addView(MerchantUi.sectionTitle(context, getString(R.string.merchant_analytics_8)))
        addMetricPair(getString(R.string.merchant_analytics_9), m.customers.toString(), getString(R.string.merchant_insights_12), m.repeat_customers.toString())
        root.addView(MerchantUi.metricCard(context, getString(R.string.merchant_insights_11), m.product_views.toString()))

        root.addView(MerchantUi.sectionTitle(context, getString(R.string.merchant_insights_13)))
        root.addView(rateCard(getString(R.string.merchant_insights_14), m.conversion_rate, getString(R.string.merchant_analytics_10)))
        root.addView(rateCard(getString(R.string.merchant_analytics_11), m.completion_rate, getString(R.string.merchant_analytics_12)))
        root.addView(rateCard(getString(R.string.merchant_insights_16), m.cancellation_rate, getString(R.string.merchant_analytics_13)))
        root.addView(MerchantUi.card(context).apply {
            addView(MerchantUi.text(context, getString(R.string.merchant_insights_18), 14.5f, true))
            addView(MerchantUi.text(context, if (m.average_rating > 0) "${money(m.average_rating)} / 5" else "—", 21f, true).apply {
                setPadding(0, MerchantUi.dp(context, 5), 0, 0)
            })
        })
        root.addView(MerchantUi.card(context).apply {
            addView(MerchantUi.text(context, getString(R.string.merchant_analytics_14), 14.5f, true))
            addView(MerchantUi.text(context, m.average_response_seconds?.let { getString(R.string.checkout_46, ((it / 60.0).roundToInt())) } ?: "—", 21f, true).apply {
                setPadding(0, MerchantUi.dp(context, 5), 0, 0)
            })
        })

        root.addView(MerchantUi.sectionTitle(context, getString(R.string.main_16)))
        root.addView(MerchantUi.metricCard(context, getString(R.string.merchant_analytics_15), getString(R.string.merchant_analytics_16, m.active_products, m.products)))
        root.addView(MerchantUi.sectionTitle(context, getString(R.string.merchant_analytics_17)))
        if (m.best_sellers.isEmpty()) {
            root.addView(MerchantUi.emptyCard(context, getString(R.string.merchant_analytics_18), getString(R.string.merchant_analytics_19)))
        } else {
            m.best_sellers.forEachIndexed { index, item ->
                root.addView(MerchantUi.card(context).apply {
                    addView(LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL
                        addView(MerchantUi.pill(context, "${index + 1}", MerchantUi.Tone.BRAND))
                        addView(MerchantUi.text(context, item.name, 15.5f, true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = MerchantUi.dp(context, 10) })
                    })
                    addView(MerchantUi.muted(context, getString(R.string.merchant_analytics_20, item.units, (money(item.revenue)), item.views), 12f).apply {
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
        root.addView(MerchantUi.title(context, getString(R.string.merchant_analytics_1)))
        root.addView(MerchantUi.card(context).apply {
            addView(MerchantUi.text(context, getString(R.string.merchant_insights_26), 16f, true))
            addView(MerchantUi.muted(context, message).apply { setPadding(0, MerchantUi.dp(context, 5), 0, MerchantUi.dp(context, 10)) })
            addView(MerchantUi.secondaryButton(context, getString(R.string.notifications_5)) { load() })
        })
    }
    private fun money(value: Double) = if (value % 1.0 == 0.0) value.toInt().toString() else String.format(java.util.Locale.US, "%.2f", value)
}
