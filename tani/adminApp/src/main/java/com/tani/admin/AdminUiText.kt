package com.tani.admin

import java.text.DecimalFormat

object AdminUiText {
    fun orderStatus(value: String?): String = when (value) {
        "pending" -> "بانتظار القبول"
        "accepted" -> "مقبول"
        "preparing" -> "قيد التجهيز"
        "ready" -> "جاهز"
        "out_for_delivery" -> "خرج للتوصيل"
        "delivered" -> "مكتمل"
        "cancelled" -> "ملغي"
        "rejected" -> "مرفوض"
        "failed" -> "تعذر التوصيل"
        else -> value ?: "غير معروف"
    }

    fun merchantStatus(value: String?): String = when (value) {
        "pending" -> "قيد المراجعة"
        "changes_requested" -> "تعديلات مطلوبة"
        "approved" -> "معتمد"
        "rejected" -> "مرفوض"
        "suspended" -> "موقوف"
        else -> value ?: "غير معروف"
    }

    fun supportStatus(value: String?): String = when (value) {
        "open" -> "مفتوحة"
        "in_progress" -> "قيد المعالجة"
        "resolved" -> "تم الحل"
        "closed" -> "مغلقة"
        else -> value ?: "غير معروف"
    }

    fun priority(value: String?): String = when (value) {
        "urgent" -> "عاجلة"
        "high" -> "عالية"
        "normal" -> "عادية"
        "low" -> "منخفضة"
        else -> value ?: "عادية"
    }

    fun role(value: String?): String = when (value) {
        "admin" -> "مدير"
        "support" -> "دعم"
        "seller" -> "تاجر"
        "customer" -> "عميل"
        else -> value ?: "مستخدم"
    }

    fun trustLevel(value: String?): String = when (value) {
        "verified" -> "موثّق"
        "trusted" -> "موثوق"
        "high_performing" -> "أداء مرتفع"
        "restricted" -> "مقيّد"
        else -> value ?: "غير مصنف"
    }

    fun friendlyError(raw: String?): String {
        val value = raw.orEmpty().lowercase()
        return when {
            "infinite recursion" in value && "orders" in value ->
                "مشكلة سابقة في صلاحيات الطلبات — تم إصلاح السبب، والسجل محفوظ للمراجعة"
            "timeout" in value || "timed out" in value -> "انتهت مهلة الاتصال بالخادم"
            "network" in value || "unable to resolve host" in value -> "تعذر الاتصال بالشبكة"
            "permission" in value || "admin required" in value -> "محاولة غير مصرح بها"
            "http_500" in value || "internal server" in value -> "خطأ داخلي في الخادم"
            value.isBlank() -> "خطأ غير مصنف"
            else -> "خطأ تقني يحتاج مراجعة"
        }
    }

    fun backendMessage(raw: String?, status: Int): String {
        val value = raw.orEmpty().lowercase()
        return when {
            "cannot remove the last active admin" in value -> "لا يمكن إزالة آخر مدير نشط. أضيفي مديراً آخر أولاً"
            "no registered account found for this email" in value -> "هذا البريد غير مسجل في تاني"
            "user profile not found" in value -> "الحساب موجود لكن ملف المستخدم غير مكتمل"
            "invalid role" in value -> "الصلاحية المختارة غير صالحة"
            "admin permission required" in value || "admin required" in value || "admin access required" in value -> "هذه العملية متاحة للمدير فقط"
            "merchant application is incomplete" in value -> "بيانات التاجر غير مكتملة ولا يمكن اعتمادها"
            "identity document" in value -> "مستند هوية التاجر غير موجود أو غير صالح"
            "order is already in a final state" in value -> "الطلب وصل إلى حالة نهائية ولا يمكن تغييره"
            "status transition is not allowed" in value -> "لا يمكن نقل الطلب إلى الحالة المختارة"
            "not found" in value -> "العنصر المطلوب غير موجود"
            "infinite recursion" in value -> "تعذر الوصول للبيانات بسبب إعداد صلاحيات قديم"
            status == 401 -> "انتهت جلسة الدخول، سجّلي الدخول مرة أخرى"
            status == 403 -> "لا تملكين صلاحية تنفيذ هذه العملية"
            status >= 500 -> "حدث خطأ في الخادم، حاولي مرة أخرى"
            else -> "تعذر تنفيذ العملية"
        }
    }

    fun percent(part: Double, total: Double): String {
        if (total <= 0.0) return "0%"
        return DecimalFormat("0.#").format(part / total * 100.0) + "%"
    }

    fun money(value: Double): String = DecimalFormat("#,##0.##").format(value) + " ج.س"
}
