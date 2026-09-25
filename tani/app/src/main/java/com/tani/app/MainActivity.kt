package com.tani.app

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationView
import com.tani.app.data.Analytics
import com.tani.app.data.AuthRedirect
import com.tani.app.data.Cart
import com.tani.app.data.ErrorMonitoring
import com.tani.app.data.Repository
import com.tani.app.data.Supabase
import com.tani.app.data.cache.AppContentStore
import com.tani.app.ui.auth.AuthFragment
import com.tani.app.ui.cart.CartFragment
import com.tani.app.ui.growth.FavoritesFragment
import com.tani.app.ui.growth.NotificationsFragment
import com.tani.app.ui.home.HomeFragment
import com.tani.app.ui.legal.AboutFragment
import com.tani.app.ui.marketplace.StoresFragment
import com.tani.app.ui.orders.OrdersFragment
import com.tani.app.ui.profile.AccountFragment
import com.tani.app.ui.profile.ProfileFragment
import java.net.URL
import java.net.URLDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    lateinit var nav: BottomNavigationView
    private lateinit var toolbar: MaterialToolbar
    private lateinit var drawer: DrawerLayout
    private lateinit var drawerNav: NavigationView
    private lateinit var drawerAccountHeader: View
    private lateinit var drawerAccountAvatar: ImageView
    private lateinit var drawerAccountName: TextView
    private lateinit var drawerAccountEmail: TextView

    private var selectedPrimaryItemId: Int = R.id.home
    private var suppressNavCallback = false

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Tani)
        super.onCreate(savedInstanceState)
        selectedPrimaryItemId = savedInstanceState?.getInt(STATE_SELECTED_PRIMARY, R.id.home) ?: R.id.home

        Supabase.init(this)
        AuthRedirect.install()
        Cart.init(this)
        ErrorMonitoring.init(this)
        lifecycleScope.launch {
            ErrorMonitoring.flushPendingCrash()
            Analytics.track("app_open", screen = "app")
        }
        setContentView(R.layout.activity_main)

        drawer = findViewById(R.id.drawer_layout)
        drawerNav = findViewById(R.id.drawer_nav)
        setDrawerEnabled(false)
        AppContentStore.hydrateFromDisk(this)

        val launchSplash = findViewById<View>(R.id.launch_splash)
        launchSplash.alpha = 0f
        launchSplash.scaleX = 0.94f
        launchSplash.scaleY = 0.94f
        launchSplash.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(240L)
            .start()

        toolbar = findViewById(R.id.top_app_bar)
        toolbar.inflateMenu(R.menu.top_app_bar)
        toolbar.setNavigationOnClickListener {
            if (supportFragmentManager.backStackEntryCount > 0) {
                onBackPressedDispatcher.onBackPressed()
            } else {
                refreshDrawerAccount()
                drawer.openDrawer(GravityCompat.START)
            }
        }
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_notifications -> {
                    showProtectedSecondary(NotificationsFragment())
                    true
                }
                else -> false
            }
        }

        nav = findViewById(R.id.bottom_nav)
        nav.menu.clear()
        nav.inflateMenu(R.menu.bottom_nav)
        nav.setOnItemSelectedListener { item ->
            if (suppressNavCallback) return@setOnItemSelectedListener true
            openPrimary(item.itemId)
        }
        nav.setOnItemReselectedListener {
            // Intentionally keep the current fragment instance and its scroll/filter state.
        }

        setupDrawerNavigation()
        setupDrawerAccountHeader()
        supportFragmentManager.addOnBackStackChangedListener { updateChromeFromBackStack() }
        refreshCartBadge()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawer.isDrawerOpen(GravityCompat.START)) {
                    drawer.closeDrawer(GravityCompat.START)
                    return
                }
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        })

        if (!handleAuthDeepLink(intent)) {
            if (!Supabase.hasStoredSession()) Supabase.clearSession()
            startAppFast(savedInstanceState != null)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_SELECTED_PRIMARY, selectedPrimaryItemId)
        super.onSaveInstanceState(outState)
    }

    private fun startAppFast(restoringState: Boolean) {
        findViewById<TextView>(R.id.launch_loading_text)?.text = "فتح تاني…"

        if (restoringState && visibleContentFragment() != null) {
            syncBottomNavSelection(selectedPrimaryItemId)
            updateChromeFromBackStack()
            hideLaunchSplash()
        } else {
            showApp()
        }

        // Refresh cached public data after the UI is already usable. On a cold cache the
        // Home screen performs its own request, so avoid firing a duplicate startup fetch.
        if (AppContentStore.hasCoreContent()) {
            lifecycleScope.launch {
                runCatching { AppContentStore.warmUp(this@MainActivity) }
            }
        }
    }

    private fun canCommitNavigation(): Boolean =
        !isFinishing && !isDestroyed && !supportFragmentManager.isStateSaved

    private fun hideLaunchSplash() {
        val launchSplash = findViewById<View>(R.id.launch_splash)
        if (launchSplash.visibility != View.VISIBLE) return
        launchSplash.animate()
            .alpha(0f)
            .scaleX(1.02f)
            .scaleY(1.02f)
            .setDuration(180L)
            .withEndAction { launchSplash.visibility = View.GONE }
            .start()
    }

    private fun setupDrawerNavigation() {
        drawerNav.setNavigationItemSelectedListener { item ->
            drawer.closeDrawer(GravityCompat.START)
            when (item.itemId) {
                R.id.drawer_home -> openPrimary(R.id.home)
                R.id.drawer_stores -> openPrimary(R.id.categories)
                R.id.drawer_favorites -> {
                    showProtectedSecondary(FavoritesFragment())
                    true
                }
                R.id.drawer_cart -> openPrimary(R.id.cart)
                R.id.drawer_orders -> openPrimary(R.id.orders)
                R.id.drawer_notifications -> {
                    showProtectedSecondary(NotificationsFragment())
                    true
                }
                R.id.drawer_about -> {
                    show(AboutFragment())
                    true
                }
                else -> false
            }
        }
    }

    private fun setupDrawerAccountHeader() {
        drawerAccountHeader = drawerNav.getHeaderView(0)
        drawerAccountAvatar = drawerAccountHeader.findViewById(R.id.drawer_account_avatar)
        drawerAccountName = drawerAccountHeader.findViewById(R.id.drawer_account_name)
        drawerAccountEmail = drawerAccountHeader.findViewById(R.id.drawer_account_email)
        drawerAccountHeader.setOnClickListener {
            drawer.closeDrawer(GravityCompat.START)
            showProtectedSecondary(AccountFragment())
        }
        refreshDrawerAccount()
    }

    private fun refreshDrawerAccount() {
        if (!::drawerAccountHeader.isInitialized) return
        if (!Supabase.hasStoredSession()) {
            renderGuestDrawerAccount()
            return
        }

        AppContentStore.account?.let {
            renderDrawerAccount(it)
            return
        }

        lifecycleScope.launch {
            runCatching { Repository().accountProfile() }
                .onSuccess { account ->
                    AppContentStore.updateAccount(account)
                    renderDrawerAccount(account)
                }
                .onFailure { renderGuestDrawerAccount() }
        }
    }

    private fun renderDrawerAccount(account: com.tani.app.data.AccountProfile) {
        drawerAccountName.text = account.profile.name.ifBlank { "حسابي" }
        drawerAccountEmail.text = account.email.ifBlank { "بيانات الحساب" }
        val avatarUrl = account.profile.avatar_url
        if (avatarUrl.isNullOrBlank()) {
            renderDefaultDrawerAvatar()
            return
        }
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    URL(avatarUrl).openStream().use { BitmapFactory.decodeStream(it) }
                }.getOrNull()
            }
            if (bitmap != null) {
                drawerAccountAvatar.setPadding(0, 0, 0, 0)
                drawerAccountAvatar.setImageBitmap(bitmap)
            } else {
                renderDefaultDrawerAvatar()
            }
        }
    }

    private fun renderGuestDrawerAccount() {
        drawerAccountName.text = "حسابي"
        drawerAccountEmail.text = "سجلي الدخول لعرض بيانات الحساب"
        renderDefaultDrawerAvatar()
    }

    private fun renderDefaultDrawerAvatar() {
        val pad = (12 * resources.displayMetrics.density).toInt()
        drawerAccountAvatar.setPadding(pad, pad, pad, pad)
        drawerAccountAvatar.setImageResource(android.R.drawable.ic_menu_myplaces)
    }

    override fun onResume() {
        super.onResume()
        if (::nav.isInitialized) refreshCartBadge()
        if (::drawerAccountHeader.isInitialized) refreshDrawerAccount()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthDeepLink(intent)
    }

    private fun handleAuthDeepLink(intent: Intent?): Boolean {
        val uri = intent?.data ?: return false
        val isLegacyRecovery =
            BuildConfig.DEBUG && uri.scheme == "tani" && uri.host == "auth" && uri.path == "/reset"
        val isVerifiedRecovery =
            uri.scheme == "https" &&
                uri.host.equals(BuildConfig.APP_LINK_HOST, ignoreCase = true) &&
                uri.path == "/auth/reset"
        if (!isLegacyRecovery && !isVerifiedRecovery) return false

        val params = parseAuthParams(uri)
        val token = params["access_token"]
        val refreshToken = params["refresh_token"]
        val type = params["type"]
        val error = params["error_description"] ?: params["error"]

        if (error != null) {
            setDrawerEnabled(false)
            nav.visibility = View.GONE
            toolbar.visibility = View.GONE
            show(
                AuthFragment.newResetInstance(null, null, error),
                addToBackStack = false
            )
            hideLaunchSplash()
            return true
        }

        if (
            (type == "signup" || type == "email") &&
            !token.isNullOrBlank() &&
            !refreshToken.isNullOrBlank()
        ) {
            Supabase.saveSession(token, refreshToken, null)
            showApp()
            return true
        }

        setDrawerEnabled(false)
        nav.visibility = View.GONE
        toolbar.visibility = View.GONE
        show(
            if (type == null || type == "recovery") {
                AuthFragment.newResetInstance(token, refreshToken)
            } else {
                AuthFragment.newResetInstance(null, null, "رابط المصادقة غير صالح")
            },
            addToBackStack = false
        )
        hideLaunchSplash()
        return true
    }

    private fun parseAuthParams(uri: Uri): Map<String, String> {
        val raw = buildList {
            uri.query?.let(::add)
            uri.fragment?.let(::add)
        }.joinToString("&")

        if (raw.isBlank()) return emptyMap()
        return raw.split("&")
            .mapNotNull { pair ->
                val parts = pair.split("=", limit = 2)
                if (parts.isEmpty() || parts[0].isBlank()) null
                else parts[0] to URLDecoder.decode(parts.getOrElse(1) { "" }, "UTF-8")
            }
            .toMap()
    }

    fun showAuth() {
        AppContentStore.clearPrivateData()
        drawer.closeDrawer(GravityCompat.START)
        setDrawerEnabled(false)
        nav.visibility = View.GONE
        toolbar.visibility = View.GONE
        if (::drawerAccountHeader.isInitialized) renderGuestDrawerAccount()
        show(AuthFragment(), addToBackStack = false)
    }

    fun showApp() {
        if (!canCommitNavigation()) return
        showPrimary(R.id.home)
        refreshDrawerAccount()
        hideLaunchSplash()
    }

    fun show(fragment: Fragment, addToBackStack: Boolean = true) {
        if (!canCommitNavigation()) return
        val screen = fragment.javaClass.simpleName
            .removeSuffix("Fragment")
            .replace(Regex("([a-z])([A-Z])"), "$1_$2")
            .lowercase()
        lifecycleScope.launch { Analytics.track("screen_view", screen = screen) }

        if (!addToBackStack) {
            supportFragmentManager.popBackStackImmediate(
                null,
                FragmentManager.POP_BACK_STACK_INCLUSIVE
            )
            supportFragmentManager.beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.nav_host, fragment, "root_$screen")
                .setPrimaryNavigationFragment(fragment)
                .commit()
            return
        }

        drawer.closeDrawer(GravityCompat.START)
        setDrawerEnabled(false)
        nav.visibility = View.GONE
        toolbar.visibility = View.VISIBLE
        toolbar.setNavigationIcon(R.drawable.ic_back)
        toolbar.logo = null
        toolbar.title = screenTitle(fragment)

        val current = visibleContentFragment()
        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .apply {
                if (current != null) hide(current)
                add(R.id.nav_host, fragment, "secondary_${screen}_${System.nanoTime()}")
                setPrimaryNavigationFragment(fragment)
            }
            .addToBackStack(screen)
            .commit()
    }

    private fun openPrimary(itemId: Int): Boolean {
        val protected = itemId == R.id.orders || itemId == R.id.profile
        if (protected && !Supabase.hasStoredSession()) {
            show(AuthFragment())
            return false
        }
        showPrimary(itemId)
        return true
    }

    private fun showPrimary(itemId: Int) {
        if (!canCommitNavigation()) return

        supportFragmentManager.popBackStackImmediate(
            null,
            FragmentManager.POP_BACK_STACK_INCLUSIVE
        )

        val tag = primaryTag(itemId)
        val existing = supportFragmentManager.findFragmentByTag(tag)
        val target = existing ?: createPrimaryFragment(itemId)
        val transaction = supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)

        supportFragmentManager.fragments
            .filter { it.id == R.id.nav_host && it.isAdded }
            .forEach { fragment ->
                if (fragment === target) {
                    transaction.show(fragment)
                    transaction.setMaxLifecycle(fragment, Lifecycle.State.RESUMED)
                } else {
                    transaction.hide(fragment)
                    transaction.setMaxLifecycle(fragment, Lifecycle.State.STARTED)
                }
            }

        if (!target.isAdded) {
            transaction.add(R.id.nav_host, target, tag)
            transaction.setMaxLifecycle(target, Lifecycle.State.RESUMED)
        }
        transaction.setPrimaryNavigationFragment(target)
        transaction.commitNow()

        selectedPrimaryItemId = itemId
        syncBottomNavSelection(itemId)
        configurePrimaryChrome(target)
    }

    private fun configurePrimaryChrome(fragment: Fragment) {
        setDrawerEnabled(true)
        toolbar.visibility = View.VISIBLE
        toolbar.setNavigationIcon(R.drawable.ic_menu)
        if (fragment is HomeFragment) {
            toolbar.logo = getDrawable(R.drawable.ic_brand)
            toolbar.title = "تاني"
        } else {
            toolbar.logo = null
            toolbar.title = screenTitle(fragment)
        }
        nav.visibility = View.VISIBLE
        refreshCartBadge()
        updateDrawerSelection(fragment)
    }

    private fun createPrimaryFragment(itemId: Int): Fragment = when (itemId) {
        R.id.home -> HomeFragment()
        R.id.categories -> StoresFragment()
        R.id.cart -> CartFragment()
        R.id.orders -> OrdersFragment()
        R.id.profile -> ProfileFragment()
        else -> HomeFragment()
    }

    private fun primaryTag(itemId: Int): String = when (itemId) {
        R.id.home -> TAG_HOME
        R.id.categories -> TAG_STORES
        R.id.cart -> TAG_CART
        R.id.orders -> TAG_ORDERS
        R.id.profile -> TAG_PROFILE
        else -> TAG_HOME
    }

    private fun primaryItemId(fragment: Fragment?): Int? = when (fragment?.tag) {
        TAG_HOME -> R.id.home
        TAG_STORES -> R.id.categories
        TAG_CART -> R.id.cart
        TAG_ORDERS -> R.id.orders
        TAG_PROFILE -> R.id.profile
        else -> when (fragment) {
            is HomeFragment -> R.id.home
            is StoresFragment -> R.id.categories
            is CartFragment -> R.id.cart
            is OrdersFragment -> R.id.orders
            is ProfileFragment -> R.id.profile
            else -> null
        }
    }

    private fun syncBottomNavSelection(itemId: Int) {
        if (!::nav.isInitialized || nav.selectedItemId == itemId) return
        suppressNavCallback = true
        nav.selectedItemId = itemId
        suppressNavCallback = false
    }

    private fun visibleContentFragment(): Fragment? =
        supportFragmentManager.fragments.lastOrNull {
            it.id == R.id.nav_host && it.isAdded && !it.isHidden
        } ?: supportFragmentManager.primaryNavigationFragment

    private fun showProtectedSecondary(fragment: Fragment) {
        if (Supabase.hasStoredSession()) {
            show(fragment)
        } else {
            show(AuthFragment())
        }
    }

    private fun setDrawerEnabled(enabled: Boolean) {
        drawer.setDrawerLockMode(
            if (enabled) DrawerLayout.LOCK_MODE_UNLOCKED else DrawerLayout.LOCK_MODE_LOCKED_CLOSED,
            GravityCompat.START
        )
    }

    private fun updateDrawerSelection(fragment: Fragment) {
        drawerNav.menu.setGroupCheckable(0, true, true)
        val item = when (fragment.javaClass.simpleName) {
            "HomeFragment" -> R.id.drawer_home
            "StoresFragment" -> R.id.drawer_stores
            "CartFragment" -> R.id.drawer_cart
            "OrdersFragment" -> R.id.drawer_orders
            "ProfileFragment" -> null
            else -> null
        }
        if (item == null) {
            for (index in 0 until drawerNav.menu.size()) {
                drawerNav.menu.getItem(index).isChecked = false
            }
        } else {
            drawerNav.setCheckedItem(item)
        }
    }

    fun refreshCartBadge() {
        if (!::nav.isInitialized || nav.menu.findItem(R.id.cart) == null) return
        val count = Cart.itemCount()
        val badge = nav.getOrCreateBadge(R.id.cart)
        badge.isVisible = count > 0
        if (count > 0) badge.number = count.coerceAtMost(99)
    }

    private fun updateChromeFromBackStack() {
        val count = supportFragmentManager.backStackEntryCount
        val current = visibleContentFragment()

        if (count <= 0) {
            if (current is AuthFragment && !Supabase.hasStoredSession()) {
                setDrawerEnabled(false)
                toolbar.visibility = View.GONE
                nav.visibility = View.GONE
                return
            }

            val primaryId = primaryItemId(current)
            if (primaryId != null && current != null) {
                selectedPrimaryItemId = primaryId
                syncBottomNavSelection(primaryId)
                configurePrimaryChrome(current)
            } else {
                setDrawerEnabled(true)
                toolbar.visibility = View.VISIBLE
                toolbar.setNavigationIcon(R.drawable.ic_menu)
                toolbar.logo = null
                toolbar.title = current?.let(::screenTitle) ?: "تاني"
                nav.visibility = View.VISIBLE
                refreshCartBadge()
            }
            return
        }

        setDrawerEnabled(false)
        nav.visibility = View.GONE
        toolbar.visibility = View.VISIBLE
        toolbar.setNavigationIcon(R.drawable.ic_back)
        toolbar.logo = null
        current?.let { toolbar.title = screenTitle(it) }
    }

    private fun screenTitle(fragment: Fragment): String = when (fragment.javaClass.simpleName) {
        "HomeFragment" -> "تاني"
        "CategoriesFragment" -> "التصنيفات"
        "CartFragment" -> "السلة"
        "OrdersFragment" -> "طلباتي"
        "ProfileFragment" -> "الإعدادات"
        "AccountFragment" -> "حسابي"
        "ProductDetailsFragment" -> "تفاصيل المنتج"
        "SearchFragment" -> "البحث"
        "ProductsFragment" -> "المنتجات"
        "StoresFragment" -> "المتاجر"
        "StoreDetailsFragment" -> "المتجر"
        "CheckoutFragment" -> "إتمام الطلب"
        "AddressesFragment" -> "العناوين"
        "OrderDetailsFragment" -> "تفاصيل الطلب"
        "OrderConfirmationFragment" -> "تم الطلب"
        "FavoritesFragment" -> "المفضلة"
        "NotificationsFragment" -> "الإشعارات"
        "AboutFragment" -> "من نحن"
        "SupportCenterFragment" -> "مركز المساعدة"
        else -> "تاني"
    }

    companion object {
        private const val STATE_SELECTED_PRIMARY = "selected_primary"
        private const val TAG_HOME = "primary_home"
        private const val TAG_STORES = "primary_stores"
        private const val TAG_CART = "primary_cart"
        private const val TAG_ORDERS = "primary_orders"
        private const val TAG_PROFILE = "primary_profile"
    }
}
