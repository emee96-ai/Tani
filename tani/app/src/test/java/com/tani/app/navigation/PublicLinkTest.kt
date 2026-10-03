package com.tani.app.navigation

import org.junit.Assert.*
import org.junit.Test

class PublicLinkTest {
    private val id = "b4000000-0000-4000-8000-000000000001"
    @Test fun publicProductAndStoreRoutes() {
        assertEquals(PublicLink(PublicLink.Kind.PRODUCT,id),PublicLink.parse("tani://product/$id","shop.example.com"))
        assertEquals(PublicLink(PublicLink.Kind.STORE,id),PublicLink.parse("https://shop.example.com/store/$id","shop.example.com"))
    }
    @Test fun rejectsUntrustedAndAuthRoutes() {
        listOf("https://evil.example/store/$id", "https://shop.example.com.evil.example/store/$id",
            "https://evil@shop.example.com/store/$id", "http://shop.example.com/store/$id",
            "tani://auth/reset#access_token=token", "tani://product/$id/extra", "tani://product/not-a-uuid",
            "tani://product/$id?access_token=token", "https://shop.example.com:8080/store/$id"
        ).forEach { assertNull(it, PublicLink.parse(it,"shop.example.com")) }
    }
}
