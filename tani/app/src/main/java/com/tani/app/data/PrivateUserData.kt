package com.tani.app.data

import android.content.Context
import android.content.SharedPreferences
import com.tani.app.data.cache.AppContentStore
import java.util.UUID

/** Account-specific drafts must never be restored for another signed-in user. */
object PrivateUserData {
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
        // The old draft has no owner identifier, so it cannot be migrated safely.
        appContext.deleteSharedPreferences("merchant_onboarding_draft")
    }

    fun merchantDraft(userId: String): SharedPreferences = appContext.getSharedPreferences(
        "merchant_onboarding_draft_${UUID.fromString(userId)}", Context.MODE_PRIVATE
    )

    fun clearSessionData(userId: String?) {
        if (!::appContext.isInitialized) return
        appContext.deleteSharedPreferences("merchant_onboarding_draft")
        userId?.let { runCatching { merchantDraft(it).edit().clear().commit() } }
        AppContentStore.clearPrivateData()
    }
}
