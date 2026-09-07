package com.tani.app.ui.commerce

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object CommerceUi {
    fun statusLabel(status: String): String = when (status) {
        "pending" -> "بانتظار التاجر"
        "accepted" -> "تم قبول الطلب"
        "preparing" -> "قيد التجهيز"
        "ready" -> "جاهز للتوصيل"
        "out_for_delivery" -> "خرج للتوصيل"
        "delivered" -> "تم التسليم"
        "cancelled" -> "ملغي"
        "rejected" -> "مرفوض"
        "failed" -> "تعذر التنفيذ"
        "confirmed" -> "مؤكد"
        "processing" -> "قيد التنفيذ"
        else -> status
    }

    fun formatDate(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return runCatching {
            val instant = Instant.parse(value)
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                .withZone(ZoneId.systemDefault())
                .format(instant)
        }.getOrDefault(value.take(16).replace('T', ' '))
    }
}
