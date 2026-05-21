package com.mar.agent.sdk.tools

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class MarNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        Log.i(TAG, "NotificationListener connected")
        NotificationListenerManager.isConnected = true

        // Seed buffer with existing notifications (e.g. Spotify already playing before NLS connected)
        try {
            for (sbn in activeNotifications) {
                val extras = sbn.notification.extras
                val event = NotificationEvent(
                    packageName = sbn.packageName,
                    title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString(),
                    text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString(),
                    subText = extras.getCharSequence(android.app.Notification.EXTRA_SUB_TEXT)?.toString()
                        ?: extras.getCharSequence(android.app.Notification.EXTRA_INFO_TEXT)?.toString(),
                    category = sbn.notification.category,
                    key = sbn.key,
                    timestamp = sbn.postTime
                )
                NotificationListenerManager.addNotification(event)
                Log.i(TAG, "Seeded existing: ${sbn.packageName} text=${event.text?.take(60)}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to seed active notifications: ${e.message}")
        }

        try { NotificationTriggerRouter.registerAllFromFilesDir(this) }
        catch (e: Exception) { Log.e(TAG, "Trigger registration failed: ${e.message}") }
    }

    override fun onListenerDisconnected() {
        Log.w(TAG, "NotificationListener disconnected")
        NotificationListenerManager.isConnected = false
    }

    private val recentKeys = mutableSetOf<String>()
    private var lastCleanup = 0L

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            val pkg = sbn.packageName
            val key = sbn.key

            // Skip own notifications entirely — prevents trigger loops
            if (pkg == packageName) return

            // Dedup: same notification key within 500ms (apps like Spotify fire 18× for one update)
            val now = System.currentTimeMillis()
            if (now - lastCleanup > 1000) { recentKeys.clear(); lastCleanup = now }
            if (!recentKeys.add(key)) return

            val notification = sbn.notification
            val extras = notification.extras
            val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()
            val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()
            val subText = extras.getCharSequence(android.app.Notification.EXTRA_SUB_TEXT)?.toString()
                ?: extras.getCharSequence(android.app.Notification.EXTRA_INFO_TEXT)?.toString()
                ?: extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)?.toString()
            val category = notification.category

            Log.i(TAG, "Notification from $pkg: title=$title subText=$subText text=$text")

            val event = NotificationEvent(
                packageName = pkg,
                title = title,
                text = text,
                subText = subText,
                category = category,
                key = key,
                timestamp = now
            )
            NotificationListenerManager.addNotification(event)
            NotificationTriggerRouter.match(this, event)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing notification: ${e.message}")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        Log.d(TAG, "Notification removed: ${sbn.packageName}")
    }

    companion object {
        private const val TAG = "MAR_NLS"
    }
}
