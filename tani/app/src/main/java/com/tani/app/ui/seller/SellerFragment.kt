package com.tani.app.ui.seller

import com.tani.app.util.runCatchingCancellable

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
        root.addView(MerchantUi.title(context, getString(R.string.seller_1)))
        root.addView(MerchantUi.card(context, soft = true).apply {
            addView(MerchantUi.text(context, getString(R.string.seller_2), 14f, true))
        })

        viewLifecycleOwner.lifecycleScope.launch {
            runCatchingCancellable {
                val profile = repository.merchantProfile()
                val seller = repository.seller()
                if (profile?.verification_status == "approved" && seller != null) {
                    coroutineScope {
                        val storeRequest = async { runCatchingCancellable { repository.merchantStore() }.getOrNull() }
                        val summaryRequest = async { runCatchingCancellable { repository.merchantDashboardSummary() }.getOrNull() }
                        val zonesRequest = async { runCatchingCancellable { repository.merchantDeliveryZones(seller.id) }.getOrDefault(emptyList()) }
                        val deliveryRequest = async { runCatchingCancellable { repository.merchantDeliverySettings(seller.id) }.getOrNull() }
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
                showError(CustomerErrorMessages.from(error, getString(R.string.seller_3)))
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
        root.addView(MerchantUi.subtitle(context, getString(R.string.seller_4)))

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
            MerchantOperationalState.MISSING_STORE -> Triple(getString(R.string.seller_5), getString(R.string.seller_6), MerchantUi.Tone.WARNING)
            MerchantOperationalState.HIDDEN -> Triple(getString(R.string.seller_7), getString(R.string.seller_8), MerchantUi.Tone.WARNING)
            MerchantOperationalState.CLOSED -> Triple(getString(R.string.seller_9), getString(R.string.seller_10), MerchantUi.Tone.NEUTRAL)
            MerchantOperationalState.DELIVERY_UNCONFIGURED -> Triple(getString(R.string.seller_11), getString(R.string.seller_12), MerchantUi.Tone.WARNING)
            MerchantOperationalState.DELIVERY_PAUSED -> Triple(getString(R.string.seller_13), getString(R.string.seller_14), MerchantUi.Tone.NEUTRAL)
            MerchantOperationalState.NO_ACTIVE_DELIVERY_ZONES -> Triple(getString(R.string.seller_15), getString(R.string.seller_16), MerchantUi.Tone.WARNING)
            MerchantOperationalState.READY -> Triple(getString(R.string.seller_17), getString(R.string.merchant_orders_58), MerchantUi.Tone.SUCCESS)
        }

        root.addView(MerchantUi.card(context, soft = true).apply {
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(MerchantUi.text(context, storeStateTitle, 17f, true))
                    addView(MerchantUi.muted(context, if (profile.trust_badge) getString(R.string.seller_18) else getString(R.string.seller_19), 12f).apply {
                        setPadding(0, MerchantUi.dp(context, 4), 0, 0)
                    })
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(MerchantUi.pill(context, storePill, storeTone))
            })
            addView(MerchantUi.muted(
                context,
                "الظهور: ${if (store?.is_active == true) "ظاهر" else "غير ظاهر"} • " +
                    "التوصيل: ${if (delivery?.is_active == true) "متاح" else "متوقف"} • " +
                    getString(R.string.seller_20, data.activeDeliveryZoneCount, data.deliveryZoneCount),
                12f
            ).apply { setPadding(0, MerchantUi.dp(context, 7), 0, 0) })
            if (store != null && store.is_active) {
                addView(MerchantUi.compactButton(context, if (store.is_open) getString(R.string.seller_21) else getString(R.string.seller_22)) {
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
                addView(MerchantUi.text(context, getString(R.string.seller_23), 15.5f, true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(MerchantUi.pill(
                    context,
                    "${readiness.completed}/${readiness.total}",
                    if (readiness.completed == readiness.total) MerchantUi.Tone.SUCCESS else MerchantUi.Tone.BRAND
                ))
            })
            addView(MerchantUi.muted(
                context,
                if (readiness.completed == readiness.total) getString(R.string.seller_24)
                else getString(R.string.seller_25),
                12.5f
            ).apply { setPadding(0, MerchantUi.dp(context, 6), 0, 0) })
        })

        val setupIssues = buildList {
            if (store == null) add(getString(R.string.seller_26))
            if (store != null && !store.is_active) add(getString(R.string.seller_27))
            if (store?.logo_url.isNullOrBlank()) add(getString(R.string.seller_28))
            if (store?.cover_url.isNullOrBlank()) add(getString(R.string.seller_29))
            if (delivery == null) add(getString(R.string.seller_30))
            else if (!delivery.is_active) add(getString(R.string.seller_31))
            if (data.activeDeliveryZoneCount == 0) add(getString(R.string.merchant_delivery_34))
            if (summary != null && summary.active_products == 0) add(getString(R.string.seller_32))
        }
        if (setupIssues.isNotEmpty()) {
            root.addView(MerchantUi.sectionTitle(context, getString(R.string.seller_33)))
            root.addView(MerchantUi.card(context).apply {
                addView(MerchantUi.text(context, getString(R.string.seller_34), 15.5f, true))
                setupIssues.forEach { issue ->
                    addView(MerchantUi.muted(context, "• $issue", 13f).apply {
                        setPadding(0, MerchantUi.dp(context, 5), 0, 0)
                    })
                }
                if (store != null && (store.logo_url.isNullOrBlank() || store.cover_url.isNullOrBlank() || !store.is_active)) {
                    addView(MerchantUi.compactButton(context, getString(R.string.seller_35)) { open(MerchantStoreFragment()) })
                } else if (store == null) {
                    addView(MerchantUi.compactButton(context, getString(R.string.seller_36)) { load() })
                }
                if (delivery == null || !delivery.is_active || data.activeDeliveryZoneCount == 0) {
                    addView(MerchantUi.compactButton(context, getString(R.string.seller_37)) { open(MerchantDeliveryFragment()) })
                }
                if (summary != null && summary.active_products == 0) {
                    addView(MerchantUi.compactButton(context, getString(R.string.seller_38)) { open(MerchantProductsFragment()) })
                }
            })
        }

        root.addView(MerchantUi.sectionTitle(context, getString(R.string.seller_39)))
        if (summary != null) {
            val activeOrders = MerchantDashboardPolicy.activeOrderCount(summary.orders, summary.delivered_orders, summary.cancelled_orders)
            root.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                addView(MerchantUi.metricCard(context, getString(R.string.seller_40), summary.orders.toString(), getString(R.string.merchant_insights_6)), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = MerchantUi.dp(context, 4) })
                addView(MerchantUi.metricCard(context, getString(R.string.seller_41), getString(R.string.monetization_8, (money(summary.gmv))), getString(R.string.seller_42)), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = MerchantUi.dp(context, 4) })
            })
            root.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                addView(MerchantUi.metricCard(context, getString(R.string.seller_43), activeOrders.toString(), getString(R.string.seller_44)), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = MerchantUi.dp(context, 4) })
                addView(MerchantUi.metricCard(context, getString(R.string.seller_45), summary.active_products.toString(), getString(R.string.seller_46, summary.products)), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = MerchantUi.dp(context, 4) })
            })
            root.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                addView(MerchantUi.metricCard(context, getString(R.string.merchant_analytics_9), summary.customers.toString(), getString(R.string.seller_47)), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = MerchantUi.dp(context, 4) })
                addView(MerchantUi.metricCard(context, getString(R.string.seller_48), String.format(java.util.Locale.US, "%.1f", summary.average_rating), getString(R.string.seller_49)), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = MerchantUi.dp(context, 4) })
            })
        } else {
            root.addView(MerchantUi.card(context, soft = true).apply {
                addView(MerchantUi.muted(context, getString(R.string.seller_50), 13f))
                addView(MerchantUi.compactButton(context, getString(R.string.seller_51)) { load() })
            })
        }

        root.addView(MerchantUi.sectionTitle(context, getString(R.string.seller_52)))
        val activeOrdersBadge = summary?.let {
            MerchantDashboardPolicy.activeOrderCount(it.orders, it.delivered_orders, it.cancelled_orders)
                .takeIf { count -> count > 0 }
                ?.let { count -> getString(R.string.seller_53, count) }
        }
        root.addView(MerchantUi.navCard(context, "📦", getString(R.string.seller_40), getString(R.string.seller_54), activeOrdersBadge) {
            open(MerchantOrdersFragment())
        })
        root.addView(MerchantUi.navCard(context, "🏷️", getString(R.string.merchant_products_1), getString(R.string.seller_55)) {
            open(MerchantProductsFragment())
        })
        root.addView(MerchantUi.navCard(context, "🏪", getString(R.string.merchant_onboarding_22), getString(R.string.seller_56)) {
            open(MerchantStoreFragment())
        })
        root.addView(MerchantUi.navCard(context, "🛵", getString(R.string.order_details_4), getString(R.string.seller_57)) {
            open(MerchantDeliveryFragment())
        })

        root.addView(MerchantUi.sectionTitle(context, getString(R.string.seller_58)))
        root.addView(MerchantUi.navCard(context, "📊", getString(R.string.merchant_analytics_1), getString(R.string.seller_59)) {
            open(MerchantAnalyticsFragment())
        })
        root.addView(MerchantUi.navCard(context, "📈", getString(R.string.merchant_insights_1), getString(R.string.seller_60)) {
            open(MerchantInsightsFragment.newInstance(seller.id))
        })
        root.addView(MerchantUi.navCard(context, "✨", getString(R.string.monetization_1), getString(R.string.seller_61)) {
            open(MonetizationFragment.newInstance(seller.id))
        })
    }

    private fun toggleStoreOpen(store: MerchantStore) {
        if (storeToggleInFlight) return
        storeToggleInFlight = true
        val newOpen = !store.is_open
        Toast.makeText(requireContext(), if (newOpen) getString(R.string.seller_62) else getString(R.string.seller_63), Toast.LENGTH_SHORT).show()
        viewLifecycleOwner.lifecycleScope.launch {
            runCatchingCancellable { repository.setMerchantStoreOpen(store.id, newOpen) }
                .onSuccess {
                    storeToggleInFlight = false
                    Toast.makeText(requireContext(), if (newOpen) getString(R.string.seller_64) else getString(R.string.seller_65), Toast.LENGTH_SHORT).show()
                    load()
                }
                .onFailure { error ->
                    storeToggleInFlight = false
                    Toast.makeText(
                        requireContext(),
                        CustomerErrorMessages.from(error, getString(R.string.seller_66)),
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }

    private fun renderStatus(profile: MerchantProfile?) {
        val context = requireContext()
        root.removeAllViews()
        root.addView(MerchantUi.title(context, getString(R.string.main_8)))
        if (profile == null) {
            root.addView(MerchantUi.emptyCard(context, getString(R.string.seller_67), getString(R.string.seller_68)))
            root.addView(MerchantUi.primaryButton(context, getString(R.string.seller_69)) { open(MerchantOnboardingFragment()) })
            return
        }

        val (title, message, tone) = when (profile.verification_status) {
            "pending" -> Triple(getString(R.string.merchant_onboarding_2), getString(R.string.seller_70), MerchantUi.Tone.WARNING)
            "changes_requested" -> Triple(getString(R.string.seller_71), profile.review_note ?: getString(R.string.seller_72), MerchantUi.Tone.WARNING)
            "rejected" -> Triple(getString(R.string.seller_73), profile.review_note ?: getString(R.string.seller_74), MerchantUi.Tone.ERROR)
            "suspended" -> Triple(getString(R.string.seller_75), profile.review_note ?: getString(R.string.seller_76), MerchantUi.Tone.ERROR)
            else -> Triple(getString(R.string.merchant_onboarding_82), getString(R.string.seller_77), MerchantUi.Tone.NEUTRAL)
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
            root.addView(MerchantUi.primaryButton(context, getString(R.string.seller_78)) { open(MerchantOnboardingFragment()) })
        } else {
            root.addView(MerchantUi.secondaryButton(context, getString(R.string.merchant_onboarding_86)) { load() })
        }
    }

    private fun showError(message: String) {
        val context = requireContext()
        root.removeAllViews()
        root.addView(MerchantUi.title(context, getString(R.string.seller_1)))
        root.addView(MerchantUi.card(context).apply {
            addView(MerchantUi.text(context, getString(R.string.seller_79), 16f, true))
            addView(MerchantUi.muted(context, message).apply { setPadding(0, MerchantUi.dp(context, 5), 0, MerchantUi.dp(context, 10)) })
            addView(MerchantUi.secondaryButton(context, getString(R.string.notifications_5)) { load() })
        })
    }

    private fun open(fragment: Fragment) {
        (activity as? MainActivity)?.show(fragment)
    }

    private fun statusText(status: String): String = when (status) {
        "pending" -> getString(R.string.monetization_32)
        "approved" -> getString(R.string.merchant_onboarding_102)
        "changes_requested" -> getString(R.string.merchant_onboarding_103)
        "rejected" -> getString(R.string.monetization_34)
        "suspended" -> getString(R.string.merchant_onboarding_104)
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
