package com.tani.app.data.network

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * المصدر الوحيد لنص الخطأ الذي يراه العميل.
 * رسائل الخادم الإنجليزية تُترجم من [serverMessages]، والرسائل العربية التي يكتبها التطبيق
 * تُعرض كما هي، وأي نص تقني آخر يُستبدل بالرسالة الاحتياطية الخاصة بالشاشة.
 */
object CustomerErrorMessages {
    fun from(error: Throwable, fallback: String): String {
        val raw = error.message.orEmpty()
        return when {
            error is UnknownHostException || error is ConnectException ->
                "تعذر الاتصال بالإنترنت. تأكد من الشبكة وحاول مرة أخرى."
            error is SocketTimeoutException -> "الشبكة بطيئة ولم يكتمل الطلب. حاول مرة أخرى."
            error is SSLException -> "تعذر إنشاء اتصال آمن بالخدمة. حاول مرة أخرى."
            else -> fromMessage(raw, fallback)
        }
    }

    fun fromMessage(raw: String?, fallback: String): String {
        val value = raw.orEmpty().trim()
        val lower = value.lowercase()
        serverMessages.firstOrNull { (key, _) -> lower.contains(key) }?.let { return it.second }
        return when {
            lower.contains("timeout") || lower.contains("timed out") ->
                "الشبكة بطيئة ولم يكتمل الطلب. حاول مرة أخرى."
            lower.contains("unable to resolve") || lower.contains("failed to connect") ->
                "تعذر الاتصال بالإنترنت. تأكد من الشبكة وحاول مرة أخرى."
            lower.contains("401") || lower.contains("unauthorized") ||
                lower.contains("jwt") && lower.contains("expired") ->
                "انتهت جلسة تسجيل الدخول. سجّل الدخول من جديد لإكمال العملية."
            lower.contains("429") || lower.contains("rate limit") ->
                "تم إرسال طلبات كثيرة خلال وقت قصير. حاول مرة أخرى بعد قليل."
            lower.contains("duplicate key") || lower.contains("unique constraint") ->
                "هذا السجل موجود مسبقاً."
            lower.contains("permission denied") || lower.contains("42501") || lower.contains("row-level security") ->
                "لا توجد صلاحية لتنفيذ هذه العملية."
            Regex("\\(5\\d\\d\\)").containsMatchIn(value) || lower.contains("server error") ->
                "الخدمة غير متاحة مؤقتاً. حاول مرة أخرى."
            isArabic(value) -> masculine(value)
            else -> fallback
        }
    }

    /**
     * بعض رسائل قاعدة البيانات المنشورة مكتوبة بصيغة المؤنث؛ التطبيق يخاطب المستخدم بالمذكر.
     * نوحّد الصيغة هنا بدلاً من إعادة تعريف دوال الخادم.
     */
    internal fun masculine(value: String): String =
        feminineWord.replace(value) { match ->
            match.groupValues[1] + feminineToMasculine.getValue(match.groupValues[2])
        }

    private val feminineToMasculine = mapOf(
        "اختاري" to "اختر", "حاولي" to "حاول", "راجعي" to "راجع", "أعيدي" to "أعد",
        "أكدي" to "أكد", "أكّدي" to "أكّد", "أدخلي" to "أدخل", "أضيفي" to "أضف",
        "اكتبي" to "اكتب", "حدّثي" to "حدّث", "تحققي" to "تحقق", "تأكدي" to "تأكد",
        "سجّلي" to "سجّل", "ارفعي" to "ارفع", "أكملي" to "أكمل", "فعّلي" to "فعّل"
    )

    private val feminineWord = Regex(
        "(?<![\\u0621-\\u064A])(و|ف)?(" +
            feminineToMasculine.keys.sortedByDescending { it.length }.joinToString("|") +
            ")(?![\\u0621-\\u064A])"
    )

    internal fun isArabic(value: String): Boolean = value.any { it in '؀'..'ۿ' }

