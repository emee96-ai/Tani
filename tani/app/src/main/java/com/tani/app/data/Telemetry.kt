package com.tani.app.data

import android.content.Context
import com.tani.app.BuildConfig
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

object Analytics {
    private val sessionId: String = UUID.randomUUID().toString()

    suspend fun track(
        eventName: String,
        screen: String? = null,
        entityType: String? = null,
        entityId: String? = null,
        metadata: JsonObject = buildJsonObject { }
    ) {
        if (!Regex("^[a-z0-9_]{2,80}$").matches(eventName)) return

        val body = buildJsonObject {
            Supabase.userId?.let { put("user_id", it) }
            put("session_id", sessionId)
            put("event_name", eventName)
            screen?.take(80)?.let { put("screen", it) }
            entityType?.take(50)?.let { put("entity_type", it) }
            entityId?.takeIf { UUID_PATTERN.matches(it) }?.let { put("entity_id", it) }
            put("metadata", metadata)
            put("app_version", BuildConfig.VERSION_NAME)
            put("platform", "android")
        }

        Supabase.telemetryInsert("app_events", body.toString())
    }

    internal fun currentSessionId(): String = sessionId

    private val UUID_PATTERN = Regex(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$"
    )
}

object ErrorMonitoring {
    private const val PREFS_NAME = "tani_error_monitoring"
    private const val PENDING_TYPE = "pending_type"
    private const val PENDING_MESSAGE = "pending_message"
    private const val PENDING_SOURCE = "pending_source"

    private lateinit var context: Context
    private var installed = false

    fun init(appContext: Context) {
        context = appContext.applicationContext
        if (installed) return
        installed = true

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(PENDING_TYPE, throwable::class.java.simpleName.take(120))
                    .putString(PENDING_MESSAGE, sanitize(throwable.message).take(500))
                    .putString(PENDING_SOURCE, "uncaught:${thread.name.take(60)}")
                    .commit()
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    suspend fun flushPendingCrash() {
        if (!::context.isInitialized) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val type = prefs.getString(PENDING_TYPE, null) ?: return
        val source = prefs.getString(PENDING_SOURCE, "uncaught") ?: "uncaught"
        val message = prefs.getString(PENDING_MESSAGE, "") ?: ""

        val sent = captureMessage(
            source = source,
            errorType = type,
            message = message,
            contextData = buildJsonObject { put("recovered_on_next_launch", true) }
        )
        if (sent) prefs.edit().clear().apply()
    }

    suspend fun capture(
        source: String,
        throwable: Throwable,
        contextData: JsonObject = buildJsonObject { }
    ): Boolean = captureMessage(
        source = source,
        errorType = throwable::class.java.simpleName.ifBlank { "Throwable" },
        message = throwable.message.orEmpty(),
        contextData = contextData
    )

    suspend fun captureMessage(
        source: String,
        errorType: String,
        message: String?,
        contextData: JsonObject = buildJsonObject { }
    ): Boolean {
        val body = buildJsonObject {
            Supabase.userId?.let { put("user_id", it) }
            put("session_id", Analytics.currentSessionId())
            put("source", sanitize(source).take(100).ifBlank { "application" })
            put("error_type", sanitize(errorType).take(120).ifBlank { "Error" })
            put("message", sanitize(message).take(500))
            put("context", contextData)
            put("app_version", BuildConfig.VERSION_NAME)
            put("platform", "android")
        }
        return Supabase.telemetryInsert("app_errors", body.toString())
    }

    private fun sanitize(value: String?): String {
        var text = value.orEmpty()
        text = text.replace(Regex("(?i)Bearer\\s+[A-Za-z0-9._~+/-]+=*"), "Bearer [redacted]")
        text = text.replace(Regex("eyJ[A-Za-z0-9_-]{20,}\\.[A-Za-z0-9_-]{20,}\\.[A-Za-z0-9_-]{10,}"), "[jwt-redacted]")
        text = text.replace(Regex("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", RegexOption.IGNORE_CASE), "[email-redacted]")
        text = text.replace(Regex("(?<!\\d)\\+?\\d[\\d\\s()-]{7,}\\d(?!\\d)"), "[phone-redacted]")
        text = text.replace(Supabase.KEY, "[key-redacted]")
        return text
    }
}
