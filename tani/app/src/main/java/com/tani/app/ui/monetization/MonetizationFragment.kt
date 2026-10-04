package com.tani.app.ui.monetization

import com.tani.app.data.network.CustomerErrorMessages

import com.tani.app.util.runCatchingCancellable

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.tani.app.R
import com.tani.app.data.repository.MonetizationRepository
import com.tani.app.ui.seller.MerchantUi
import kotlinx.coroutines.launch

class MonetizationFragment : Fragment() {
    private val repository = MonetizationRepository()
    private lateinit var root: LinearLayout

    override fun onCreateView(inflater: android.view.LayoutInflater, container: android.view.ViewGroup?, state: Bundle?): View {
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

    override fun onViewCreated(view: View, state: Bundle?) { load() }

    private fun load() {
        val sellerId = requireArguments().getString(ARG_SELLER) ?: return
        val context = requireContext()
        root.removeAllViews()
        root.addView(MerchantUi.title(context, getString(R.string.monetization_1)))
        root.addView(MerchantUi.subtitle(context, getString(R.string.monetization_2)))
        root.addView(MerchantUi.card(context, soft = true).apply {
            addView(MerchantUi.text(context, getString(R.string.monetization_3), 14f, true))
        })

        viewLifecycleOwner.lifecycleScope.launch {
            runCatchingCancellable {
                Triple(repository.plans(), repository.mySubscriptions(), repository.featuredRequests(sellerId))
            }.onSuccess { (plans, subscriptions, featured) ->
                root.removeAllViews()
                root.addView(MerchantUi.title(context, getString(R.string.monetization_1)))
                root.addView(MerchantUi.subtitle(context, getString(R.string.monetization_4)))

                root.addView(MerchantUi.sectionTitle(context, getString(R.string.monetization_5)))
                if (plans.isEmpty()) {
                    root.addView(MerchantUi.emptyCard(context, getString(R.string.monetization_6), getString(R.string.monetization_7)))
                } else {
                    plans.forEach { plan ->
                        root.addView(MerchantUi.card(context).apply {
                            addView(LinearLayout(context).apply {
                                orientation = LinearLayout.HORIZONTAL
                                layoutDirection = View.LAYOUT_DIRECTION_RTL
                                addView(MerchantUi.text(context, plan.name, 17f, true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                                addView(MerchantUi.pill(context, getString(R.string.monetization_8, (money(plan.price))), MerchantUi.Tone.BRAND))
                            })
                            plan.description.takeIf { it.isNotBlank() }?.let {
                                addView(MerchantUi.muted(context, it, 12.5f).apply { setPadding(0, MerchantUi.dp(context, 6), 0, 0) })
                            }
                            addView(MerchantUi.muted(context, getString(R.string.monetization_9, plan.duration_days), 12f).apply {
                                setPadding(0, MerchantUi.dp(context, 5), 0, MerchantUi.dp(context, 8))
                            })
                            addView(MerchantUi.primaryButton(context, getString(R.string.monetization_10)) {
                                viewLifecycleOwner.lifecycleScope.launch {
                                    runCatchingCancellable { repository.requestSubscription(sellerId, plan.id, "") }
                                        .onSuccess { Toast.makeText(context, getString(R.string.monetization_11), Toast.LENGTH_SHORT).show(); load() }
                                        .onFailure { Toast.makeText(context, CustomerErrorMessages.from(it, getString(R.string.monetization_12)), Toast.LENGTH_LONG).show() }
                                }
                            })
                        })
                    }
                }

                root.addView(MerchantUi.sectionTitle(context, getString(R.string.monetization_13)))
                if (subscriptions.isEmpty()) {
                    root.addView(MerchantUi.emptyCard(context, getString(R.string.monetization_14), getString(R.string.monetization_15)))
                } else {
                    subscriptions.take(10).forEach { item ->
                        root.addView(MerchantUi.card(context).apply {
                            addView(LinearLayout(context).apply {
                                orientation = LinearLayout.HORIZONTAL
                                layoutDirection = View.LAYOUT_DIRECTION_RTL
                                addView(MerchantUi.text(context, getString(R.string.monetization_16), 15f, true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                                addView(MerchantUi.pill(context, statusLabel(item.status), statusTone(item.status)))
                            })
                            addView(MerchantUi.muted(context, "${dateLabel(item.starts_at)} → ${item.ends_at?.let(::dateLabel) ?: "بدون تاريخ نهاية"}", 12f).apply {
                                setPadding(0, MerchantUi.dp(context, 6), 0, 0)
                            })
                        })
                    }
                }

                root.addView(MerchantUi.sectionTitle(context, getString(R.string.monetization_17)))
                root.addView(MerchantUi.card(context, soft = true).apply {
                    addView(MerchantUi.text(context, getString(R.string.monetization_18), 16f, true))
                    addView(MerchantUi.muted(context, getString(R.string.monetization_19), 12.5f).apply {
                        setPadding(0, MerchantUi.dp(context, 5), 0, MerchantUi.dp(context, 9))
                    })
                    addView(MerchantUi.primaryButton(context, getString(R.string.monetization_20)) {
                        viewLifecycleOwner.lifecycleScope.launch {
                            runCatchingCancellable { repository.requestFeatured(sellerId = sellerId, productId = null, placement = "home", note = getString(R.string.monetization_21)) }
                                .onSuccess { Toast.makeText(context, getString(R.string.monetization_22), Toast.LENGTH_LONG).show(); load() }
                                .onFailure { Toast.makeText(context, CustomerErrorMessages.from(it, getString(R.string.monetization_12)), Toast.LENGTH_LONG).show() }
                        }
                    })
                })

                if (featured.isNotEmpty()) {
                    root.addView(MerchantUi.text(context, getString(R.string.monetization_23), 14.5f, true).apply { setPadding(0, 0, 0, MerchantUi.dp(context, 7)) })
                    featured.take(10).forEach { item ->
                        root.addView(MerchantUi.card(context).apply {
                            addView(LinearLayout(context).apply {
                                orientation = LinearLayout.HORIZONTAL
                                layoutDirection = View.LAYOUT_DIRECTION_RTL
                                addView(MerchantUi.text(context, placementLabel(item.placement), 14f, true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                                addView(MerchantUi.pill(context, statusLabel(item.status), statusTone(item.status)))
                            })
                            item.created_at?.let { addView(MerchantUi.muted(context, dateLabel(it), 11.5f).apply { setPadding(0, MerchantUi.dp(context, 5), 0, 0) }) }
                        })
                    }
                }

                root.addView(MerchantUi.sectionTitle(context, getString(R.string.monetization_24)))
                root.addView(MerchantUi.card(context).apply {
                    addView(MerchantUi.text(context, getString(R.string.monetization_25), 16f, true))
                    addView(MerchantUi.muted(context, getString(R.string.monetization_26), 12.5f).apply {
                        setPadding(0, MerchantUi.dp(context, 5), 0, MerchantUi.dp(context, 9))
                    })
                    addView(MerchantUi.secondaryButton(context, getString(R.string.monetization_27)) {
                        viewLifecycleOwner.lifecycleScope.launch {
                            runCatchingCancellable { repository.createAdCampaign(sellerId = sellerId, name = getString(R.string.monetization_28), placement = "home", productId = null, budget = null) }
                                .onSuccess { Toast.makeText(context, getString(R.string.monetization_29), Toast.LENGTH_SHORT).show() }
                                .onFailure { Toast.makeText(context, CustomerErrorMessages.from(it, getString(R.string.monetization_30)), Toast.LENGTH_LONG).show() }
                        }
                    })
                })
            }.onFailure {
                root.removeAllViews()
                root.addView(MerchantUi.title(context, getString(R.string.monetization_1)))
                root.addView(MerchantUi.card(context).apply {
                    addView(MerchantUi.text(context, getString(R.string.monetization_31), 16f, true))
                    addView(MerchantUi.muted(context, CustomerErrorMessages.from(it, getString(R.string.merchant_insights_27))).apply { setPadding(0, MerchantUi.dp(context, 5), 0, 0) })
                })
            }
        }
    }

    private fun statusLabel(status: String): String = when (status.lowercase()) {
        "pending" -> getString(R.string.monetization_32)
        "active", "approved" -> getString(R.string.monetization_33)
        "rejected" -> getString(R.string.monetization_34)
        "cancelled", "canceled" -> getString(R.string.monetization_35)
        "expired" -> getString(R.string.monetization_36)
        else -> status
    }

    private fun statusTone(status: String): MerchantUi.Tone = when (status.lowercase()) {
        "active", "approved" -> MerchantUi.Tone.SUCCESS
        "pending" -> MerchantUi.Tone.WARNING
        "rejected", "cancelled", "canceled", "expired" -> MerchantUi.Tone.ERROR
        else -> MerchantUi.Tone.NEUTRAL
    }

    private fun placementLabel(placement: String): String = when (placement.lowercase()) {
        "home" -> getString(R.string.monetization_37)
        "category" -> getString(R.string.monetization_38)
        "search" -> getString(R.string.monetization_39)
        else -> placement
    }

    private fun dateLabel(value: String): String = value.take(10)
    private fun money(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else String.format(java.util.Locale.US, "%.2f", value)

    companion object {
        private const val ARG_SELLER = "seller_id"
        fun newInstance(id: String) = MonetizationFragment().apply { arguments = Bundle().apply { putString(ARG_SELLER, id) } }
    }
}
