package com.tani.app.data.notifications

import android.os.Build
import com.tani.app.BuildConfig
import com.tani.app.data.Supabase
import java.util.Locale
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object PushRegistrationPolicy {
    private val supportedProviders = setOf("fcm", "expo", "apns")

    fun normalizeProvider(value: String): String {
        val normalized = value.trim().lowercase(Locale.ROOT)
        require(normalized in supportedProviders) { "مزود الإشعارات غير مدعوم" }
        return normalized
    }

    fun normalizeToken(value: String): String {
        val normalized = value.trim()
        require(normalized.length in 16..4096) { "رمز الإشعارات غير صالح" }
        return normalized
    }
}

interface PushRegistrationGateway {
    suspend fun register(provider: String, token: String): String
    suspend fun unregister(provider: String, token: String): Boolean
}

class SupabasePushRegistrationGateway : PushRegistrationGateway {
    override suspend fun register(provider: String, token: String): String {
        require(Supabase.hasStoredSession() && !Supabase.userId.isNullOrBlank()) {
            "تسجيل الدخول مطلوب لتفعيل الإشعارات"
        }
        val normalizedProvider = PushRegistrationPolicy.normalizeProvider(provider)
        val normalizedToken = PushRegistrationPolicy.normalizeToken(token)

        return Supabase.post(
            "rpc/register_my_push_device",
            buildJsonObject {
                put("p_provider", normalizedProvider)
                put("p_token", normalizedToken)
                put("p_platform", "android")
                put("p_app_version", BuildConfig.VERSION_NAME)
                put("p_device_model", listOf(Build.MANUFACTURER, Build.MODEL).filter { it.isNotBlank() }.joinToString(" ").take(160))
                put("p_locale", Locale.getDefault().toLanguageTag().take(32))
            }.toString()
        )
    }

    override suspend fun unregister(provider: String, token: String): Boolean {
        if (!Supabase.hasStoredSession() || Supabase.userId.isNullOrBlank()) return false
        val normalizedProvider = PushRegistrationPolicy.normalizeProvider(provider)
        val normalizedToken = PushRegistrationPolicy.normalizeToken(token)

        return Supabase.post(
            "rpc/unregister_my_push_device",
            buildJsonObject {
                put("p_provider", normalizedProvider)
                put("p_token", normalizedToken)
            }.toString()
        )
    }
}

object PushRegistrationGatewayProvider {
    @Volatile
    var gateway: PushRegistrationGateway = SupabasePushRegistrationGateway()
}
