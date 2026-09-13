package com.tani.admin.data

import android.content.Context
import com.tani.admin.security.SecureTokenStorage
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URLEncoder

object AdminApi {
    const val URL = "https://sihttimibjzoahvwuwbm.supabase.co"
    const val KEY = "sb_publishable_9syQMNqMr0q9V0_Z4W-jvA_OQjhHj1Y"

    private const val ACCESS = "access_token"
    private const val REFRESH = "refresh_token"
    private const val USER_ID = "user_id"

    private lateinit var storage: SecureTokenStorage
    private val refreshMutex = Mutex()

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = HttpClient(Android) { expectSuccess = false }

    fun init(context: Context) {
        storage = SecureTokenStorage(context.applicationContext)
    }

    private var accessToken: String?
        get() = storage.getString(ACCESS)
        set(value) = storage.putString(ACCESS, value)

    private var refreshToken: String?
        get() = storage.getString(REFRESH)
        set(value) = storage.putString(REFRESH, value)

    var userId: String?
        get() = storage.getString(USER_ID)
        private set(value) = storage.putString(USER_ID, value)

    fun hasSession(): Boolean = !accessToken.isNullOrBlank() && !refreshToken.isNullOrBlank()

    fun clearSession() = storage.clear()

    suspend fun signIn(email: String, password: String) {
        val response = client.request("$URL/auth/v1/token?grant_type=password") {
            method = HttpMethod.Post
            headers {
                append("apikey", KEY)
                append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            }
            setBody(buildJsonObject {
                put("email", email)
                put("password", password)
            }.toString())
        }
        val root = parseObject(response)
        val access = root.string("access_token") ?: error("تعذر تسجيل الدخول")
        val refresh = root.string("refresh_token") ?: error("تعذر حفظ جلسة الدخول")
        val uid = root["user"]?.jsonObject?.string("id") ?: error("جلسة غير صالحة")
        accessToken = access
        refreshToken = refresh
        userId = uid
    }

    suspend fun signOut() {
        val token = accessToken
        if (!token.isNullOrBlank()) {
            runCatching {
                client.request("$URL/auth/v1/logout") {
                    method = HttpMethod.Post
                    headers {
                        append("apikey", KEY)
                        append("Authorization", "Bearer $token")
                    }
                }.bodyAsText()
            }
        }
        clearSession()
    }

    suspend fun getMyRole(): String {
        val uid = userId ?: error("جلسة غير صالحة")
        val rows = rows(
            "profiles",
            "id=eq.${enc(uid)}&select=role,is_active&limit=1"
        )
        val profile = rows.firstOrNull()?.jsonObject ?: error("لا يوجد ملف مستخدم لهذا الحساب")
        val active = profile.bool("is_active")
        val role = profile.string("role")
        if (!active || role !in setOf("admin", "support")) {
            error("هذا الحساب لا يملك صلاحية تطبيق الإدارة")
        }
        return role
    }

    suspend fun rows(table: String, query: String): JsonArray {
        val response = authedRequest("$URL/rest/v1/$table?$query", HttpMethod.Get)
        return parseElement(response).jsonArray
    }

    suspend fun exactCount(table: String, filter: String = ""): Int {
        val query = buildString {
            append("select=id")
            if (filter.isNotBlank()) append('&').append(filter)
            append("&limit=1")
        }
        val response = authedRequest(
            "$URL/rest/v1/$table?$query",
            HttpMethod.Get,
            extraHeaders = mapOf("Prefer" to "count=exact", "Range" to "0-0")
        )
        response.bodyAsText()
        if (response.status.value !in 200..299) return 0
        val total = response.headers["Content-Range"]?.substringAfterLast('/')?.toIntOrNull()
        return total ?: 0
    }

    suspend fun rpc(name: String, body: JsonObject = buildJsonObject { }): JsonElement? {
        val response = authedRequest(
            "$URL/rest/v1/rpc/$name",
            HttpMethod.Post,
            body = body.toString(),
            extraHeaders = mapOf("Prefer" to "return=representation")
        )
        val text = response.bodyAsText()
        ensureOk(response, text)
        return if (text.isBlank()) null else json.parseToJsonElement(text)
    }

