package com.tani.app.data.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PushRegistrationPolicyTest {
    @Test
    fun normalizesProviderAndToken() {
        assertEquals("fcm", PushRegistrationPolicy.normalizeProvider(" FCM "))
        assertEquals("token_1234567890123456", PushRegistrationPolicy.normalizeToken("  token_1234567890123456  "))
    }

    @Test
    fun rejectsUnsupportedProvider() {
        assertThrows(IllegalArgumentException::class.java) {
            PushRegistrationPolicy.normalizeProvider("unknown")
        }
    }

    @Test
    fun rejectsShortToken() {
        assertThrows(IllegalArgumentException::class.java) {
            PushRegistrationPolicy.normalizeToken("short")
        }
    }

    @Test
    fun rejectsOversizedToken() {
        assertThrows(IllegalArgumentException::class.java) {
            PushRegistrationPolicy.normalizeToken("x".repeat(4097))
        }
    }
}
