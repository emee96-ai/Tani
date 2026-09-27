package com.tani.app.data.network

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

object CustomerErrorMessages {
    fun from(error: Throwable, fallback: String): String {
        val raw = error.message.orEmpty()
        return when {
            error is UnknownHostException || error is ConnectException ->
                "تعذر الاتصال بالإنترنت. تأكدي من الشبكة وحاولي مرة أخرى."
            error is SocketTimeoutException || raw.contains("timeout", ignoreCase = true) ->
                "الشبكة بطيئة ولم يكتمل الطلب. حاولي مرة أخرى."
            error is SSLException ->
                "تعذر إنشاء اتصال آمن بالخدمة. حاولي مرة أخرى."
            raw.contains("401") || raw.contains("unauthorized", ignoreCase = true) ||
                raw.contains("jwt", ignoreCase = true) && raw.contains("expired", ignoreCase = true) ->
                "انتهت جلسة تسجيل الدخول. سجّلي الدخول من جديد لإكمال العملية."
            raw.contains("429") || raw.contains("rate limit", ignoreCase = true) ->
                "تم إرسال طلبات كثيرة خلال وقت قصير. حاولي مرة أخرى بعد قليل."
            raw.contains("5") && raw.contains("server", ignoreCase = true) ->
                "الخدمة غير متاحة مؤقتاً. حاولي مرة أخرى."
            else -> fallback
        }
    }
}
