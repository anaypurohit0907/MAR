package com.mar.agent.sdk.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.mar.agent.sdk.work.MarWorkScheduler

object AgentNotificationManager {

    private const val CHANNEL_ID = "mar_agent_controls"
    const val ACTION_PAUSE = "com.mar.action.PAUSE_AGENT"
    const val ACTION_RESUME = "com.mar.action.RESUME_AGENT"
    
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
            // Typically this intent should point to a BroadcastReceiver to handle the action
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
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle("MAR Agent: $agentId")
            .setContentText(statusText)
            .addAction(android.R.drawable.ic_media_pause, actionText, pendingIntent)
            .setOngoing(isRunning)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        notificationManager.notify(agentId.hashCode(), builder.build())
    }
}
