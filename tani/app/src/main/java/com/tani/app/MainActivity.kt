package com.tani.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.tani.app.data.Supabase
import com.tani.app.data.Cart
import com.tani.app.data.Analytics
import com.tani.app.data.ErrorMonitoring
import com.tani.app.ui.auth.AuthFragment
import com.tani.app.ui.cart.CartFragment
import com.tani.app.ui.categories.CategoriesFragment
import com.tani.app.ui.home.HomeFragment
import com.tani.app.ui.orders.OrdersFragment
import com.tani.app.ui.products.ProductsFragment
import com.tani.app.ui.profile.ProfileFragment
import java.net.URLDecoder
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    lateinit var nav: BottomNavigationView

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

        if (!handleAuthDeepLink(intent)) {
            if (Supabase.hasStoredSession()) {
                showApp()
            } else {
                // جلسة قديمة تحفظ access token فقط لا يمكن تجديدها بأمان.
                Supabase.clearSession()
                showAuth()
            }
        }
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
        show(AuthFragment(), addToBackStack = false)
    }

    fun showApp() {
        nav.visibility = View.VISIBLE
        showPrimary(HomeFragment())
    }

    fun show(fragment: Fragment, addToBackStack: Boolean = true) {
        val screen = fragment.javaClass.simpleName
            .removeSuffix("Fragment")
            .replace(Regex("([a-z])([A-Z])"), "\$1_\$2")
            .lowercase()
        lifecycleScope.launch { Analytics.track("screen_view", screen = screen) }

        val transaction = supportFragmentManager
            .beginTransaction()
            .replace(R.id.nav_host, fragment)
        if (addToBackStack) transaction.addToBackStack(screen)
        transaction.commit()
    }

    private fun showPrimary(fragment: Fragment) {
        supportFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        show(fragment, addToBackStack = false)
    }
}
