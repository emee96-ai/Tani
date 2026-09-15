package com.tani.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationView
import com.tani.app.data.Analytics
import com.tani.app.data.AuthRedirect
import com.tani.app.data.Cart
import com.tani.app.data.ErrorMonitoring
import com.tani.app.data.Supabase
import com.tani.app.ui.auth.AuthFragment
import com.tani.app.ui.cart.CartFragment
import com.tani.app.ui.categories.CategoriesFragment
import com.tani.app.ui.growth.FavoritesFragment
import com.tani.app.ui.growth.NotificationsFragment
import com.tani.app.ui.home.HomeFragment
import com.tani.app.ui.legal.AboutFragment
import com.tani.app.ui.marketplace.StoresFragment
import com.tani.app.ui.orders.OrdersFragment
import com.tani.app.ui.profile.ProfileFragment
import java.net.URLDecoder
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    lateinit var nav: BottomNavigationView
    private lateinit var toolbar: MaterialToolbar
    private lateinit var drawer: DrawerLayout
    private lateinit var drawerNav: NavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Tani)
        super.onCreate(savedInstanceState)
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

        val launchSplash = findViewById<View>(R.id.launch_splash)
        launchSplash.alpha = 0f
        launchSplash.scaleX = 0.94f
        launchSplash.scaleY = 0.94f
        launchSplash.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(480L)
            .withEndAction {
                launchSplash.postDelayed({
                    launchSplash.animate()
                        .alpha(0f)
                        .scaleX(1.03f)
                        .scaleY(1.03f)
                        .setDuration(260L)
                        .withEndAction { launchSplash.visibility = View.GONE }
                        .start()
                }, 620L)
            }
            .start()

        toolbar = findViewById(R.id.top_app_bar)
        toolbar.inflateMenu(R.menu.top_app_bar)
        toolbar.setNavigationOnClickListener {
            if (supportFragmentManager.backStackEntryCount > 0) {
                onBackPressedDispatcher.onBackPressed()
            } else {
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
        nav.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.home -> showPrimary(HomeFragment())
                R.id.categories -> showPrimary(CategoriesFragment())
                R.id.cart -> showPrimary(CartFragment())
                R.id.orders -> showProtected(OrdersFragment())
                R.id.profile -> showProtected(ProfileFragment())
            }
            true
        }
        setupDrawerNavigation()
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
            if (Supabase.hasStoredSession()) {
                showApp()
            } else {
                Supabase.clearSession()
                showApp()
            }
        }
    }

    private fun setupDrawerNavigation() {
        drawerNav.setNavigationItemSelectedListener { item ->
            drawer.closeDrawer(GravityCompat.START)
            when (item.itemId) {
                R.id.drawer_home -> showPrimary(HomeFragment())
                R.id.drawer_categories -> showPrimary(CategoriesFragment())
                R.id.drawer_stores -> show(StoresFragment())
                R.id.drawer_favorites -> showProtectedSecondary(FavoritesFragment())
                R.id.drawer_cart -> showPrimary(CartFragment())
                R.id.drawer_orders -> showProtected(OrdersFragment())
                R.id.drawer_notifications -> showProtectedSecondary(NotificationsFragment())
                R.id.drawer_about -> show(AboutFragment())
                R.id.drawer_profile -> showProtected(ProfileFragment())
                else -> return@setNavigationItemSelectedListener false
            }
            true
        }
    }

    override fun onResume() {
        super.onResume()
        if (::nav.isInitialized) refreshCartBadge()
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
        drawer.closeDrawer(GravityCompat.START)
        setDrawerEnabled(false)
        nav.visibility = View.GONE
        toolbar.visibility = View.GONE
        show(AuthFragment(), addToBackStack = false)
    }

    fun showApp() {
        showPrimary(HomeFragment())
    }

    fun show(fragment: Fragment, addToBackStack: Boolean = true) {
        val screen = fragment.javaClass.simpleName
            .removeSuffix("Fragment")
            .replace(Regex("([a-z])([A-Z])"), "$1_$2")
            .lowercase()
        lifecycleScope.launch { Analytics.track("screen_view", screen = screen) }

        if (addToBackStack) {
            drawer.closeDrawer(GravityCompat.START)
            setDrawerEnabled(false)
            nav.visibility = View.GONE
            toolbar.visibility = View.VISIBLE
            toolbar.setNavigationIcon(R.drawable.ic_back)
            toolbar.logo = null
            toolbar.title = screenTitle(fragment)
        }

        val transaction = supportFragmentManager
            .beginTransaction()
            .replace(R.id.nav_host, fragment)
        if (addToBackStack) transaction.addToBackStack(screen)
        transaction.commit()
    }

    private fun showPrimary(fragment: Fragment) {
        supportFragmentManager.popBackStackImmediate(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
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
        show(fragment, addToBackStack = false)
    }

    private fun showProtected(fragment: Fragment) {
        if (Supabase.hasStoredSession()) {
            showPrimary(fragment)
        } else {
            show(AuthFragment())
        }
    }

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
        val item = when (fragment.javaClass.simpleName) {
            "HomeFragment" -> R.id.drawer_home
            "CategoriesFragment" -> R.id.drawer_categories
            "CartFragment" -> R.id.drawer_cart
            "OrdersFragment" -> R.id.drawer_orders
            "ProfileFragment" -> R.id.drawer_profile
            else -> return
        }
        drawerNav.setCheckedItem(item)
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
        val current = supportFragmentManager.findFragmentById(R.id.nav_host)
        if (count <= 0) {
            if (current is AuthFragment && !Supabase.hasStoredSession()) {
                setDrawerEnabled(false)
                toolbar.visibility = View.GONE
                nav.visibility = View.GONE
                return
            }
            setDrawerEnabled(true)
            toolbar.visibility = View.VISIBLE
            toolbar.setNavigationIcon(R.drawable.ic_menu)
            if (current is HomeFragment) {
                toolbar.logo = getDrawable(R.drawable.ic_brand)
                toolbar.title = "تاني"
            } else {
                toolbar.logo = null
                toolbar.title = current?.let(::screenTitle) ?: "تاني"
            }
            nav.visibility = View.VISIBLE
            current?.let(::updateDrawerSelection)
            refreshCartBadge()
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
        "ProfileFragment" -> "حسابي"
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
}
