package com.tani.admin

import org.junit.Assert.assertEquals
import org.junit.Test

class AdminUiTextTest {
    @Test fun localizesStatuses() {
        assertEquals("بانتظار القبول", AdminUiText.orderStatus("pending"))
        assertEquals("موقوف", AdminUiText.merchantStatus("suspended"))
        assertEquals("قيد المعالجة", AdminUiText.supportStatus("in_progress"))
    }

    @Test fun hidesRawInfrastructureErrors() {
        val raw = "HTTP_500 infinite recursion detected in policy for relation orders"
        assertEquals(
            "مشكلة سابقة في صلاحيات الطلبات — تم إصلاح السبب، والسجل محفوظ للمراجعة",
            AdminUiText.friendlyError(raw)
        )
    }

    @Test fun calculatesSafePercentages() {
        assertEquals("0%", AdminUiText.percent(0.0, 0.0))
        assertEquals("75%", AdminUiText.percent(3.0, 4.0))
    }
}
