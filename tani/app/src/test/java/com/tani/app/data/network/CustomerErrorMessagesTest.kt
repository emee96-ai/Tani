package com.tani.app.data.network

import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomerErrorMessagesTest {
    private val fallback = "تعذر تنفيذ العملية"

    @Test
    fun translatesKnownServerMessages() {
        val message = CustomerErrorMessages.from(
            IllegalStateException("Order does not belong to this customer"), fallback
        )
        assertEquals("هذا الطلب غير مرتبط بحسابك.", message)
    }

    @Test
    fun translatesServerMessagesWithDynamicSuffix() {
        val message = CustomerErrorMessages.fromMessage("Insufficient stock for قميص أزرق", fallback)
        assertEquals("الكمية المطلوبة غير متوفرة في المخزون.", message)
    }

    @Test
    fun keepsArabicAppMessages() {
        val raw = "حجم الصورة يجب ألا يتجاوز 5 ميجابايت"
        assertEquals(raw, CustomerErrorMessages.from(IllegalArgumentException(raw), fallback))
    }

    @Test
    fun hidesUnknownTechnicalMessages() {
        val message = CustomerErrorMessages.from(
            IllegalStateException("column orders.foo does not exist"), fallback
        )
        assertEquals(fallback, message)
    }

    @Test
    fun hidesBlankMessages() {
        assertEquals(fallback, CustomerErrorMessages.from(IllegalStateException(), fallback))
    }

    @Test
    fun mapsNetworkAndHttpStatusErrors() {
        assertTrue(CustomerErrorMessages.from(UnknownHostException("x"), fallback).contains("الإنترنت"))
        assertTrue(CustomerErrorMessages.fromMessage("تعذر الاتصال بالخدمة (503)", fallback).contains("مؤقتاً"))
        assertTrue(CustomerErrorMessages.fromMessage("JWT expired", fallback).contains("الجلسة") ||
            CustomerErrorMessages.fromMessage("JWT expired", fallback).contains("جلسة"))
    }

    @Test
    fun mapsPermissionAndDuplicateErrors() {
        assertEquals("لا توجد صلاحية لتنفيذ هذه العملية.",
            CustomerErrorMessages.fromMessage("new row violates row-level security policy", fallback))
        assertEquals("هذا السجل موجود مسبقاً.",
            CustomerErrorMessages.fromMessage("duplicate key value violates unique constraint", fallback))
    }

    @Test
    fun convertsFeminineServerMessagesToMasculine() {
        assertEquals(
            "تغيّر المخزون. راجع السلة ثم حاول مرة أخرى",
            CustomerErrorMessages.fromMessage("تغيّر المخزون. راجعي السلة ثم حاولي مرة أخرى", fallback)
        )
        assertEquals(
            "تغيّر السعر أو التوصيل. راجع الإجمالي الجديد ثم أكد الطلب مرة أخرى",
            CustomerErrorMessages.fromMessage("تغيّر السعر أو التوصيل. راجعي الإجمالي الجديد ثم أكدي الطلب مرة أخرى", fallback)
        )
        assertEquals("اختر المقاس أو اللون للمنتج", CustomerErrorMessages.masculine("اختاري المقاس أو اللون للمنتج"))
    }
}
