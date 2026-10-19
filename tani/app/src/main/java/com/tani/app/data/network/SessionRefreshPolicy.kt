package com.tani.app.data.network

/**
 * يفرّق بين رفض الخادم للجلسة وبين فشل مؤقت في الشبكة أو الخادم.
 * رفض refresh token (مُلغى بعد تغيير كلمة المرور أو خروج من كل الأجهزة)
 * يعني أن الجلسة انتهت نهائياً، ولا فائدة من إبقائها محفوظة.
 */
object SessionRefreshPolicy {
    fun isSessionRejected(status: Int): Boolean =
        status in 400..499 && status != 408 && status != 429
}
