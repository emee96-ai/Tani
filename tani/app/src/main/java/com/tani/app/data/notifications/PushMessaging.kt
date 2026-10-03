package com.tani.app.data.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.tani.app.BuildConfig
import com.tani.app.data.Supabase
import com.tani.app.util.runCatchingCancellable
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object PushMessaging {
    const val CHANNEL_ID = "tani_updates"
    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val epoch = AtomicLong()
    @Volatile var isConfigured = false
        private set

    fun initialize(context: Context) {
        appContext = context.applicationContext
        if (listOf(BuildConfig.FIREBASE_API_KEY, BuildConfig.FIREBASE_APPLICATION_ID,
                BuildConfig.FIREBASE_PROJECT_ID, BuildConfig.FIREBASE_SENDER_ID).any { it.isBlank() }) return
        isConfigured = runCatching {
            if (FirebaseApp.getApps(appContext).isEmpty()) {
                FirebaseApp.initializeApp(appContext, FirebaseOptions.Builder()
                    .setApiKey(BuildConfig.FIREBASE_API_KEY)
                    .setApplicationId(BuildConfig.FIREBASE_APPLICATION_ID)
                    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                    .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID).build())
            }
            FirebaseMessaging.getInstance().isAutoInitEnabled = false
            true
        }.getOrDefault(false)
        if (Build.VERSION.SDK_INT >= 26) {
            appContext.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "تحديثات تاني", NotificationManager.IMPORTANCE_DEFAULT))
        }
        onSessionChanged()
    }

    fun hasPermission(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context,
            Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun onSessionChanged() {
        epoch.incrementAndGet()
        if (!::appContext.isInitialized) return
        NotificationManagerCompat.from(appContext).cancelAll()
        if (!isConfigured) return
        if (Supabase.hasStoredSession() && Supabase.userId != null) registerCurrent() else {
            scope.launch {
                mutex.withLock {
                    if (!Supabase.hasStoredSession()) runCatchingCancellable {
                        withTimeoutOrNull(3_000) { FirebaseMessaging.getInstance().deleteToken().awaitValue() }
                    }
                }
            }
        }
    }

    fun registerCurrent() = registerToken(null)
    fun onNewToken(token: String) = registerToken(token)

    private fun registerToken(suppliedToken: String?) {
        if (!isConfigured || !hasPermission(appContext) || !Supabase.hasStoredSession()) return
        val owner = Supabase.userId ?: return
        val generation = epoch.get()
        scope.launch {
            mutex.withLock {
                runCatchingCancellable {
                    withTimeoutOrNull(8_000) {
                        if (!eligible(owner, generation)) return@withTimeoutOrNull
                        val token = suppliedToken ?: FirebaseMessaging.getInstance().token.awaitValue()
                        if (eligible(owner, generation)) PushRegistrationGatewayProvider.gateway.register("fcm", token)
                    }
                }
            }
        }
    }

    private fun eligible(owner: String, generation: Long): Boolean =
        generation == epoch.get() && Supabase.userId == owner && Supabase.hasStoredSession() && hasPermission(appContext)

    suspend fun beforeSignOut() {
        epoch.incrementAndGet()
        if (!::appContext.isInitialized) return
        NotificationManagerCompat.from(appContext).cancelAll()
        if (!isConfigured) return
        withTimeoutOrNull(3_000) {
            mutex.withLock {
                runCatchingCancellable {
                    val messaging = FirebaseMessaging.getInstance()
                    val token = messaging.token.awaitValue()
                    PushRegistrationGatewayProvider.gateway.unregister("fcm", token)
                    messaging.deleteToken().awaitValue()
                }
            }
        }
    }
}

private suspend fun <T> Task<T>.awaitValue(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (continuation.isActive) {
            if (task.isSuccessful) continuation.resume(task.result)
            else continuation.resumeWithException(task.exception ?: IllegalStateException("Push task failed"))
        }
    }
}
