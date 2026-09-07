package com.tani.app.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.delete
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.patch
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.call.body
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object Supabase {

    const val URL = "https://sihttimibjzoahvwuwbm.supabase.co"
    const val KEY = "sb_publishable_9syQMNqMr0q9V0_Z4W-jvA_OQjhHj1Y"
    const val PASSWORD_RESET_REDIRECT = "tani://auth/reset"

    private const val ACCESS_TOKEN_KEY = "access_token"
    private const val REFRESH_TOKEN_KEY = "refresh_token"
    private const val USER_ID_KEY = "user_id"
    private const val REFRESH_LEEWAY_SECONDS = 90L

    private lateinit var prefs: SharedPreferences

    @PublishedApi
    internal val refreshMutex = Mutex()

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    val client = HttpClient(Android) {
        expectSuccess = false
    }

    fun init(context: Context) {
        prefs = context.applicationContext
            .getSharedPreferences("tani_auth", Context.MODE_PRIVATE)
    }

    var token: String?
        get() = prefs.getString(ACCESS_TOKEN_KEY, null)
        private set(value) {
            prefs.edit().putString(ACCESS_TOKEN_KEY, value).apply()
        }

    var refreshToken: String?
        get() = prefs.getString(REFRESH_TOKEN_KEY, null)
        private set(value) {
            prefs.edit().putString(REFRESH_TOKEN_KEY, value).apply()
        }

    var userId: String?
        get() = prefs.getString(USER_ID_KEY, null)
        private set(value) {
            prefs.edit().putString(USER_ID_KEY, value).apply()
        }

    /**
     * الجلسة القابلة للاستعادة يجب أن تحتوي access + refresh token.
     * النسخ القديمة من التطبيق كانت تحفظ access token فقط، لذلك نطلب
     * تسجيل الدخول مرة واحدة بعد التحديث بدلاً من ترك جلسة تنتهي فجأة.
     */
    fun hasStoredSession(): Boolean =
        !token.isNullOrBlank() && !refreshToken.isNullOrBlank()

    fun saveSession(accessToken: String, refreshToken: String, id: String?) {
        val resolvedUserId = id ?: jwtStringClaim(accessToken, "sub")
        prefs.edit()
            .putString(ACCESS_TOKEN_KEY, accessToken)
            .putString(REFRESH_TOKEN_KEY, refreshToken)
            .putString(USER_ID_KEY, resolvedUserId)
            .apply()
    }

    fun clearSession() {
        prefs.edit().clear().apply()
    }

    @PublishedApi
    internal fun authHeaders(builder: HttpRequestBuilder) {
        builder.headers {
            append("apikey", KEY)
            append("Authorization", "Bearer ${token ?: KEY}")
        }
    }

    /**
     * يجدد الجلسة قبل انتهاء JWT بقليل. Supabase قد يدور refresh token،
     * لذلك نحفظ دائماً refresh token الجديد إذا أُعيد من الخادم.
     */
    @PublishedApi
    internal suspend fun ensureFreshSession(): Boolean {
        val access = token ?: return false
        if (!tokenExpiresSoon(access)) return true
        return refreshSession(force = false)
    }

    suspend fun refreshSession(force: Boolean = true): Boolean = refreshMutex.withLock {
        val access = token ?: return@withLock false
        if (!force && !tokenExpiresSoon(access)) return@withLock true

        val currentRefreshToken = refreshToken ?: return@withLock false

        return@withLock runCatching {
            val response: AuthResponse = authPost(
                "token?grant_type=refresh_token",
                buildJsonObject {
                    put("refresh_token", currentRefreshToken)
                }.toString()
            )

            val newAccessToken = response.access_token
                ?: error("تعذر تجديد جلسة تسجيل الدخول")
            val newRefreshToken = response.refresh_token ?: currentRefreshToken

            saveSession(
                accessToken = newAccessToken,
                refreshToken = newRefreshToken,
                id = response.user?.id ?: userId
            )
            true
        }.getOrElse {
            // لا نحذف الجلسة هنا لأن الفشل قد يكون مجرد انقطاع شبكة مؤقت.
            false
        }
    }

    @PublishedApi
    internal fun tokenExpiresSoon(accessToken: String): Boolean {
        val exp = jwtLongClaim(accessToken, "exp") ?: return true
        val nowSeconds = System.currentTimeMillis() / 1000L
        return exp <= nowSeconds + REFRESH_LEEWAY_SECONDS
    }

    private fun jwtLongClaim(accessToken: String, claim: String): Long? =
        jwtPayload(accessToken)?.get(claim)?.jsonPrimitive?.longOrNull

    private fun jwtStringClaim(accessToken: String, claim: String): String? =
        runCatching { jwtPayload(accessToken)?.get(claim)?.jsonPrimitive?.content }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }

    private fun jwtPayload(accessToken: String) = runCatching {
        val payloadPart = accessToken.split('.').getOrNull(1) ?: return@runCatching null
        val decoded = Base64.decode(
            payloadPart,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        json.parseToJsonElement(String(decoded, Charsets.UTF_8)).jsonObject
    }.getOrNull()

    suspend inline fun <reified T> get(
        path: String,
        query: String = ""
    ): T {
        if (token != null && !ensureFreshSession()) {
            error("تعذر تجديد جلسة الدخول. تحققي من الاتصال ثم حاولي مرة أخرى")
        }

        val url =
            "$URL/rest/v1/$path${if (query.isBlank()) "" else "?$query"}"

        var usedToken = token
        var response = client.get(url) {
            authHeaders(this)
        }

        if (
            response.status.value == 401 &&
            usedToken != null &&
            refreshToken != null
        ) {
            response.bodyAsText() // استهلاك الاستجابة قبل إعادة المحاولة
            if (refreshSession(force = true)) {
                usedToken = token
                response = client.get(url) {
                    authHeaders(this)
                }
            }
        }

        return parse(response)
    }

    suspend inline fun <reified T> post(
        path: String,
        body: String
    ): T {
        if (token != null && !ensureFreshSession()) {
            error("تعذر تجديد جلسة الدخول. تحققي من الاتصال ثم حاولي مرة أخرى")
        }

        var response = client.post("$URL/rest/v1/$path") {
            authHeaders(this)

            headers {
                append(
                    HttpHeaders.ContentType,
                    ContentType.Application.Json.toString()
                )
                append("Prefer", "return=representation")
            }

            setBody(body)
        }

        if (
            response.status.value == 401 &&
            token != null &&
            refreshToken != null
        ) {
            response.bodyAsText()
            if (refreshSession(force = true)) {
                response = client.post("$URL/rest/v1/$path") {
                    authHeaders(this)
                    headers {
                        append(
                            HttpHeaders.ContentType,
                            ContentType.Application.Json.toString()
                        )
                        append("Prefer", "return=representation")
                    }
                    setBody(body)
                }
            }
        }

        return parse(response)
    }

    suspend inline fun <reified T> patch(
        path: String,
        query: String,
        body: String
    ): T {
        if (token != null && !ensureFreshSession()) {
            error("تعذر تجديد جلسة الدخول. تحققي من الاتصال ثم حاولي مرة أخرى")
        }

        val url = "$URL/rest/v1/$path${if (query.isBlank()) "" else "?$query"}"
        var response = client.patch(url) {
            authHeaders(this)
            headers {
                append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                append("Prefer", "return=representation")
            }
            setBody(body)
        }

        if (response.status.value == 401 && token != null && refreshToken != null) {
            response.bodyAsText()
            if (refreshSession(force = true)) {
                response = client.patch(url) {
                    authHeaders(this)
                    headers {
                        append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        append("Prefer", "return=representation")
                    }
                    setBody(body)
                }
            }
        }

        return parse(response)
    }


    suspend fun delete(
        path: String,
        query: String
    ): Boolean {
        if (token != null && !ensureFreshSession()) {
            error("تعذر تجديد جلسة الدخول. تحققي من الاتصال ثم حاولي مرة أخرى")
        }

        val url = "$URL/rest/v1/$path${if (query.isBlank()) "" else "?$query"}"
        var response = client.delete(url) {
            authHeaders(this)
            headers { append("Prefer", "return=minimal") }
        }

        if (response.status.value == 401 && token != null && refreshToken != null) {
            response.bodyAsText()
            if (refreshSession(force = true)) {
                response = client.delete(url) {
                    authHeaders(this)
                    headers { append("Prefer", "return=minimal") }
                }
            }
        }

        val text = response.bodyAsText()
        if (response.status.value !in 200..299) {
            val message = runCatching {
                val obj = json.parseToJsonElement(text).jsonObject
                sequenceOf("message", "msg", "error_description", "error")
                    .mapNotNull { key -> obj[key]?.toString()?.trim('\"') }
                    .firstOrNull { it.isNotBlank() }
            }.getOrNull()
            error(message ?: "تعذر تنفيذ العملية (${response.status.value})")
        }
        return true
    }

    suspend fun uploadAvatar(bytes: ByteArray): String {
        val uid = userId ?: error("تسجيل الدخول مطلوب")
        require(bytes.isNotEmpty()) { "الصورة غير صالحة" }
        require(bytes.size <= 5 * 1024 * 1024) { "حجم الصورة يجب ألا يتجاوز 5 ميجابايت" }

        if (!ensureFreshSession()) {
            error("تعذر تجديد جلسة الدخول. تحققي من الاتصال ثم حاولي مرة أخرى")
        }

        val objectPath = "$uid/avatar.jpg"
        val response = client.post("$URL/storage/v1/object/avatars/$objectPath") {
            authHeaders(this)
            headers {
                append(HttpHeaders.ContentType, "image/jpeg")
                append("x-upsert", "true")
            }
            setBody(bytes)
        }

        val text = response.bodyAsText()
        if (response.status.value !in 200..299) {
            val message = runCatching {
                val obj = json.parseToJsonElement(text).jsonObject
                sequenceOf("message", "error", "error_description")
                    .mapNotNull { key -> obj[key]?.toString()?.trim('"') }
                    .firstOrNull { it.isNotBlank() }
            }.getOrNull()
            error(message ?: "تعذر رفع الصورة")
        }

        return "$URL/storage/v1/object/public/avatars/$objectPath?v=${System.currentTimeMillis()}"
    }


    /**
     * إرسال Analytics/Error telemetry بدون رمي خطأ للمستخدم.
     * لا نستخدم parse() هنا حتى لا يدخل تسجيل الخطأ في حلقة عند فشل شبكة المراقبة نفسها.
     */
    suspend fun telemetryInsert(path: String, body: String): Boolean = runCatching {
        if (token != null && !ensureFreshSession()) return@runCatching false

        val response = client.post("$URL/rest/v1/$path") {
            authHeaders(this)
            headers {
                append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                append("Prefer", "return=minimal")
            }
            setBody(body)
        }
        response.bodyAsText()
        response.status.value in 200..299
    }.getOrDefault(false)

    suspend inline fun <reified T> authGet(
        path: String,
        bearerToken: String
    ): T {
        val response = client.get("$URL/auth/v1/$path") {
            headers {
                append("apikey", KEY)
                append("Authorization", "Bearer $bearerToken")
            }
        }
        return parse(response)
    }

    suspend fun downloadPublicBytes(url: String): ByteArray? = runCatching {
        val response = client.get(url)
        if (response.status.value !in 200..299) return@runCatching null
        response.body<ByteArray>()
    }.getOrNull()

    suspend fun signOut() {
        val access = token
        if (!access.isNullOrBlank()) {
            runCatching {
                val response = client.post("$URL/auth/v1/logout") {
                    headers {
                        append("apikey", KEY)
                        append("Authorization", "Bearer $access")
                    }
                }
                response.bodyAsText()
            }
        }
        clearSession()
    }

    suspend inline fun <reified T> authPost(
        path: String,
        body: String
    ): T {
        val response = client.post("$URL/auth/v1/$path") {
            headers {
                append("apikey", KEY)
                append("Authorization", "Bearer $KEY")
                append(
                    HttpHeaders.ContentType,
                    ContentType.Application.Json.toString()
                )
            }
            setBody(body)
        }
        return parse(response)
    }

    suspend inline fun <reified T> authPut(
        path: String,
        body: String,
        bearerToken: String
    ): T {
        val response = client.put("$URL/auth/v1/$path") {
            headers {
                append("apikey", KEY)
                append("Authorization", "Bearer $bearerToken")
                append(
                    HttpHeaders.ContentType,
                    ContentType.Application.Json.toString()
                )
            }
            setBody(body)
        }
        return parse(response)
    }



    suspend inline fun <reified T> authPostAuthorized(
        path: String,
        body: String,
        bearerToken: String
    ): T {
        val response = client.post("$URL/auth/v1/$path") {
            headers {
                append("apikey", KEY)
                append("Authorization", "Bearer $bearerToken")
                append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            }
            setBody(body)
        }
        return parse(response)
    }

    suspend fun uploadObject(
        bucket: String,
        objectPath: String,
        bytes: ByteArray,
        mimeType: String,
        upsert: Boolean = false
    ): String {
        require(bytes.isNotEmpty()) { "الملف غير صالح" }
        if (!ensureFreshSession()) {
            error("تعذر تجديد جلسة الدخول. تحققي من الاتصال ثم حاولي مرة أخرى")
        }
        val response = client.post("$URL/storage/v1/object/$bucket/$objectPath") {
            authHeaders(this)
            headers {
                append(HttpHeaders.ContentType, mimeType)
                append("x-upsert", upsert.toString())
            }
            setBody(bytes)
        }
        val text = response.bodyAsText()
        if (response.status.value !in 200..299) {
            val message = runCatching {
                val obj = json.parseToJsonElement(text).jsonObject
                sequenceOf("message", "error", "error_description")
                    .mapNotNull { key -> obj[key]?.toString()?.trim('"') }
                    .firstOrNull { it.isNotBlank() }
            }.getOrNull()
            error(message ?: "تعذر رفع الملف")
        }
        return objectPath
    }

    suspend fun deleteStorageObject(bucket: String, objectPath: String): Boolean {
        if (!ensureFreshSession()) {
            error("تعذر تجديد جلسة الدخول. تحققي من الاتصال ثم حاولي مرة أخرى")
        }
        val response = client.delete("$URL/storage/v1/object/$bucket/$objectPath") {
            authHeaders(this)
        }
        val text = response.bodyAsText()
        if (response.status.value !in 200..299) {
            val message = runCatching {
                val obj = json.parseToJsonElement(text).jsonObject
                sequenceOf("message", "error", "error_description")
                    .mapNotNull { key -> obj[key]?.toString()?.trim('"') }
                    .firstOrNull { it.isNotBlank() }
            }.getOrNull()
            error(message ?: "تعذر حذف الملف")
        }
        return true
    }

    suspend inline fun <reified T> parse(response: HttpResponse): T {
        val text = response.bodyAsText()

        if (response.status.value !in 200..299) {
            val message = runCatching {
                val obj = json.parseToJsonElement(text).jsonObject
                sequenceOf("message", "msg", "error_description", "error")
                    .mapNotNull { key -> obj[key]?.toString()?.trim('"') }
                    .firstOrNull { it.isNotBlank() }
            }.getOrNull()

            ErrorMonitoring.captureMessage(
                source = "supabase_http",
                errorType = "HTTP_${response.status.value}",
                message = message ?: "Request failed",
                contextData = buildJsonObject { put("status", response.status.value) }
            )
            error(message ?: "تعذر الاتصال بالخدمة (${response.status.value})")
        }

        return json.decodeFromString(text.ifBlank { "{}" })
    }
}