    /** مفاتيح بأحرف صغيرة من رسائل RAISE EXCEPTION في قاعدة البيانات؛ الأطول أولاً حين تتداخل. */
    private val serverMessages: List<Pair<String, String>> = listOf(
        "a product in your cart is no longer available" to "أحد المنتجات في السلة لم يعد متاحاً. راجع السلة ثم أعد المحاولة.",
        "product not found or merchant is not approved" to "المنتج غير متاح حالياً.",
        "product has order history" to "لا يمكن حذف منتج له طلبات سابقة. يمكن إيقافه بدلاً من حذفه.",
        "product is not available" to "المنتج غير متاح حالياً.",
        "product not found" to "المنتج غير متاح حالياً.",
        "requested quantity is not available" to "الكمية المطلوبة غير متوفرة حالياً.",
        "insufficient stock" to "الكمية المطلوبة غير متوفرة في المخزون.",
        "cart has too many items" to "السلة تحتوي على عدد كبير من المنتجات. قلّل المنتجات ثم أعد المحاولة.",
        "cart is empty" to "السلة فارغة.",
        "invalid cart" to "بيانات السلة غير صحيحة. حدّث السلة ثم أعد المحاولة.",
        "invalid checkout key" to "تعذر تأكيد محاولة الطلب. أعد فتح صفحة الدفع.",
        "choose a delivery area" to "اختر منطقة التوصيل لكل متجر.",
        "selected delivery area is not available" to "منطقة التوصيل المختارة غير متاحة لهذا المتجر.",
        "delivery settings are incomplete" to "أحد المتاجر لم يكمل إعداد التوصيل بعد.",
        "invalid delivery zone selections" to "راجع اختيار مناطق التوصيل.",
        "invalid delivery fee or estimate" to "راجع رسوم التوصيل وزمن الوصول.",
        "invalid delivery fee" to "رسوم التوصيل غير صحيحة.",
        "invalid delivery estimate" to "زمن الوصول المتوقع غير صحيح.",
        "invalid delivery area" to "منطقة التوصيل غير صحيحة.",
        "at least one delivery zone is required" to "أضف منطقة توصيل واحدة على الأقل.",
        "enable at least one" to "فعّل منطقة توصيل واحدة على الأقل.",
        "address and phone are required" to "العنوان ورقم الهاتف مطلوبان.",
        "address not found" to "العنوان غير موجود.",
        "order notes are too long" to "ملاحظات الطلب طويلة جداً.",
        "order does not belong to this customer" to "هذا الطلب غير مرتبط بحسابك.",
        "order group not found" to "الطلب غير موجود.",
        "order has no items" to "الطلب لا يحتوي على منتجات.",
        "no cancellable orders found" to "لا يمكن إلغاء هذا الطلب الآن.",
        "can no longer be cancelled" to "لا يمكن إلغاء الطلب بعد قبول أحد التجار له.",
        "reviews require a delivered order" to "التقييم متاح بعد استلام الطلب فقط.",
        "invalid review" to "التقييم يجب أن يكون من 1 إلى 5، والتعليق أقل من 1000 حرف.",
        "ticket is closed" to "هذه التذكرة مغلقة. افتح تذكرة جديدة إذا احتجت للمساعدة.",
        "ticket is not accessible" to "هذه التذكرة غير متاحة لحسابك.",
        "invalid message" to "الرسالة فارغة أو طويلة جداً.",
        "invalid referral code" to "كود الإحالة غير صحيح.",
        "cannot refer yourself" to "لا يمكن استخدام كود الإحالة الخاص بك.",
        "invalid phone number" to "رقم الهاتف غير صحيح.",
        "merchant phone changed" to "تغير رقم هاتف التاجر. أعد فتح التطبيق.",
        "merchant phone verification is required" to "يجب التحقق من رقم هاتف التاجر أولاً.",
        "merchant phone is not verified" to "رقم هاتف التاجر غير موثق بعد.",
        "merchant policies must be accepted" to "يجب الموافقة على اتفاقية وسياسات التاجر قبل الإرسال.",
        "merchant application is incomplete" to "طلب التاجر غير مكتمل. راجع الحقول المطلوبة.",
        "merchant application not found" to "طلب التاجر غير موجود.",
        "cannot be resubmitted" to "حالة حساب التاجر لا تسمح بإعادة إرسال الطلب.",
        "merchant identity document is missing" to "مستند الهوية مطلوب.",
        "identity document was not uploaded" to "مستند الهوية غير متاح. ارفع المستند مرة أخرى.",
        "identity document is required" to "مستند الهوية مطلوب.",
        "invalid identity document type" to "نوع مستند الهوية غير مدعوم.",
        "merchant account is inactive" to "حساب التاجر غير مفعّل.",
        "merchant account is unavailable" to "حساب التاجر غير متاح حالياً.",
        "approved merchant required" to "هذه العملية متاحة للتجار المعتمدين فقط.",
        "store name is required" to "اسم المتجر مطلوب.",
        "business name is required" to "اسم النشاط التجاري مطلوب.",
        "store category is invalid" to "الفئة المختارة لم تعد متاحة. اختر فئة أخرى.",
        "choose a category" to "اختر فئة للمتجر أو اكتب اسم الفئة المطلوبة.",
        "category not found" to "الفئة غير موجودة.",
        "city is required" to "المدينة مطلوبة.",
        "description is too long" to "الوصف طويل جداً.",
        "store seller link is not owned" to "هذا المتجر غير مرتبط بحسابك.",
        "plan not available" to "الباقة غير متاحة حالياً.",
        "request already reviewed" to "تمت مراجعة هذا الطلب مسبقاً.",
        "request not found" to "الطلب غير موجود.",
        "protected profile authorization fields" to "لا يمكن تعديل هذه البيانات من الحساب.",
        "protected merchant verification fields" to "لا يمكن تعديل بيانات التحقق من الحساب.",
        "account is not active" to "الحساب غير مفعّل.",
        "account profile not found" to "تعذر العثور على بيانات الحساب. سجّل الدخول من جديد.",
        "user profile not found" to "تعذر العثور على بيانات الحساب. سجّل الدخول من جديد.",
        "no registered account found for this email" to "لا يوجد حساب مسجل بهذا البريد.",
        "authentication required" to "تسجيل الدخول مطلوب لإكمال العملية.",
        "invalid login credentials" to "البريد الإلكتروني أو كلمة المرور غير صحيحة.",
        "user already registered" to "يوجد حساب مسجل بهذا البريد بالفعل.",
        "password should be" to "كلمة المرور لا تحقق متطلبات الأمان.",
        "staff access required" to "لا توجد صلاحية لتنفيذ هذه العملية.",
        "admin access required" to "لا توجد صلاحية لتنفيذ هذه العملية."
    )
}
