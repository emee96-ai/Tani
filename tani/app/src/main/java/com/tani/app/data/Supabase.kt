package com.tani.app.data

import android.content.Context
import android.content.SharedPreferences
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

object Supabase {

    const val URL = "https://sihttimibjzoahvwuwbm.supabase.co"
    const val KEY = "sb_publishable_9syQMNqMr0q9V0_Z4W-jvA_OQjhHj1Y"

    private lateinit var prefs: SharedPreferences

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
        get() = prefs.getString("access_token", null)
        set(value) {
            prefs.edit().putString("access_token", value).apply()
        }

    var userId: String?
        get() = prefs.getString("user_id", null)
        set(value) {
            prefs.edit().putString("user_id", value).apply()
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

    suspend inline fun <reified T> get(
        path: String,
        query: String = ""
    ): T {
        val url =
            "$URL/rest/v1/$path${if (query.isBlank()) "" else "?$query"}"

        val response = client.get(url) {
            authHeaders(this)
        }

        return parse(response)
    }

    suspend inline fun <reified T> post(
        path: String,
        body: String
    ): T {
        val response = client.post("$URL/rest/v1/$path") {
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

        return parse(response)
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

    suspend inline fun <reified T> parse(
        response: HttpResponse
    ): T {
        val text = response.bodyAsText()

        if (response.status.value !in 200..299) {
            val message = runCatching {
                json.parseToJsonElement(text)
                    .jsonObject["message"]
                    ?.toString()
                    ?.trim('"')
            }.getOrNull()

            error(
                message
                    ?: "Supabase ${response.status.value}: $text"
            )
        }

        return json.decodeFromString(text)
    }
}
