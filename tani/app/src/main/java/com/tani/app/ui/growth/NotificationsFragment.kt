package com.tani.app.ui.growth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.tani.app.R
import com.tani.app.data.growth.NotificationPreferences
import com.tani.app.data.notifications.NotificationGatewayProvider
import com.tani.app.ui.common.ScreenUi
import kotlinx.coroutines.launch

class NotificationsFragment : Fragment() {
    private val gateway get() = NotificationGatewayProvider.gateway
    private lateinit var root: LinearLayout
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val c=requireContext(); root=ScreenUi.root(c)
        return ScrollView(c).apply { setBackgroundColor(c.getColor(R.color.tani_background)); addView(root) }
    }
    override fun onViewCreated(view: View, state: Bundle?) { load() }
    private fun load() {
        val c=requireContext(); root.removeAllViews(); root.addView(ScreenUi.title(c,"الإشعارات")); root.addView(ScreenUi.subtitle(c,"تحديثات الطلبات والتاجر والمخزون تصل أولاً إلى Inbox داخل تاني، ويمكن ربط Push provider لاحقاً دون تغيير الواجهة."))
        lifecycleScope.launch {
            runCatching { gateway.preferences() to gateway.inbox() }.onSuccess { (prefs, items) ->
                val settings=ScreenUi.card(c); settings.addView(ScreenUi.text(c,"التفضيلات",17f,true))
                val push=CheckBox(c).apply{text="تشغيل الإشعارات";isChecked=prefs.push_enabled}
                val orders=CheckBox(c).apply{text="تحديثات الطلبات";isChecked=prefs.order_updates}
                val promos=CheckBox(c).apply{text="العروض والحملات";isChecked=prefs.promotions}
                val messages=CheckBox(c).apply{text="الرسائل والدعم";isChecked=prefs.messages}
                settings.addView(push);settings.addView(orders);settings.addView(promos);settings.addView(messages)
                settings.addView(ScreenUi.button(c,"حفظ التفضيلات") {
                    lifecycleScope.launch {
                        runCatching { gateway.savePreferences(NotificationPreferences(prefs.user_id,push.isChecked,orders.isChecked,promos.isChecked,messages.isChecked)) }
                            .onSuccess { Toast.makeText(c,"تم الحفظ",Toast.LENGTH_SHORT).show() }
                            .onFailure { Toast.makeText(c,it.message?:"تعذر الحفظ",Toast.LENGTH_LONG).show() }
                    }
                }); root.addView(settings)
                root.addView(ScreenUi.text(c,"Inbox",19f,true))
                if(items.isEmpty()) root.addView(ScreenUi.muted(c,"لا توجد إشعارات حالياً."))
                items.forEach { n -> root.addView(ScreenUi.card(c).apply {
                    addView(ScreenUi.text(c,n.title,16f,true));addView(ScreenUi.text(c,n.body,14f));addView(ScreenUi.muted(c,n.created_at?:""))
                    if(n.read_at==null) addView(ScreenUi.button(c,"تحديد كمقروء") { lifecycleScope.launch { runCatching { gateway.markRead(n.id) }; load() } })
                }) }
            }.onFailure { root.addView(ScreenUi.muted(c,it.message?:"تعذر تحميل الإشعارات")) }
        }
    }
}
