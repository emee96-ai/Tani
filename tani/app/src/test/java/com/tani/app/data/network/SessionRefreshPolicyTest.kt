package com.tani.app.data.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRefreshPolicyTest {
    @Test
    fun rejectedRefreshTokenEndsSession() {
        assertTrue(SessionRefreshPolicy.isSessionRejected(400))
        assertTrue(SessionRefreshPolicy.isSessionRejected(401))
        assertTrue(SessionRefreshPolicy.isSessionRejected(403))
    }

    @Test
    fun temporaryFailuresKeepSession() {
        assertFalse(SessionRefreshPolicy.isSessionRejected(408))
        assertFalse(SessionRefreshPolicy.isSessionRejected(429))
        assertFalse(SessionRefreshPolicy.isSessionRejected(500))
        assertFalse(SessionRefreshPolicy.isSessionRejected(503))
    }
}
