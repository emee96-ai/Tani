package com.tani.app.data.auth

import com.tani.app.data.AuthResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthResponseTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test fun signupWaitingForEmailConfirmationCarriesTheUserIdAtTheTop() {
        val response = json.decodeFromString<AuthResponse>(
            """{"id":"a9c89ca9-fe69-4984-97b8-6aab0b23a124","aud":"authenticated","role":"authenticated","email":"new@tani.invalid","confirmation_sent_at":"2026-10-26T22:07:25Z","user_metadata":{"name":"New"}}"""
        )
        assertEquals("a9c89ca9-fe69-4984-97b8-6aab0b23a124", response.user?.id ?: response.id)
        assertNull(response.access_token)
    }

    @Test fun signupWithASessionCarriesTheUserInside() {
        val response = json.decodeFromString<AuthResponse>(
            """{"access_token":"a","refresh_token":"r","token_type":"bearer","expires_in":3600,"user":{"id":"u1","email":"x@tani.invalid"}}"""
        )
        assertEquals("u1", response.user?.id ?: response.id)
    }
}
