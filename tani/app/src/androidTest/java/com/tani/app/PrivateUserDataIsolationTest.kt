package com.tani.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tani.app.data.PrivateUserData
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrivateUserDataIsolationTest {
    @Test fun draftsAreIsolatedAndClearedOnLogout() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val first = "10000000-0000-4000-8000-000000000001"
        val second = "10000000-0000-4000-8000-000000000002"
        PrivateUserData.init(context)
        try {
            PrivateUserData.merchantDraft(first).edit().putString("phone", "0912345678").commit()
            assertNull(PrivateUserData.merchantDraft(second).getString("phone", null))
            PrivateUserData.merchantDraft(second).edit().putString("phone", "0999999999").commit()
            PrivateUserData.clearSessionData(first)
            assertNull(PrivateUserData.merchantDraft(first).getString("phone", null))
            assertEquals("0999999999", PrivateUserData.merchantDraft(second).getString("phone", null))
        } finally {
            PrivateUserData.clearSessionData(first)
            PrivateUserData.clearSessionData(second)
        }
    }

    @Test fun unownedLegacyDraftIsNotImported() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("merchant_onboarding_draft", Context.MODE_PRIVATE)
            .edit().putString("phone", "0912345678").commit()
        PrivateUserData.init(context)
        assertFalse(context.getSharedPreferences("merchant_onboarding_draft", Context.MODE_PRIVATE).contains("phone"))
    }
}
