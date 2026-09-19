package com.tani.app

import androidx.annotation.IdRes
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
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
        ensureGuestHome()

        onView(withId(R.id.orders)).perform(click())
        waitForDisplayed(R.id.auth_title)
        onView(withText("مرحباً بعودتك")).check(matches(isDisplayed()))
        onView(withId(R.id.browse_as_guest)).check(matches(isDisplayed())).perform(click())

        waitForDisplayed(R.id.bottom_nav)
        onView(withId(R.id.home)).check(matches(isDisplayed()))
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
}
