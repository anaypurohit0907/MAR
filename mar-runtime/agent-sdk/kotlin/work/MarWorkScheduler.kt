package com.mar.agent.sdk.work

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * MarWorkScheduler: Translates Yaml/DSL Triggers into actual Android WorkManager policies.
 * Ensures tasks adhere to Battery Optimization restraints while guaranteeing execution.
 */
class MarWorkScheduler(private val context: Context) {

    private val workManager = WorkManager.getInstance(context)

    /**
     * Schedule a periodic execution for an agent (e.g. BirthdayAgent running daily).
     */
    fun schedulePeriodicAgent(agentId: String, repeatIntervalHours: Long) {
        // Build constraint graph according to Android doze-safe rules
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true) // Don't run LLMs on 5% battery
            .setRequiresDeviceIdle(false)   // Can run while user is using phone
            .build()

        val workRequest = PeriodicWorkRequestBuilder<MarAgentWorker>(
            repeatIntervalHours, TimeUnit.HOURS
        )
            .setInputData(workDataOf(MarAgentWorker.KEY_AGENT_ID to agentId))
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .addTag("mar_agent_$agentId")
            .build()

        workManager.enqueueUniquePeriodicWork(
            "unique_agent_$agentId",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    /**
     * Trigger an Agent immediately (e.g. via quick settings Intent or Termux CLI).
     */
    fun executeAgentNow(agentId: String) {
        val workRequest = OneTimeWorkRequestBuilder<MarAgentWorker>()
            .setInputData(workDataOf(MarAgentWorker.KEY_AGENT_ID to agentId))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST) // Use Android 12+ Expedited Job if quota allows
            .build()

        workManager.enqueue(workRequest)
    }

    /**
     * Stops an executing agent or cancels its future schedule.
     */
    fun cancelAgent(agentId: String) {
        workManager.cancelAllWorkByTag("mar_agent_$agentId")
    }
}
