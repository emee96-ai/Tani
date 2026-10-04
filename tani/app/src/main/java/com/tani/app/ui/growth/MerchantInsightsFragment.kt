package com.tani.app.ui.growth

import com.tani.app.data.network.CustomerErrorMessages

import com.tani.app.util.runCatchingCancellable

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
        root.addView(MerchantUi.title(context, getString(R.string.merchant_insights_1)))
        root.addView(MerchantUi.subtitle(context, getString(R.string.merchant_insights_2)))
        root.addView(MerchantUi.card(context, soft = true).apply {
            addView(MerchantUi.text(context, getString(R.string.merchant_insights_3), 14f, true))
        })

        viewLifecycleOwner.lifecycleScope.launch {
            runCatchingCancellable {
                coroutineScope {
                    async { growth.merchantMetrics(sellerId) }.await() to async { scale.inventoryHealth() }.await()
                }
            }.onSuccess { (metrics, health) ->
                root.removeAllViews()
                root.addView(MerchantUi.title(context, getString(R.string.merchant_insights_1)))
                root.addView(MerchantUi.subtitle(context, getString(R.string.merchant_insights_4)))

                root.addView(MerchantUi.sectionTitle(context, getString(R.string.merchant_insights_5)))
                addMetricPair(getString(R.string.merchant_insights_6), (metrics?.total_orders ?: 0).toString(), getString(R.string.merchant_insights_7), (metrics?.completed_orders ?: 0).toString())
                addMetricPair(getString(R.string.merchant_insights_8), (metrics?.cancelled_orders ?: 0).toString(), getString(R.string.merchant_insights_9), money(metrics?.total_sales ?: 0.0) + getString(R.string.merchant_insights_10))
                addMetricPair(getString(R.string.merchant_insights_11), (metrics?.product_views ?: 0).toString(), getString(R.string.merchant_insights_12), (metrics?.repeat_customers ?: 0).toString())

                root.addView(MerchantUi.sectionTitle(context, getString(R.string.merchant_insights_13)))
                root.addView(rateCard(getString(R.string.merchant_insights_14), metrics?.conversion_rate ?: 0.0, getString(R.string.merchant_insights_15)))
                root.addView(rateCard(getString(R.string.merchant_insights_16), metrics?.cancellation_rate ?: 0.0, getString(R.string.merchant_insights_17)))
                root.addView(MerchantUi.card(context).apply {
                    addView(MerchantUi.text(context, getString(R.string.merchant_insights_18), 15f, true))
                    val rating = metrics?.average_rating ?: 0.0
                    addView(MerchantUi.text(context, if (rating > 0) "${money(rating)} / 5" else "—", 21f, true).apply {
                        setPadding(0, MerchantUi.dp(context, 5), 0, 0)
                    })
                })

                root.addView(MerchantUi.sectionTitle(context, getString(R.string.merchant_insights_19)))
                val alerts = health.out_of_stock + health.low_stock + health.inactive
                root.addView(MerchantUi.card(context, soft = alerts > 0).apply {
                    addView(LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        layoutDirection = View.LAYOUT_DIRECTION_RTL
                        addView(MerchantUi.text(context, getString(R.string.merchant_insights_20), 16f, true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                        addView(MerchantUi.pill(context, if (alerts == 0) getString(R.string.merchant_insights_21) else getString(R.string.merchant_insights_22, alerts.toString()), if (alerts == 0) MerchantUi.Tone.SUCCESS else MerchantUi.Tone.WARNING))
                    })
                    addView(inventoryLine(getString(R.string.merchant_insights_23), health.out_of_stock, MerchantUi.Tone.ERROR))
                    addView(inventoryLine(getString(R.string.merchant_insights_24), health.low_stock, MerchantUi.Tone.WARNING))
                    addView(inventoryLine(getString(R.string.merchant_insights_25), health.inactive, MerchantUi.Tone.NEUTRAL))
                })
            }.onFailure {
                root.removeAllViews()
                root.addView(MerchantUi.title(context, getString(R.string.merchant_insights_1)))
                root.addView(MerchantUi.card(context).apply {
                    addView(MerchantUi.text(context, getString(R.string.merchant_insights_26), 16f, true))
                    addView(MerchantUi.muted(context, CustomerErrorMessages.from(it, getString(R.string.merchant_insights_27))).apply {
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
