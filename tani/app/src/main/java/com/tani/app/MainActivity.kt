package com.tani.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.tani.app.data.Analytics
import com.tani.app.data.Cart
import com.tani.app.data.ErrorMonitoring
import com.tani.app.data.Supabase
import com.tani.app.ui.auth.AuthFragment
import com.tani.app.ui.cart.CartFragment
import com.tani.app.ui.categories.CategoriesFragment
import com.tani.app.ui.home.HomeFragment
import com.tani.app.ui.orders.OrdersFragment
import com.tani.app.ui.profile.ProfileFragment
import java.net.URLDecoder
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    lateinit var nav: BottomNavigationView
    private lateinit var toolbar: MaterialToolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Tani)
        super.onCreate(savedInstanceState)
        Supabase.init(this)
        Cart.init(this)
        ErrorMonitoring.init(this)
        lifecycleScope.launch {
            ErrorMonitoring.flushPendingCrash()
            Analytics.track("app_open", screen = "app")
        }
        setContentView(R.layout.activity_main)

        toolbar = findViewById(R.id.top_app_bar)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        nav = findViewById(R.id.bottom_nav)
        nav.menu.clear()
        nav.inflateMenu(R.menu.bottom_nav)
        nav.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.home -> showPrimary(HomeFragment())
                R.id.categories -> showPrimary(CategoriesFragment())
                R.id.cart -> showPrimary(CartFragment())
                R.id.orders -> showPrimary(OrdersFragment())
                R.id.profile -> showPrimary(ProfileFragment())
            }
            true
        }
        supportFragmentManager.addOnBackStackChangedListener { updateChromeFromBackStack() }
        refreshCartBadge()

        if (!handleAuthDeepLink(intent)) {
            if (Supabase.hasStoredSession()) {
                showApp()
            } else {
                Supabase.clearSession()
                showAuth()
            }
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
            uri.scheme == "tani" && uri.host == "auth" && uri.path == "/reset"
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

        nav.visibility = View.GONE
        toolbar.visibility = View.GONE
        show(
            if (type == null || type == "recovery") {
                AuthFragment.newResetInstance(token, refreshToken, error)
            } else {
                AuthFragment.newResetInstance(null, null, "رابط الاستعادة غير صالح")
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
        nav.visibility = View.GONE
        toolbar.visibility = View.GONE
        show(AuthFragment(), addToBackStack = false)
    }

    fun showApp() {
        nav.visibility = View.VISIBLE
        toolbar.visibility = View.GONE
        showPrimary(HomeFragment())
    }

    fun show(fragment: Fragment, addToBackStack: Boolean = true) {
        val screen = fragment.javaClass.simpleName
            .removeSuffix("Fragment")
            .replace(Regex("([a-z])([A-Z])"), "\$1_\$2")
            .lowercase()
        lifecycleScope.launch { Analytics.track("screen_view", screen = screen) }

        if (addToBackStack) {
            nav.visibility = View.GONE
            toolbar.visibility = View.VISIBLE
            toolbar.title = screenTitle(fragment)
        }

        val transaction = supportFragmentManager
            .beginTransaction()
            .replace(R.id.nav_host, fragment)
        if (addToBackStack) transaction.addToBackStack(screen)
        transaction.commit()
    }

    private fun showPrimary(fragment: Fragment) {
        supportFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        toolbar.visibility = View.GONE
        nav.visibility = View.VISIBLE
        refreshCartBadge()
        show(fragment, addToBackStack = false)
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
        if (count <= 0) {
            toolbar.visibility = View.GONE
            nav.visibility = if (Supabase.hasStoredSession()) View.VISIBLE else View.GONE
            refreshCartBadge()
            return
        }
        nav.visibility = View.GONE
        toolbar.visibility = View.VISIBLE
        supportFragmentManager.findFragmentById(R.id.nav_host)?.let { toolbar.title = screenTitle(it) }
    }

    private fun screenTitle(fragment: Fragment): String = when (fragment.javaClass.simpleName) {
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
        "SupportCenterFragment" -> "مركز المساعدة"
        else -> "تاني"
    }
}
