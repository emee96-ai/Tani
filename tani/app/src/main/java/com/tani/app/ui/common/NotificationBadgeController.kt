package com.tani.app.ui.common

import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.tani.app.MainActivity
import com.tani.app.R
import com.tani.app.data.Supabase
import com.tani.app.data.growth.NotificationItem
import com.tani.app.data.notifications.NotificationGatewayProvider
import com.tani.app.ui.auth.AuthFragment
import com.tani.app.ui.growth.NotificationsFragment
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.WeakHashMap

/** Keeps the notification bell visible on every app screen and renders its unread badge. */
object NotificationBadgeController {
    private const val ACTION_TAG = "tani_notification_action"
    private const val BADGE_TAG = "tani_notification_badge"
    private const val REFRESH_THROTTLE_MS = 5_000L

    private val boundActivities = Collections.newSetFromMap(WeakHashMap<MainActivity, Boolean>())
    private val lastRefresh = WeakHashMap<MainActivity, Long>()

    fun bind(activity: MainActivity) {
        ensureActionView(activity)
        val firstBind = synchronized(boundActivities) { boundActivities.add(activity) }
        if (firstBind) {
            activity.supportFragmentManager.registerFragmentLifecycleCallbacks(
                object : FragmentManager.FragmentLifecycleCallbacks() {
                    override fun onFragmentResumed(fm: FragmentManager, fragment: Fragment) {
                        ensureActionView(activity)
                        refresh(activity, force = false)
                    }
                },
                true
            )
        }
        refresh(activity, force = true)
    }

    fun refresh(activity: MainActivity, force: Boolean = true) {
        ensureActionView(activity)
        if (!Supabase.hasStoredSession()) {
            updateCount(activity, 0)
            return
        }

        val now = SystemClock.elapsedRealtime()
        val shouldLoad = synchronized(lastRefresh) {
            val last = lastRefresh[activity] ?: 0L
            if (!force && now - last < REFRESH_THROTTLE_MS) false
            else {
                lastRefresh[activity] = now
                true
            }
        }
        if (!shouldLoad) return

        activity.lifecycleScope.launch {
            runCatching { NotificationGatewayProvider.gateway.inbox() }
                .onSuccess { updateFromItems(activity, it) }
        }
    }

    fun updateFromItems(activity: MainActivity, items: List<NotificationItem>) {
        updateCount(activity, items.count { it.read_at == null })
    }

    fun updateCount(activity: MainActivity, unread: Int) {
        activity.runOnUiThread {
            ensureActionView(activity)
            val item = activity.findViewById<MaterialToolbar>(R.id.top_app_bar)
                ?.menu
                ?.findItem(R.id.action_notifications)
                ?: return@runOnUiThread
            val badge = item.actionView?.findViewWithTag<TextView>(BADGE_TAG) ?: return@runOnUiThread
            if (unread <= 0) {
                badge.visibility = View.GONE
                badge.text = ""
            } else {
                badge.visibility = View.VISIBLE
                badge.text = if (unread > 99) "99+" else unread.toString()
                badge.contentDescription = "$unread إشعار غير مقروء"
            }
        }
    }

    private fun ensureActionView(activity: MainActivity) {
        val toolbar = activity.findViewById<MaterialToolbar>(R.id.top_app_bar) ?: return
        val item = toolbar.menu.findItem(R.id.action_notifications) ?: return
        item.isVisible = true
        if (item.actionView?.tag == ACTION_TAG) return

        val density = activity.resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        val root = FrameLayout(activity).apply {
            tag = ACTION_TAG
            layoutParams = FrameLayout.LayoutParams(dp(52), dp(48))
            isClickable = true
            isFocusable = true
            contentDescription = "الإشعارات"
            setOnClickListener {
                if (Supabase.hasStoredSession()) activity.show(NotificationsFragment())
                else activity.show(AuthFragment())
            }
        }

        root.addView(
            ImageView(activity).apply {
                setImageResource(R.drawable.ic_notifications)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                contentDescription = null
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                setPadding(dp(12), dp(12), dp(12), dp(12))
            },
            FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER)
        )

        val badgeBackground = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.rgb(214, 45, 45))
            cornerRadius = dp(20).toFloat()
        }
        root.addView(
            TextView(activity).apply {
                tag = BADGE_TAG
                visibility = View.GONE
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                textSize = 9.5f
                setPadding(dp(4), 0, dp(4), 0)
                minWidth = dp(18)
                background = badgeBackground
            },
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, dp(18), Gravity.TOP or Gravity.END).apply {
                topMargin = dp(3)
                marginEnd = dp(2)
            }
        )

        item.actionView = root
    }
}

/** Auto-initializes the notification chrome without coupling every screen to MainActivity. */
class NotificationBadgeInitProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        val app = context?.applicationContext as? Application ?: return true
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                (activity as? MainActivity)?.let(NotificationBadgeController::bind)
            }

            override fun onActivityResumed(activity: Activity) {
                (activity as? MainActivity)?.let { NotificationBadgeController.refresh(it, force = true) }
            }

            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
