package com.mar.agent.sdk.cli

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mar.agent.sdk.work.MarWorkScheduler

/**
 * Allows triggering and inspecting Agents via ADB or Termux:
 * Example termux trigger:
 * am broadcast -a com.mar.cli.TRIGGER_AGENT --es agent_id "BirthdayGreeter"
 */
class TermuxCliReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.mar.cli.TRIGGER_AGENT") {
            val agentId = intent.getStringExtra("agent_id")
            if (agentId != null) {
                Log.i("MAR_CLI", "Received Termux request to execute agent: $agentId")
                
                // Immediately enqueue the agent
                val scheduler = MarWorkScheduler(context)
                scheduler.executeAgentNow(agentId)
                
                // Optional: set result code for standard ADB debugging
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
