package com.tani.app

import androidx.annotation.IdRes
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.tani.app.data.Supabase
import com.tani.app.data.cache.AppContentStore
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class MainActivitySmokeTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun guestLaunchShowsPrimaryNavigation() {
        waitForDisplayed(R.id.bottom_nav)
        onView(withId(R.id.home)).check(matches(isDisplayed()))
        onView(withId(R.id.categories)).check(matches(isDisplayed()))
        onView(withId(R.id.cart)).check(matches(isDisplayed()))
        onView(withId(R.id.orders)).check(matches(isDisplayed()))
        onView(withId(R.id.profile)).check(matches(isDisplayed()))
    }

    @Test
    fun guestCanNavigateStoresAndCart() {
        ensureGuestHome()

        onView(withId(R.id.categories)).perform(click())
        waitForDisplayed(R.id.stores_list)
        onView(withText("استكشفي المتاجر المحلية حسب الاسم أو المنطقة"))
            .check(matches(isDisplayed()))

        onView(withId(R.id.cart)).perform(click())
        waitForDisplayed(R.id.cart_box)
        onView(withText("سلة المشتريات")).check(matches(isDisplayed()))
    }

    @Test
    fun protectedOrdersRouteGuestToLoginAndGuestCanReturn() {
        openAuthAsGuest()
        onView(withText("مرحباً بعودتك")).check(matches(isDisplayed()))
        onView(withId(R.id.browse_as_guest)).perform(scrollTo()).check(matches(isDisplayed())).perform(click())

        waitForDisplayed(R.id.bottom_nav)
        onView(withId(R.id.home)).check(matches(isDisplayed()))
    }

    @Test
    fun loginRejectsMalformedEmailBeforeNetworkCall() {
        openAuthAsGuest()

        onView(withId(R.id.email)).perform(replaceText("not-an-email"))
        onView(withId(R.id.password)).perform(replaceText("Password123"))
        closeSoftKeyboard()
        onView(withId(R.id.action)).perform(scrollTo(), click())

        waitForText(R.id.status, "البريد الإلكتروني غير صحيح")
    }

    @Test
    fun signupRejectsPasswordMismatchBeforeNetworkCall() {
        openAuthAsGuest()
        onView(withId(R.id.toggle)).perform(scrollTo(), click())
        waitForText(R.id.auth_title, "إنشاء حساب")

        onView(withId(R.id.name)).perform(replaceText("إيمان"))
        onView(withId(R.id.phone)).perform(replaceText("0912345678"))
        onView(withId(R.id.email)).perform(replaceText("ui-test@example.com"))
        onView(withId(R.id.password)).perform(replaceText("Password123"))
        onView(withId(R.id.confirm_password)).perform(replaceText("Password456"))
        closeSoftKeyboard()
        onView(withId(R.id.action)).perform(scrollTo(), click())

        waitForText(R.id.status, "كلمتا المرور غير متطابقتين")
    }

    @Test
    fun forgotPasswordRejectsMalformedEmailBeforeNetworkCall() {
        openAuthAsGuest()
        onView(withId(R.id.forgot_password)).perform(scrollTo(), click())
        waitForText(R.id.auth_title, "نسيت كلمة المرور؟")

        onView(withId(R.id.email)).perform(replaceText("wrong"))
        closeSoftKeyboard()
        onView(withId(R.id.action)).perform(scrollTo(), click())

        waitForText(R.id.status, "البريد الإلكتروني غير صحيح")
    }

    private fun openAuthAsGuest() {
        ensureGuestHome()
        onView(withId(R.id.orders)).perform(click())
        waitForDisplayed(R.id.auth_title)
    }

    private fun ensureGuestHome() {
        waitForDisplayed(R.id.bottom_nav)
        activityRule.scenario.onActivity { activity ->
            Supabase.clearSession()
            AppContentStore.clearPrivateData()
            activity.showApp()
        }
        waitForDisplayed(R.id.bottom_nav)
    }

    private fun waitForDisplayed(@IdRes id: Int, timeoutMs: Long = 10_000L) {
        val deadline = System.currentTimeMillis() + timeoutMs
        var lastFailure: Throwable? = null
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(withId(id)).check(matches(isDisplayed()))
                return
            } catch (failure: Throwable) {
                lastFailure = failure
                Thread.sleep(200L)
            }
        }
        fail("View $id did not become visible within ${timeoutMs}ms. Last error: ${lastFailure?.message}")
    }

    private fun waitForText(@IdRes id: Int, expected: String, timeoutMs: Long = 5_000L) {
        val deadline = System.currentTimeMillis() + timeoutMs
        var lastFailure: Throwable? = null
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(withId(id)).check(matches(withText(expected)))
                return
            } catch (failure: Throwable) {
                lastFailure = failure
                Thread.sleep(100L)
            }
        }
        fail("View $id did not show '$expected' within ${timeoutMs}ms. Last error: ${lastFailure?.message}")
    }
}
