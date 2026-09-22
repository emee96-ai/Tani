package com.tani.app.ui.growth

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
        if (!forceRefresh && AppContentStore.notificationsLoaded) {
            preferences = AppContentStore.notificationPreferences
            items = AppContentStore.notifications
            syncBadge()
            render()
            return
        }
        root.removeAllViews()
        root.addView(ScreenUi.title(context, "الإشعارات"))
        root.addView(ScreenUi.subtitle(context, "كل تحديثات طلباتك ورسائلك المهمة في مكان واحد."))
        root.addView(ScreenUi.muted(context, "جاري تحميل الإشعارات..."))

        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { gateway.preferences() to gateway.inbox() }
                .onSuccess { (prefs, loadedItems) ->
                    preferences = prefs
                    items = loadedItems
                    AppContentStore.updateNotifications(loadedItems, prefs)
                    syncBadge()
                    render()
                }
                .onFailure {
                    root.removeAllViews()
                    root.addView(ScreenUi.title(context, "الإشعارات"))
                    root.addView(ScreenUi.subtitle(context, "تعذر تحميل الإشعارات حالياً."))
                    root.addView(ScreenUi.muted(context, it.message ?: "حاولي مرة أخرى"))
                    root.addView(ScreenUi.button(context, "إعادة المحاولة") { load() })
                }
        }
    }

    private fun render() {
        val context = requireContext()
        val unread = items.count { it.read_at == null }
        root.removeAllViews()
        root.addView(ScreenUi.title(context, "الإشعارات"))
        root.addView(
            ScreenUi.subtitle(
                context,
                if (unread > 0) "عندك $unread إشعار غير مقروء" else "كل الإشعارات مقروءة"
            )
        )

        val summary = ScreenUi.card(context)
        summary.addView(ScreenUi.text(context, "صندوق الإشعارات", 18f, true))
        summary.addView(
            ScreenUi.muted(
                context,
                if (items.isEmpty()) "ما عندك إشعارات حتى الآن."
                else "${items.size} إشعار • $unread غير مقروء"
            )
        )

        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, ScreenUi.dp(context, 10), 0, 0)
        }
        val allButton = filterButton("الكل (${items.size})", filter == Filter.ALL) {
            filter = Filter.ALL
            render()
        }
        val unreadButton = filterButton("غير المقروء ($unread)", filter == Filter.UNREAD) {
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
            summary.addView(ScreenUi.button(context, "تحديد الكل كمقروء") { markAllRead() })
        }
        summary.addView(ScreenUi.button(context, "تحديث الإشعارات") { load(forceRefresh = true) })
        root.addView(summary)

        val shown = when (filter) {
            Filter.ALL -> items
            Filter.UNREAD -> items.filter { it.read_at == null }
        }

        if (shown.isEmpty()) {
            val empty = ScreenUi.card(context)
            empty.addView(
                ScreenUi.text(
                    context,
                    if (filter == Filter.UNREAD) "ما عندك إشعارات جديدة 🎉" else "لا توجد إشعارات حالياً",
                    17f,
                    true
                )
            )
            empty.addView(
                ScreenUi.muted(
                    context,
                    if (filter == Filter.UNREAD) "أي إشعار جديد حيظهر هنا مباشرة."
                    else "تحديثات الطلبات والرسائل المهمة حتظهر هنا."
                )
            )
            root.addView(empty)
        } else {
            shown.forEach { notification -> root.addView(notificationCard(notification)) }
        }

        preferences?.let { root.addView(preferencesCard(it)) }
    }

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
                text = "جديد"
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
            card.addView(ScreenUi.muted(context, "اضغطي على الإشعار لفتح التفاصيل"))
        }
        if (isUnread) {
            card.addView(ScreenUi.button(context, "تحديد كمقروء") { markRead(notification.id) })
        }
        return card
    }

    private fun preferencesCard(prefs: NotificationPreferences): View {
        val context = requireContext()
        val card = ScreenUi.card(context)
        card.addView(ScreenUi.text(context, "إعدادات الإشعارات", 18f, true))
        card.addView(ScreenUi.muted(context, "اختاري أنواع التنبيهات التي تريدين استقبالها."))

        val enabled = CheckBox(context).apply {
            text = "تشغيل الإشعارات"
            isChecked = prefs.push_enabled
        }
        val orders = CheckBox(context).apply {
            text = "تحديثات الطلبات"
            isChecked = prefs.order_updates
        }
        val promos = CheckBox(context).apply {
            text = "العروض والحملات"
            isChecked = prefs.promotions
        }
        val messages = CheckBox(context).apply {
            text = "الرسائل والدعم"
            isChecked = prefs.messages
        }
        card.addView(enabled)
        card.addView(orders)
        card.addView(promos)
        card.addView(messages)
        card.addView(ScreenUi.button(context, "حفظ إعدادات الإشعارات") {
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
            runCatching {
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
                Toast.makeText(context, "تم حفظ إعدادات الإشعارات", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, it.message ?: "تعذر حفظ الإعدادات", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun openNotification(notification: NotificationItem) {
        val main = activity as? MainActivity ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            if (notification.read_at == null) {
                runCatching { gateway.markRead(notification.id) }
                    .onSuccess {
                        items = items.map { item ->
                            if (item.id == notification.id) item.copy(read_at = "read") else item
                        }
                        AppContentStore.updateNotifications(items, preferences)
                        syncBadge()
                    }
                    .onFailure {
                        Toast.makeText(requireContext(), it.message ?: "تعذر تحديث الإشعار", Toast.LENGTH_LONG).show()
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
        notification.type.lowercase() in setOf(
            "new_order",
            "merchant_order_update",
            "stock_alert",
            "stock",
            "inventory",
            "merchant_verification",
            "merchant",
            "seller",
            "order",
            "order_update",
            "order_status"
        )

    private fun markRead(id: String) {
        val context = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { gateway.markRead(id) }
                .onSuccess {
                    items = items.map { item -> if (item.id == id) item.copy(read_at = "read") else item }
                    AppContentStore.updateNotifications(items, preferences)
                    syncBadge()
                    render()
                }
                .onFailure {
                    Toast.makeText(context, it.message ?: "تعذر تحديث الإشعار", Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun markAllRead() {
        val context = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { gateway.markAllRead() }
                .onSuccess {
                    items = items.map { it.copy(read_at = it.read_at ?: "read") }
                    AppContentStore.updateNotifications(items, preferences)
                    syncBadge()
                    Toast.makeText(context, "تم تحديد كل الإشعارات كمقروءة", Toast.LENGTH_SHORT).show()
                    render()
                }
                .onFailure {
                    Toast.makeText(context, it.message ?: "تعذر تحديث الإشعارات", Toast.LENGTH_LONG).show()
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
        "new_order" -> "طلب جديد للمتجر"
        "merchant_order_update" -> "تحديث طلب للمتجر"
        "order", "order_update", "order_status" -> "تحديث طلب"
        "promotion", "promo", "campaign" -> "عرض وحملة"
        "message", "support" -> "رسالة ودعم"
        "stock_alert", "stock", "inventory" -> "تنبيه مخزون"
        "merchant_verification" -> "تحديث حساب التاجر"
        "merchant", "seller" -> "تحديث متجر"
        else -> "إشعار من تاني"
    }

    private enum class Filter { ALL, UNREAD }
}