    suspend fun patchRow(table: String, id: String, body: JsonObject) {
        val response = authedRequest(
            "$URL/rest/v1/$table?id=eq.${enc(id)}",
            HttpMethod.Patch,
            body = body.toString(),
            extraHeaders = mapOf("Prefer" to "return=minimal")
        )
        val text = response.bodyAsText()
        ensureOk(response, text)
    }

    suspend fun downloadPrivateObject(storagePath: String): ByteArray {
        val safePath = storagePath.split('/').joinToString("/") { enc(it) }
        val response = authedRequest(
            "$URL/storage/v1/object/authenticated/merchant-private/$safePath",
            HttpMethod.Get
        )
        if (response.status.value !in 200..299) {
            val text = response.bodyAsText()
            ensureOk(response, text)
        }
        return response.body()
    }

    private suspend fun authedRequest(
        url: String,
        method: HttpMethod,
        body: String? = null,
        extraHeaders: Map<String, String> = emptyMap()
    ): HttpResponse {
        if (accessToken.isNullOrBlank() || refreshToken.isNullOrBlank()) {
            error("تسجيل الدخول مطلوب")
        }
        var response = doRequest(url, method, body, extraHeaders)
        if (response.status.value == 401) {
            response.bodyAsText()
            if (refreshSession()) response = doRequest(url, method, body, extraHeaders)
        }
        return response
    }

    private suspend fun doRequest(
        url: String,
        method: HttpMethod,
        body: String?,
        extraHeaders: Map<String, String>
    ): HttpResponse = client.request(url) {
        this.method = method
        authHeaders(this)
        headers {
            if (body != null) append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            extraHeaders.forEach { (k, v) -> append(k, v) }
        }
        if (body != null) setBody(body)
    }

    private fun authHeaders(builder: HttpRequestBuilder) {
        builder.headers {
            append("apikey", KEY)
            append("Authorization", "Bearer ${accessToken ?: KEY}")
        }
    }

    private suspend fun refreshSession(): Boolean = refreshMutex.withLock {
        val refresh = refreshToken ?: return@withLock false
        return@withLock runCatching {
            val response = client.request("$URL/auth/v1/token?grant_type=refresh_token") {
                method = HttpMethod.Post
                headers {
                    append("apikey", KEY)
                    append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                }
                setBody(buildJsonObject { put("refresh_token", refresh) }.toString())
            }
            val root = parseObject(response)
            accessToken = root.string("access_token") ?: error("تعذر تجديد الجلسة")
            refreshToken = root.string("refresh_token") ?: refresh
            userId = root["user"]?.jsonObject?.string("id") ?: userId
            true
        }.getOrElse { false }
    }

    private suspend fun parseElement(response: HttpResponse): JsonElement {
        val text = response.bodyAsText()
        ensureOk(response, text)
        return if (text.isBlank()) JsonNull else json.parseToJsonElement(text)
    }

    private suspend fun parseObject(response: HttpResponse): JsonObject = parseElement(response).jsonObject

    private fun ensureOk(response: HttpResponse, text: String) {
        if (response.status.value in 200..299) return
        val message = runCatching {
            val obj = json.parseToJsonElement(text).jsonObject
            sequenceOf("message", "msg", "error_description", "error")
                .mapNotNull { obj[it]?.jsonPrimitive?.contentOrNull }
                .firstOrNull { it.isNotBlank() }
        }.getOrNull()
        error(message ?: "تعذر تنفيذ العملية (${response.status.value})")
    }

    private fun enc(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
}

fun JsonObject.string(key: String): String? = this[key]?.let { el ->
    if (el === JsonNull) null else runCatching { el.jsonPrimitive.contentOrNull }.getOrNull()
}

fun JsonObject.bool(key: String): Boolean = string(key)?.toBooleanStrictOrNull() ?: false

fun JsonObject.numberText(key: String, fallback: String = "0"): String =
    string(key)?.takeIf { it.isNotBlank() } ?: fallback
