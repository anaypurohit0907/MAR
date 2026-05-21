package com.mar.agent.sdk.cli

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mar.agent.sdk.db.WorkflowRepository
import com.mar.agent.sdk.tools.NotificationTriggerRouter
import com.mar.agent.sdk.work.MarWorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TermuxCliReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        NotificationTriggerRouter.registerAllFromFilesDir(context)
        if (intent.action == "com.mar.cli.TRIGGER_AGENT") {
            val agentId = intent.getStringExtra("agent_id")
            if (agentId != null) {
                Log.i("MAR_CLI", "Received Termux request to execute agent: $agentId")
                CoroutineScope(Dispatchers.IO).launch {
                    val repo = WorkflowRepository(context)
                    repo.importFromFilesDir()
                    val yamlWorkflow = repo.loadYaml(agentId)
                    val scheduler = MarWorkScheduler(context)
                    scheduler.executeAgentNow(agentId, yamlWorkflow = yamlWorkflow)
                }
                resultCode = android.app.Activity.RESULT_OK
                resultData = "Agent $agentId queued successfully"
            }
        } else if (intent.action == "com.mar.cli.CANCEL_AGENT") {
            val agentId = intent.getStringExtra("agent_id")
            if (agentId != null) {
                Log.i("MAR_CLI", "Received Termux request to cancel agent: $agentId")
                MarWorkScheduler(context).cancelAgent(agentId)
                resultCode = android.app.Activity.RESULT_OK
                resultData = "Agent $agentId cancelled successfully"
            }
        }
    }
}
