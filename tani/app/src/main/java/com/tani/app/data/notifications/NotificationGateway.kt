package com.tani.app.data.notifications

import com.tani.app.data.growth.NotificationItem
import com.tani.app.data.growth.NotificationPreferences
import com.tani.app.data.repository.GrowthRepository

interface NotificationGateway {
    suspend fun inbox(): List<NotificationItem>
    suspend fun markRead(id: String)
    suspend fun preferences(): NotificationPreferences
    suspend fun savePreferences(value: NotificationPreferences): NotificationPreferences
}

class SupabaseNotificationGateway(
    private val repository: GrowthRepository = GrowthRepository()
) : NotificationGateway {
    override suspend fun inbox() = repository.notifications()
    override suspend fun markRead(id: String) = repository.markNotificationRead(id)
    override suspend fun preferences() = repository.notificationPreferences()
    override suspend fun savePreferences(value: NotificationPreferences) = repository.saveNotificationPreferences(value)
}

object NotificationGatewayProvider {
    @Volatile var gateway: NotificationGateway = SupabaseNotificationGateway()
}
