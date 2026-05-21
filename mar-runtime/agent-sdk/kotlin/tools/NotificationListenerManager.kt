package com.mar.agent.sdk.tools

data class NotificationEvent(
    val packageName: String,
    val title: String?,
    val text: String?,
    val subText: String? = null,
    val category: String?,
    val key: String,
    val timestamp: Long = System.currentTimeMillis()
)

object NotificationListenerManager {
    @Volatile
    var isConnected: Boolean = false

    private val recentNotifications = mutableListOf<NotificationEvent>()
    private const val MAX_RECENT = 50

    fun addNotification(event: NotificationEvent) {
        synchronized(recentNotifications) {
            // Dedup by key — replace existing entry if same key
            val idx = recentNotifications.indexOfLast { it.key == event.key }
            if (idx >= 0) recentNotifications.removeAt(idx)
            recentNotifications.add(event)
            if (recentNotifications.size > MAX_RECENT) {
                recentNotifications.removeAt(0)
            }
        }
    }

    fun getRecentNotifications(count: Int = 10): List<NotificationEvent> {
        synchronized(recentNotifications) {
            return recentNotifications.takeLast(count).toList()
        }
    }

    fun getLatestNotification(): NotificationEvent? {
        synchronized(recentNotifications) {
            return recentNotifications.lastOrNull()
        }
    }

    fun clear() {
        synchronized(recentNotifications) { recentNotifications.clear() }
    }
}
