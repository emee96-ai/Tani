package com.tani.app.data

import com.tani.app.data.notifications.PushMessaging
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class Repository {
    suspend fun login(email: String, password: String) {
        val cleanEmail = validateEmail(email)
        require(password.isNotBlank()) { "كلمة المرور مطلوبة" }

        val response: AuthResponse = Supabase.authPost(
            "token?grant_type=password",
            buildJsonObject {
                put("email", cleanEmail)
                put("password", password)
            }.toString()
        )

        val accessToken = response.access_token ?: error("تعذر تسجيل الدخول")
        val refreshToken = response.refresh_token ?: error("تعذر إنشاء جلسة تسجيل دخول آمنة")
        Supabase.saveSession(accessToken, refreshToken, response.user?.id)
        Analytics.track("login_success", screen = "auth")
    }

    suspend fun signup(
        email: String,
        password: String,
        confirmPassword: String,
        name: String,
        phone: String
    ): Boolean {
        val cleanName = name.trim()
        val cleanPhone = normalizePhone(phone)
        val cleanEmail = validateEmail(email)

        require(cleanName.length >= 2) { "أدخل الاسم الكامل أو اسماً واضحاً" }
        require(cleanPhone.length in 9..15) { "رقم الهاتف غير صحيح" }
        validateNewPassword(password, confirmPassword)

        val body = buildJsonObject {
            put("email", cleanEmail)
            put("password", password)
            put("data", buildJsonObject {
                put("name", cleanName)
                put("phone", cleanPhone)
            })
        }

        val response: AuthResponse = Supabase.authPost("signup", body.toString())
        val uid = response.user?.id ?: error("تعذر إنشاء الحساب")

        // public.handle_new_user() في schema.sql ينشئ profile تلقائياً.
        // لا ننشئه مرة ثانية من التطبيق حتى لا يحدث تعارض بالمفتاح الأساسي.
        val accessToken = response.access_token
        val refreshToken = response.refresh_token
        if (accessToken != null && refreshToken != null) {
            Supabase.saveSession(accessToken, refreshToken, uid)
        }
        Analytics.track("signup_success", screen = "auth")

        // true = توجد جلسة كاملة قابلة للتجديد، false = تأكيد البريد مطلوب قبل الدخول.
        return accessToken != null && refreshToken != null
    }

    suspend fun requestPasswordReset(email: String) {
        val cleanEmail = validateEmail(email)
        val redirect = java.net.URLEncoder.encode(
            Supabase.PASSWORD_RESET_REDIRECT,
            Charsets.UTF_8.name()
        )

        Supabase.authPost<JsonElement>(
            "recover?redirect_to=$redirect",
            buildJsonObject { put("email", cleanEmail) }.toString()
        )
        Analytics.track("password_reset_requested", screen = "auth")
    }

    suspend fun resetPassword(
        recoveryToken: String,
        recoveryRefreshToken: String,
        password: String,
        confirmPassword: String
    ) {
        require(recoveryToken.isNotBlank()) { "رابط استعادة كلمة المرور غير صالح" }
        require(recoveryRefreshToken.isNotBlank()) { "رابط الاستعادة لا يحتوي جلسة كاملة. اطلب رابطاً جديداً" }
        validateNewPassword(password, confirmPassword)

        Supabase.authPut<AuthUser>(
            "user",
            buildJsonObject { put("password", password) }.toString(),
            recoveryToken
        )

        // لا نعتمد جلسة قادمة من رابط خارجي كجلسة دخول: رابط مصنوع بتوكن حساب المهاجم
        // كان سيُدخل الضحية إلى ذلك الحساب. نلغي جلسات الحساب (فتخرج الأجهزة الأخرى
        // بعد تغيير كلمة المرور) ويسجّل المستخدم دخوله بكلمة المرور الجديدة.
        Supabase.revokeSessions(recoveryToken)
        if (Supabase.token != null) logout()
        Analytics.track("password_reset_completed", screen = "auth")
    }

    suspend fun changePassword(
        currentPassword: String,
        newPassword: String,
        confirmPassword: String
    ) {
        require(currentPassword.isNotBlank()) { "كلمة المرور الحالية مطلوبة" }
        require(currentPassword != newPassword) { "كلمة المرور الجديدة يجب أن تختلف عن الحالية" }
        validateNewPassword(newPassword, confirmPassword)
        val access = Supabase.token ?: error("تسجيل الدخول مطلوب")
        Supabase.authPut<AuthUser>(
            "user",
            buildJsonObject {
                put("current_password", currentPassword)
                put("password", newPassword)
            }.toString(),
            access
        )
        Analytics.track("password_changed", screen = "profile")
    }

    suspend fun logout() {
        PrivateUserData.clearSessionData(Supabase.userId)
        Cart.clear()
        PushMessaging.beforeSignOut()
        Supabase.signOut()
    }

    private fun validateEmail(email: String): String {
        val clean = email.trim().lowercase()
        val pattern = Regex("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", RegexOption.IGNORE_CASE)
        require(clean.isNotBlank()) { "البريد الإلكتروني مطلوب" }
        require(pattern.matches(clean)) { "البريد الإلكتروني غير صحيح" }
        return clean
    }

    internal fun normalizePhone(phone: String): String {
        val clean = phone.trim().replace(Regex("[\\s()-]"), "")
        require(Regex("^\\+?[0-9]+$").matches(clean)) { "رقم الهاتف غير صحيح" }
        return clean
    }

    private fun validateNewPassword(password: String, confirmPassword: String) {
        require(password.length >= 8) { "كلمة المرور يجب أن تكون 8 أحرف على الأقل" }
        require(password.any { it.isLetter() } && password.any { it.isDigit() }) {
            "كلمة المرور يجب أن تحتوي على حرف ورقم على الأقل"
        }
        require(password == confirmPassword) { "كلمتا المرور غير متطابقتين" }
    }

    suspend fun accountProfile(): AccountProfile {
        val uid = Supabase.userId ?: error("تسجيل الدخول مطلوب")

        val profile = Supabase.get<List<UserProfile>>(
            "profiles",
            "select=id,name,phone,role,avatar_url,is_active&id=eq.$uid&limit=1"
        ).firstOrNull() ?: error("تعذر العثور على الملف الشخصي")

        require(profile.is_active) { "هذا الحساب غير نشط" }

        // Supabase.get() يجدد الجلسة عند الحاجة، لذلك نقرأ التوكن بعده.
        val access = Supabase.token ?: error("تسجيل الدخول مطلوب")
        val authUser: AuthUser = Supabase.authGet("user", access)
        return AccountProfile(
            profile = profile,
            email = authUser.email.orEmpty()
        )
    }

    suspend fun updateProfile(
        name: String,
        phone: String,
        avatarUrl: String? = null
    ): UserProfile {
        val uid = Supabase.userId ?: error("تسجيل الدخول مطلوب")
        val cleanName = name.trim()
        val cleanPhone = normalizePhone(phone)

        require(cleanName.length in 2..100) { "الاسم يجب أن يكون بين حرفين و100 حرف" }
        require(cleanPhone.length in 7..30) { "رقم الهاتف غير صحيح" }

        val body = buildJsonObject {
            put("name", cleanName)
            put("phone", cleanPhone)
            if (!avatarUrl.isNullOrBlank()) put("avatar_url", avatarUrl)
            put("updated_at", java.time.Instant.now().toString())
        }

        val updated = Supabase.patch<List<UserProfile>>(
            "profiles",
            "id=eq.$uid",
            body.toString()
        ).firstOrNull() ?: error("تعذر حفظ الملف الشخصي")
        Analytics.track("profile_updated", screen = "profile")
        return updated
    }

    suspend fun uploadAvatar(bytes: ByteArray): String {
        val url = Supabase.uploadAvatar(bytes)
        Analytics.track("avatar_uploaded", screen = "profile")
        return url
    }

    suspend fun softDeleteAccount(): Boolean {
        val receipt: JsonObject = Supabase.invokeFunction("delete-account", "{}")
        val result = receipt["deleted"]?.jsonPrimitive?.booleanOrNull == true
        if (result) {
            Cart.clear()
            PushMessaging.beforeSignOut()
            Supabase.clearSession()
        }
        return result
    }

}
