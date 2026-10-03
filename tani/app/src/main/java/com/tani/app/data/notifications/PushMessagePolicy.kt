package com.tani.app.data.notifications

object PushMessagePolicy {
    const val ACTION = "com.tani.app.OPEN_NOTIFICATION"
    const val USER_ID = "tani_push_user_id"
    const val NOTIFICATION_ID = "tani_push_notification_id"
    private val uuid = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    fun canDisplay(currentUser: String?, messageUser: String?, notificationId: String?): Boolean =
        currentUser != null && messageUser == currentUser && uuid.matches(currentUser) &&
            notificationId != null && uuid.matches(notificationId)
}
