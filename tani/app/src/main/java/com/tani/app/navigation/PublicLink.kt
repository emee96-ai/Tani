package com.tani.app.navigation

import java.net.URI
import java.util.UUID

data class PublicLink(val kind: Kind, val id: String) {
    enum class Kind { PRODUCT, STORE }
    companion object {
        fun parse(value: String, verifiedHost: String): PublicLink? = runCatching {
            val uri = URI(value)
            if (uri.userInfo != null || uri.fragment != null || uri.query != null) return null
            val parts = uri.rawPath?.split('/') ?: return null
            val kind: String
            val id: String
            when (uri.scheme?.lowercase()) {
                "tani" -> {
                    if (uri.port != -1 || parts.size != 2 || parts[0].isNotEmpty()) return null
                    kind = uri.host ?: return null
                    id = parts[1]
                }
                "https" -> {
                    if (!uri.host.equals(verifiedHost, ignoreCase = true) || uri.port !in listOf(-1,443) || parts.size != 3) return null
                    kind = parts[1]
                    id = parts[2]
                }
                else -> return null
            }
            if (!Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$").matches(id)) return null
            PublicLink(when (kind.lowercase()) {
                "product" -> Kind.PRODUCT
                "store" -> Kind.STORE
                else -> return null
            }, UUID.fromString(id).toString())
        }.getOrNull()
    }
}
