package com.tani.app.ui.growth

import com.tani.app.data.network.CustomerErrorMessages

import com.tani.app.util.runCatchingCancellable

import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.tani.app.MainActivity
import com.tani.app.R
import com.tani.app.data.Repository
import com.tani.app.data.cache.AppContentStore
import com.tani.app.data.growth.NotificationItem
import com.tani.app.data.growth.NotificationPreferences
import com.tani.app.data.notifications.NotificationGatewayProvider
import com.tani.app.ui.commerce.CommerceUi
import com.tani.app.ui.commerce.OrderDetailsFragment
import com.tani.app.ui.common.NotificationBadgeController
import com.tani.app.ui.common.ScreenUi
import com.tani.app.ui.orders.OrdersFragment
import com.tani.app.ui.seller.MerchantOrdersFragment
import com.tani.app.ui.seller.MerchantProductsFragment
import com.tani.app.ui.seller.SellerFragment
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

class NotificationsFragment : Fragment() {
    private val gateway get() = NotificationGatewayProvider.gateway
    private lateinit var root: LinearLayout
    private var items: List<NotificationItem> = emptyList()
    private var preferences: NotificationPreferences? = null
    private var filter = Filter.ALL
    private var audience = Audience.CUSTOMER
    private var hasMerchantAccount = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val context = requireContext()
        root = ScreenUi.root(context)
        return ScrollView(context).apply {
            setBackgroundColor(context.getColor(R.color.tani_background))
            addView(root)
        }
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        load()
    }

    private fun load(forceRefresh: Boolean = false) {
        val context = requireContext()
        val notificationsReady = !forceRefresh && AppContentStore.notificationsLoaded
        val merchantReady = !forceRefresh && AppContentStore.merchantLoaded

        if (notificationsReady && merchantReady) {
            preferences = AppContentStore.notificationPreferences
            items = AppContentStore.notifications
            hasMerchantAccount = AppContentStore.merchant != null
            if (!hasMerchantAccount) audience = Audience.CUSTOMER
            syncBadge()
            render()
            return
        }

        root.removeAllViews()
        root.addView(ScreenUi.title(context, getString(R.string.main_24)))
        root.addView(ScreenUi.subtitle(context, getString(R.string.notifications_1)))
        root.addView(ScreenUi.muted(context, getString(R.string.notifications_2)))

        viewLifecycleOwner.lifecycleScope.launch {
            val notificationsResult = runCatchingCancellable {
                if (notificationsReady) {
                    AppContentStore.notificationPreferences to AppContentStore.notifications
                } else {
                    gateway.preferences() to gateway.inbox()
                }
            }

            notificationsResult
                .onSuccess { (prefs, loadedItems) ->
                    preferences = prefs
                    items = loadedItems
                    AppContentStore.updateNotifications(loadedItems, prefs)

                    val merchant = if (merchantReady) {
                        AppContentStore.merchant
                    } else {
                        runCatchingCancellable { Repository().merchantProfile() }.getOrNull()
                    }
                    AppContentStore.updateMerchant(merchant)
                    hasMerchantAccount = merchant != null
                    if (!hasMerchantAccount) audience = Audience.CUSTOMER

                    syncBadge()
                    render()
                }
                .onFailure {
                    root.removeAllViews()
                    root.addView(ScreenUi.title(context, getString(R.string.main_24)))
                    root.addView(ScreenUi.subtitle(context, getString(R.string.notifications_3)))
                    root.addView(ScreenUi.muted(context, CustomerErrorMessages.from(it, getString(R.string.notifications_4))))
                    root.addView(ScreenUi.button(context, getString(R.string.notifications_5)) { load(forceRefresh = true) })
                }
        }
    }

    private fun render() {
        val context = requireContext()
        val customerItems = items.filterNot(::isMerchantNotification)
        val merchantItems = items.filter(::isMerchantNotification)
        val customerUnread = customerItems.count { it.read_at == null }
        val merchantUnread = merchantItems.count { it.read_at == null }
        val sectionItems = currentAudienceItems(customerItems, merchantItems)
        val unread = sectionItems.count { it.read_at == null }

        root.removeAllViews()
        root.addView(ScreenUi.title(context, getString(R.string.main_24)))
        root.addView(
            ScreenUi.subtitle(
                context,
                when {
                    hasMerchantAccount -> getString(R.string.notifications_6)
                    unread > 0 -> getString(R.string.notifications_7, unread)
                    else -> getString(R.string.notifications_8)
                }
            )
        )

        if (hasMerchantAccount) {
            root.addView(audienceTabs(customerUnread, merchantUnread))
        }

        val summary = ScreenUi.card(context)
        summary.addView(
            ScreenUi.text(
                context,
                if (hasMerchantAccount && audience == Audience.MERCHANT) getString(R.string.notifications_9) else getString(R.string.notifications_10),
                18f,
                true
            )
        )
        summary.addView(
            ScreenUi.muted(
                context,
                when {
                    sectionItems.isEmpty() -> getString(R.string.notifications_11)
                    unread > 0 -> getString(R.string.notifications_12, sectionItems.size, unread)
                    else -> getString(R.string.notifications_13, sectionItems.size)
                }
            )
        )

        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, ScreenUi.dp(context, 10), 0, 0)
        }
        val allButton = filterButton(getString(R.string.notifications_14, sectionItems.size), filter == Filter.ALL) {
            filter = Filter.ALL
            render()
        }
        val unreadButton = filterButton(getString(R.string.notifications_15, unread), filter == Filter.UNREAD) {
            filter = Filter.UNREAD
            render()
        }
        actions.addView(allButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = ScreenUi.dp(context, 6)
        })
        actions.addView(unreadButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = ScreenUi.dp(context, 6)
        })
        summary.addView(actions)

        if (unread > 0) {
            summary.addView(ScreenUi.button(context, getString(R.string.notifications_16)) { markAudienceRead(sectionItems) })
        }
        summary.addView(ScreenUi.button(context, getString(R.string.notifications_17)) { load(forceRefresh = true) })
        root.addView(summary)

        val shown = when (filter) {
            Filter.ALL -> sectionItems
            Filter.UNREAD -> sectionItems.filter { it.read_at == null }
        }

        if (shown.isEmpty()) {
            val empty = ScreenUi.card(context)
            empty.addView(
                ScreenUi.text(
                    context,
                    if (filter == Filter.UNREAD) getString(R.string.notifications_18) else getString(R.string.notifications_19),
                    17f,
                    true
                )
            )
            empty.addView(
                ScreenUi.muted(
                    context,
                    when {
                        filter == Filter.UNREAD -> getString(R.string.notifications_20)
                        hasMerchantAccount && audience == Audience.MERCHANT -> getString(R.string.notifications_21)
                        else -> getString(R.string.notifications_22)
                    }
                )
            )
            root.addView(empty)
        } else {
            shown.forEach { notification -> root.addView(notificationCard(notification)) }
        }

        preferences?.let { root.addView(preferencesCard(it)) }
    }

    private fun audienceTabs(customerUnread: Int, merchantUnread: Int): View {
        val context = requireContext()
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, ScreenUi.dp(context, 12), 0, ScreenUi.dp(context, 4))

            addView(
                audienceButton(getString(R.string.notifications_23), customerUnread, audience == Audience.CUSTOMER) {
                    audience = Audience.CUSTOMER
                    filter = Filter.ALL
                    render()
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = ScreenUi.dp(context, 6)
                }
            )
            addView(
                audienceButton(getString(R.string.notifications_24), merchantUnread, audience == Audience.MERCHANT) {
                    audience = Audience.MERCHANT
                    filter = Filter.ALL
                    render()
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = ScreenUi.dp(context, 6)
                }
            )
        }
    }

    private fun audienceButton(label: String, unread: Int, selected: Boolean, onClick: () -> Unit): Button =
        Button(requireContext()).apply {
            text = if (unread > 0) "$label ($unread)" else label
            isAllCaps = false
            minHeight = ScreenUi.dp(requireContext(), 52)
            textSize = 15f
            alpha = if (selected) 1f else 0.66f
            setTypeface(typeface, if (selected) Typeface.BOLD else Typeface.NORMAL)
            setOnClickListener { onClick() }
        }

    private fun currentAudienceItems(
        customerItems: List<NotificationItem> = items.filterNot(::isMerchantNotification),
        merchantItems: List<NotificationItem> = items.filter(::isMerchantNotification)
    ): List<NotificationItem> =
        if (hasMerchantAccount && audience == Audience.MERCHANT) merchantItems else customerItems

    private fun isMerchantNotification(notification: NotificationItem): Boolean =
        notification.type.lowercase() in MERCHANT_NOTIFICATION_TYPES

    private fun notificationCard(notification: NotificationItem): View {
        val context = requireContext()
        val card = ScreenUi.card(context).apply {
            isClickable = true
            isFocusable = true
            contentDescription = "${notification.title}. ${notification.body}"
            setOnClickListener { openNotification(notification) }
        }
        val isUnread = notification.read_at == null

        val top = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val title = TextView(context).apply {
            text = notification.title
            textSize = 16f
            setTextColor(context.getColor(R.color.text_dark))
            setTypeface(typeface, Typeface.BOLD)
        }
        top.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (isUnread) {
            top.addView(TextView(context).apply {
                text = getString(R.string.notifications_25)
                textSize = 12f
                setTextColor(context.getColor(R.color.success))
                setTypeface(typeface, Typeface.BOLD)
                setPadding(ScreenUi.dp(context, 8), ScreenUi.dp(context, 4), ScreenUi.dp(context, 8), ScreenUi.dp(context, 4))
                background = context.getDrawable(R.drawable.bg_chip)
            })
        }
        card.addView(top)

        card.addView(ScreenUi.muted(context, typeLabel(notification.type)))
        card.addView(ScreenUi.text(context, notification.body, 14f))
        val date = CommerceUi.formatDate(notification.created_at)
        if (date.isNotBlank()) card.addView(ScreenUi.muted(context, date))

        if (hasDestination(notification)) {
            card.addView(ScreenUi.muted(context, getString(R.string.notifications_26)))
        }
        if (isUnread) {
            card.addView(ScreenUi.button(context, getString(R.string.notifications_27)) { markRead(notification.id) })
        }
        return card
    }

    private fun preferencesCard(prefs: NotificationPreferences): View {
        val context = requireContext()
        val card = ScreenUi.card(context)
        card.addView(ScreenUi.text(context, getString(R.string.notifications_28), 18f, true))
        card.addView(ScreenUi.muted(context, getString(R.string.notifications_29)))

        val enabled = CheckBox(context).apply {
            text = getString(R.string.notifications_30)
            isChecked = prefs.push_enabled
        }
        val orders = CheckBox(context).apply {
            text = getString(R.string.notifications_31)
            isChecked = prefs.order_updates
        }
        val promos = CheckBox(context).apply {
            text = getString(R.string.notifications_32)
            isChecked = prefs.promotions
        }
        val messages = CheckBox(context).apply {
            text = getString(R.string.notifications_33)
            isChecked = prefs.messages
        }
        card.addView(enabled)
        card.addView(orders)
        card.addView(promos)
        card.addView(messages)
        card.addView(ScreenUi.button(context, getString(R.string.notifications_34)) {
            savePreferences(prefs, enabled, orders, promos, messages)
        })
        return card
    }

    private fun savePreferences(
        prefs: NotificationPreferences,
        enabled: CheckBox,
        orders: CheckBox,
        promos: CheckBox,
        messages: CheckBox
    ) {
        val context = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            runCatchingCancellable {
                gateway.savePreferences(
                    NotificationPreferences(
                        user_id = prefs.user_id,
                        push_enabled = enabled.isChecked,
                        order_updates = orders.isChecked,
                        promotions = promos.isChecked,
                        messages = messages.isChecked
                    )
                )
            }.onSuccess {
                preferences = it
                AppContentStore.updateNotifications(items, it)
                Toast.makeText(context, getString(R.string.notifications_35), Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, CustomerErrorMessages.from(it, getString(R.string.notifications_36)), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun openNotification(notification: NotificationItem) {
        val main = activity as? MainActivity ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            if (notification.read_at == null) {
                runCatchingCancellable { gateway.markRead(notification.id) }
                    .onSuccess {
                        items = items.map { item ->
                            if (item.id == notification.id) item.copy(read_at = "read") else item
                        }
                        AppContentStore.updateNotifications(items, preferences)
                        syncBadge()
                    }
                    .onFailure {
                        Toast.makeText(requireContext(), CustomerErrorMessages.from(it, getString(R.string.notifications_37)), Toast.LENGTH_LONG).show()
                    }
            }
            openDestination(main, notification)
        }
    }

    private fun openDestination(main: MainActivity, notification: NotificationItem) {
        when (notification.type.lowercase()) {
            "new_order", "merchant_order_update" -> main.show(MerchantOrdersFragment())
            "stock_alert", "stock", "inventory" -> main.show(MerchantProductsFragment())
            "merchant_verification", "merchant", "seller" -> main.show(SellerFragment())
            "order", "order_update", "order_status" -> {
                val groupId = notification.data["order_group_id"]?.jsonPrimitive?.contentOrNull
                if (groupId.isNullOrBlank()) main.show(OrdersFragment())
                else main.show(OrderDetailsFragment.newInstance(groupId))
            }
        }
    }

    private fun hasDestination(notification: NotificationItem): Boolean =
        notification.type.lowercase() in DESTINATION_NOTIFICATION_TYPES

    private fun markRead(id: String) {
        val context = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            runCatchingCancellable { gateway.markRead(id) }
                .onSuccess {
                    items = items.map { item -> if (item.id == id) item.copy(read_at = "read") else item }
                    AppContentStore.updateNotifications(items, preferences)
                    syncBadge()
                    render()
                }
                .onFailure {
                    Toast.makeText(context, CustomerErrorMessages.from(it, getString(R.string.notifications_37)), Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun markAudienceRead(sectionItems: List<NotificationItem>) {
        val context = requireContext()
        val unreadIds = sectionItems.filter { it.read_at == null }.map { it.id }
        if (unreadIds.isEmpty()) return

        viewLifecycleOwner.lifecycleScope.launch {
            runCatchingCancellable {
                if (!hasMerchantAccount) {
                    gateway.markAllRead()
                } else {
                    unreadIds.forEach { gateway.markRead(it) }
                }
            }.onSuccess {
                val ids = unreadIds.toSet()
                items = items.map { item -> if (item.id in ids) item.copy(read_at = item.read_at ?: "read") else item }
                AppContentStore.updateNotifications(items, preferences)
                syncBadge()
                Toast.makeText(context, getString(R.string.notifications_38), Toast.LENGTH_SHORT).show()
                render()
            }.onFailure {
                Toast.makeText(context, CustomerErrorMessages.from(it, getString(R.string.notifications_39)), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun syncBadge() {
        (activity as? MainActivity)?.let { NotificationBadgeController.updateFromItems(it, items) }
    }

    private fun filterButton(label: String, selected: Boolean, onClick: () -> Unit): Button =
        Button(requireContext()).apply {
            text = label
            isAllCaps = false
            minHeight = ScreenUi.dp(requireContext(), 46)
            alpha = if (selected) 1f else 0.72f
            setOnClickListener { onClick() }
        }

    private fun typeLabel(type: String): String = when (type.lowercase()) {
        "new_order" -> getString(R.string.notifications_40)
        "merchant_order_update" -> getString(R.string.notifications_41)
        "order", "order_update", "order_status" -> getString(R.string.notifications_42)
        "promotion", "promo", "campaign" -> getString(R.string.notifications_43)
        "message", "support" -> getString(R.string.notifications_44)
        "stock_alert", "stock", "inventory" -> getString(R.string.notifications_45)
        "merchant_verification" -> getString(R.string.notifications_46)
        "merchant", "seller" -> getString(R.string.notifications_47)
        else -> getString(R.string.notifications_48)
    }

    private enum class Filter { ALL, UNREAD }
    private enum class Audience { CUSTOMER, MERCHANT }

    private companion object {
        val MERCHANT_NOTIFICATION_TYPES = setOf(
            "new_order",
            "merchant_order_update",
            "stock_alert",
            "stock",
            "inventory",
            "merchant_verification",
            "merchant",
            "seller"
        )

        val DESTINATION_NOTIFICATION_TYPES = MERCHANT_NOTIFICATION_TYPES + setOf(
            "order",
            "order_update",
            "order_status"
        )
    }
}
