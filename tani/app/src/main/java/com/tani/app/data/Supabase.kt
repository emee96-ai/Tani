package com.tani.app.data

import androidx.core.content.edit
import com.tani.app.util.runCatchingCancellable

import android.content.Context
import android.util.Base64
import com.tani.app.BuildConfig
import com.tani.app.data.network.SessionRefreshPolicy
import com.tani.app.security.SecureTokenStorage
import com.tani.app.data.notifications.PushMessaging
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
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

    const val URL = BuildConfig.SUPABASE_URL
    const val KEY = BuildConfig.SUPABASE_PUBLISHABLE_KEY
    val PASSWORD_RESET_REDIRECT: String get() = BuildConfig.PASSWORD_RESET_REDIRECT
    val EMAIL_CONFIRM_REDIRECT: String get() = BuildConfig.EMAIL_CONFIRM_REDIRECT

    private const val ACCESS_TOKEN_KEY = "access_token"
    private const val REFRESH_TOKEN_KEY = "refresh_token"
    private const val USER_ID_KEY = "user_id"
    private const val REFRESH_LEEWAY_SECONDS = 90L
    private const val SESSION_REFRESH_FAILED = "تعذر تجديد جلسة الدخول. تحقق من الاتصال ثم حاول مرة أخرى"

    private lateinit var secureStorage: SecureTokenStorage

    @PublishedApi
    internal val refreshMutex = Mutex()

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    val client = HttpClient(Android) {
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 20_000
            socketTimeoutMillis = 20_000
        }
    }

    fun init(context: Context) {
        val appContext = context.applicationContext
        PrivateUserData.init(appContext)
        PendingCheckoutStore.init(appContext)
        secureStorage = SecureTokenStorage(appContext)

        // One-time migration from the legacy plaintext preferences used by older builds.
        val legacy = appContext.getSharedPreferences("tani_auth", Context.MODE_PRIVATE)
        if (secureStorage.getString(ACCESS_TOKEN_KEY).isNullOrBlank()) {
            secureStorage.putString(ACCESS_TOKEN_KEY, legacy.getString(ACCESS_TOKEN_KEY, null))
            secureStorage.putString(REFRESH_TOKEN_KEY, legacy.getString(REFRESH_TOKEN_KEY, null))
            secureStorage.putString(USER_ID_KEY, legacy.getString(USER_ID_KEY, null))
        }
        legacy.edit { clear() }
    }

    var token: String?
        get() = secureStorage.getString(ACCESS_TOKEN_KEY)
        private set(value) {
            secureStorage.putString(ACCESS_TOKEN_KEY, value)
        }

    var refreshToken: String?
        get() = secureStorage.getString(REFRESH_TOKEN_KEY)
        private set(value) {
            secureStorage.putString(REFRESH_TOKEN_KEY, value)
        }

    var userId: String?
        get() = secureStorage.getString(USER_ID_KEY)
        private set(value) {
            secureStorage.putString(USER_ID_KEY, value)
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
        val changedOwner = userId != resolvedUserId
        if (changedOwner) {
            PrivateUserData.clearSessionData(userId)
            Cart.clear()
        }
        secureStorage.putString(ACCESS_TOKEN_KEY, accessToken)
        secureStorage.putString(REFRESH_TOKEN_KEY, refreshToken)
        secureStorage.putString(USER_ID_KEY, resolvedUserId)
        if (changedOwner) PushMessaging.onSessionChanged()
    }

    fun clearSession() {
        PrivateUserData.clearSessionData(userId)
        secureStorage.clear()
        PushMessaging.onSessionChanged()
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

        return@withLock runCatchingCancellable {
            val httpResponse = client.post("$URL/auth/v1/token?grant_type=refresh_token") {
                headers {
                    append("apikey", KEY)
                    append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                }
                setBody(buildJsonObject { put("refresh_token", currentRefreshToken) }.toString())
            }
            if (SessionRefreshPolicy.isSessionRejected(httpResponse.status.value)) {
                // الخادم ألغى الجلسة: نخرج المستخدم بدل أن تفشل كل الطلبات برسالة "تحقق من الاتصال".
                httpResponse.bodyAsText()
                Cart.clear()
                clearSession()
                return@withLock false
            }
            val response: AuthResponse = parse(httpResponse)

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
        runCatchingCancellable { jwtPayload(accessToken)?.get(claim)?.jsonPrimitive?.content }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }

    private fun jwtPayload(accessToken: String) = runCatchingCancellable {
        val payloadPart = accessToken.split('.').getOrNull(1) ?: return@runCatchingCancellable null
        val decoded = Base64.decode(
            payloadPart,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        json.parseToJsonElement(String(decoded, Charsets.UTF_8)).jsonObject
    }.getOrNull()

    suspend inline fun <reified T> get(
        path: String,
        query: String = ""
    ): T = parse(sendWithSessionRetry { client.get(restUrl(path, query)) { authHeaders(this) } })

    suspend inline fun <reified T> post(
        path: String,
        body: String
    ): T = parse(sendWithSessionRetry {
        client.post(restUrl(path)) {
            authHeaders(this)
            jsonBody(body)
        }
    })

    suspend inline fun <reified T> patch(
        path: String,
        query: String,
        body: String
    ): T = parse(sendWithSessionRetry {
        client.patch(restUrl(path, query)) {
            authHeaders(this)
            jsonBody(body)
        }
    })

    suspend fun delete(
        path: String,
        query: String
    ): Boolean {
        val response = sendWithSessionRetry {
            client.delete(restUrl(path, query)) {
                authHeaders(this)
                headers { append("Prefer", "return=minimal") }
            }
        }
        val text = response.bodyAsText()
        if (response.status.value !in 200..299) {
            error(errorMessage(text) ?: "تعذر تنفيذ العملية (${response.status.value})")
        }
        return true
    }

    @PublishedApi
    internal fun restUrl(path: String, query: String = ""): String =
        "$URL/rest/v1/$path${if (query.isBlank()) "" else "?$query"}"

    @PublishedApi
    internal fun HttpRequestBuilder.jsonBody(body: String) {
        headers {
            append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            append("Prefer", "return=representation")
        }
        setBody(body)
    }

    private suspend fun requireFreshSession() {
        if (!ensureFreshSession()) error(SESSION_REFRESH_FAILED)
    }

    /**
     * يرسل الطلب، وعند 401 يجدد الجلسة مرة واحدة ثم يعيد الطلب نفسه.
     * الطلب المجهول (بدون token) لا يحتاج تجديداً.
     */
    @PublishedApi
    internal suspend fun sendWithSessionRetry(request: suspend () -> HttpResponse): HttpResponse {
        // إذا ألغى الخادم الجلسة أثناء التجديد يصبح token فارغاً ويستمر الطلب كزائر.
        if (token != null && !ensureFreshSession() && token != null) error(SESSION_REFRESH_FAILED)
        val response = request()
        if (response.status.value == 401 && token != null && refreshToken != null) {
            response.bodyAsText() // استهلاك الاستجابة قبل إعادة المحاولة
            if (refreshSession(force = true)) return request()
        }
        return response
    }

    /** يستخرج رسالة الخطأ من رد Supabase (PostgREST وAuth وStorage تستخدم مفاتيح مختلفة). */
    @PublishedApi
    internal fun errorMessage(text: String): String? = runCatchingCancellable {
        val obj = json.parseToJsonElement(text).jsonObject
        sequenceOf("message", "msg", "error_description", "error")
            .mapNotNull { key -> obj[key]?.toString()?.trim('"') }
            .firstOrNull { it.isNotBlank() }
    }.getOrNull()

    suspend fun uploadAvatar(bytes: ByteArray): String {
        val uid = userId ?: error("تسجيل الدخول مطلوب")
        require(bytes.isNotEmpty()) { "الصورة غير صالحة" }
        require(bytes.size <= 5 * 1024 * 1024) { "حجم الصورة يجب ألا يتجاوز 5 ميجابايت" }

        requireFreshSession()

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
            val message = errorMessage(text)
            error(message ?: "تعذر رفع الصورة")
        }

        return "$URL/storage/v1/object/public/avatars/$objectPath?v=${System.currentTimeMillis()}"
    }


    /**
     * إرسال Analytics/Error telemetry بدون رمي خطأ للمستخدم.
     * لا نستخدم parse() هنا حتى لا يدخل تسجيل الخطأ في حلقة عند فشل شبكة المراقبة نفسها.
     */
    suspend fun telemetryInsert(path: String, body: String): Boolean = runCatchingCancellable {
        if (token != null && !ensureFreshSession()) return@runCatchingCancellable false

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

    suspend fun downloadPublicBytes(url: String): ByteArray? = runCatchingCancellable {
        val response = client.get(url)
        if (response.status.value !in 200..299) return@runCatchingCancellable null
        response.body<ByteArray>()
    }.getOrNull()

    suspend inline fun <reified T> invokeFunction(name: String, body: String): T {
        require(name.matches(Regex("^[a-z][a-z0-9-]+$")))
        check(ensureFreshSession()) { "تعذر تجديد جلسة الدخول" }
        val response = client.post("$URL/functions/v1/$name") {
            authHeaders(this)
            headers { append(HttpHeaders.ContentType, ContentType.Application.Json.toString()) }
            setBody(body)
        }
        val text = response.bodyAsText()
        check(response.status.value in 200..299) { "تعذر تنفيذ العملية (${response.status.value})" }
        return json.decodeFromString<T>(text)
    }

    suspend fun signOut() {
        token?.takeIf { it.isNotBlank() }?.let { revokeSessions(it) }
        clearSession()
    }

    /** يلغي جلسات الحساب على الخادم (أفضل جهد، والفشل لا يمنع المتابعة). */
    suspend fun revokeSessions(accessToken: String) {
        runCatchingCancellable {
            val response = client.post("$URL/auth/v1/logout") {
                headers {
                    append("apikey", KEY)
                    append("Authorization", "Bearer $accessToken")
                }
            }
            response.bodyAsText()
        }
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
        requireFreshSession()
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
            val message = errorMessage(text)
            error(message ?: "تعذر رفع الملف")
        }
        return objectPath
    }

    suspend fun deleteStorageObject(bucket: String, objectPath: String): Boolean {
        requireFreshSession()
        val response = client.delete("$URL/storage/v1/object/$bucket/$objectPath") {
            authHeaders(this)
        }
        val text = response.bodyAsText()
        if (response.status.value !in 200..299) {
            val message = errorMessage(text)
            error(message ?: "تعذر حذف الملف")
        }
        return true
    }

    suspend inline fun <reified T> parse(response: HttpResponse): T {
        val text = response.bodyAsText()

        if (response.status.value !in 200..299) {
            val message = errorMessage(text)

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
