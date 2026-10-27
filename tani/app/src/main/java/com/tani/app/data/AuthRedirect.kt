package com.tani.app.data

import com.tani.app.BuildConfig
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin

/**
 * يضمن أن رسائل تأكيد إنشاء الحساب تعود إلى تطبيق أندرويد بدلاً من
 * Site URL الافتراضي في Supabase (مثل localhost:3000).
 * رابط التأكيد مسار مستقل (/auth/confirm) عن رابط الاستعادة، فلا يفتح
 * رابط تأكيد منتهٍ شاشة تغيير كلمة المرور.
 */
object AuthRedirect {
    @Volatile
    private var installed = false

    @Synchronized
    fun install() {
        if (installed) return

        Supabase.client.plugin(HttpSend).intercept { request ->
            if (
                request.url.toString().contains("/auth/v1/signup") &&
                request.url.parameters["redirect_to"].isNullOrBlank()
            ) {
                request.url.parameters.append(
                    "redirect_to",
                    BuildConfig.EMAIL_CONFIRM_REDIRECT
                )
            }
            execute(request)
        }

        installed = true
    }
}
