package com.dream.ourphone.service

import java.util.LinkedList

data class StoredNotification(
    val packageName: String,
    val title: String?,
    val text: String?,
    val timestamp: Long,
    val ongoing: Boolean = false
)

object NotificationStore {
    private const val MAX_SIZE = 200
    private val notifications = LinkedList<StoredNotification>()

    @Synchronized
    fun add(notification: StoredNotification) {
        notifications.addFirst(notification)
        while (notifications.size > MAX_SIZE) {
            notifications.removeLast()
        }
    }

    @Synchronized
    fun getRecent(limit: Int = 20, packageFilter: String? = null): List<StoredNotification> {
        return notifications
            .let { list ->
                if (packageFilter != null) list.filter { it.packageName == packageFilter }
                else list
            }
            .take(limit)
    }

    @Synchronized
    fun clear() {
        notifications.clear()
    }

    @Synchronized
    fun size(): Int = notifications.size
}
