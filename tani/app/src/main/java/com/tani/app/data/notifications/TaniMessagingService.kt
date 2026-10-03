package com.tani.app.data.notifications

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.tani.app.MainActivity
import com.tani.app.data.Supabase

class TaniMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) = PushMessaging.onNewToken(token)

    override fun onMessageReceived(message: RemoteMessage) {
        val owner = message.data["user_id"]
        val id = message.data["notification_id"]
        if (!PushMessaging.isConfigured || !Supabase.hasStoredSession() || !PushMessaging.hasPermission(this) ||
            !PushMessagePolicy.canDisplay(Supabase.userId, owner, id)) return
        val intent = Intent(this, MainActivity::class.java).apply {
            action = PushMessagePolicy.ACTION
            data = Uri.parse("tani-notification://$id")
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(PushMessagePolicy.USER_ID, owner)
            putExtra(PushMessagePolicy.NOTIFICATION_ID, id)
        }
        val pending = PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, PushMessaging.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("تاني")
            .setContentText("لديك تحديث جديد. افتحي تاني لمتابعته.")
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true).setContentIntent(pending).build()
        try {
            NotificationManagerCompat.from(this).notify(id, 0, notification)
        } catch (_: SecurityException) {
            // Permission may have been revoked between the check and notify.
        }
    }
}
