package com.mar.agent.sdk.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mar.agent.sdk.tools.NotificationTriggerRouter

class TriggerSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.i("MAR_TriggerSync", "Re-registering notification triggers...")
        NotificationTriggerRouter.clear()
        NotificationTriggerRouter.registerAllFromFilesDir(applicationContext)
        return Result.success()
    }
}
