package com.mar.agent.sdk.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlin.random.Random

object AgentNotificationManager {

    private const val CHANNEL_ID = "mar_agent_controls"
    private const val ACTION_CHANNEL_ID = "mar_agent_actions"
    const val ACTION_PAUSE = "com.mar.action.PAUSE_AGENT"
    const val ACTION_RESUME = "com.mar.action.RESUME_AGENT"
    
    /**
     * Broadcasts progress locally to be displayed in the App UI directly (Active Agents block)
     * Drops the generic status bar notification in favor of direct active state UI mapping.
     */
    fun showAgentProgressNotification(context: Context, agentId: String, statusText: String) {
        val intent = Intent("com.mar.agent.PROGRESS").apply {
            putExtra("agentId", agentId)
            putExtra("status", statusText)
            setPackage(context.packageName) // Enforce explicit internal mapping for receiver boundary security
        }
        context.sendBroadcast(intent)
    }

    fun clearAgentNotification(context: Context, agentId: String) {
        val intent = Intent("com.mar.agent.COMPLETE").apply {
            putExtra("agentId", agentId)
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
        
        // Backup to clear any lingering notification manager state for backward compat.
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(agentId.hashCode())
    }

    fun showAgentControlNotification(context: Context, agentId: String, isRunning: Boolean) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Agent Controls",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        // Setup Play/Pause actions
        val actionIntent = Intent(if (isRunning) ACTION_PAUSE else ACTION_RESUME).apply {
            putExtra("agentId", agentId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            agentId.hashCode(),
            actionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val actionText = if (isRunning) "Pause" else "Resume"
        val statusText = if (isRunning) "Agent $agentId is running..." else "Agent $agentId paused."

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("MAR Agent: $agentId")
            .setContentText(statusText)
            .addAction(android.R.drawable.ic_media_pause, actionText, pendingIntent)
            .setOngoing(isRunning)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        notificationManager.notify(agentId.hashCode(), builder.build())
    }

    /**
     * General-purpose delegator: Captures UI-blocking intents (like sending emails or Telegram)
     * and wraps them in a polite, high-priority notification for the user to approve at their leisure.
     */
    fun showActionRequiredNotification(context: Context, intent: Intent, component: String, description: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ACTION_CHANNEL_ID,
                "Agent Action Approvals",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                this.description = "Notifications requiring user input or review from AI Agents"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            Random.nextInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, ACTION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now) // Generic action icon
            .setContentTitle("Agent Task Ready: $component")
            .setContentText(description)
            .setStyle(NotificationCompat.BigTextStyle().bigText(description))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        notificationManager.notify(Random.nextInt(), builder.build())
    }
}
