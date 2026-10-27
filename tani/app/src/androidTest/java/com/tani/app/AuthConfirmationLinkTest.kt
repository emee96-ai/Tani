package com.tani.app

import android.content.Intent
import android.net.Uri
import androidx.annotation.IdRes
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.hamcrest.Matchers.not
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class AuthConfirmationLinkTest {

    @Test
    fun expiredConfirmationLinkOpensLoginNotPasswordReset() {
        launch("tani://auth/confirm#error=access_denied&error_code=otp_expired&error_description=Email+link+is+invalid+or+has+expired").use {
            waitForText(R.id.auth_title, "مرحباً بعودتك")
            waitForText(R.id.status, string(R.string.auth_confirm_link_failed))
            onView(withId(R.id.confirm_password)).check(matches(not(isDisplayed())))
        }
    }

    @Test
    fun successfulConfirmationLinkAsksTheUserToLogIn() {
        launch("tani://auth/confirm#access_token=ignored&refresh_token=ignored&type=signup").use {
            waitForText(R.id.auth_title, "مرحباً بعودتك")
            waitForText(R.id.status, string(R.string.auth_31))
        }
    }

    private fun launch(link: String): ActivityScenario<MainActivity> =
        ActivityScenario.launch(
            Intent(Intent.ACTION_VIEW, Uri.parse(link))
                .setClass(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
        )

    private fun string(id: Int) = ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)

    private fun waitForText(@IdRes id: Int, expected: String, timeoutMs: Long = 20_000L) {
        val deadline = System.currentTimeMillis() + timeoutMs
        var lastFailure: Throwable? = null
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(withId(id)).check(matches(withText(expected)))
                return
            } catch (failure: Throwable) {
                lastFailure = failure
                Thread.sleep(200L)
            }
        }
        fail("View $id did not show '$expected' within ${timeoutMs}ms. Last error: ${lastFailure?.message}")
    }
}
