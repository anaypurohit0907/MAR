package com.mar.agent.sdk.tools

import android.content.Context
import android.util.Log
import com.mar.agent.sdk.db.WorkflowRepository
import com.mar.agent.sdk.work.MarWorkScheduler
import com.mar.agent.sdk.yaml.parser.MarYamlParser
import com.mar.agent.sdk.yaml.parser.NotificationTrigger
import java.io.File
import java.io.FileInputStream

object NotificationTriggerRouter {
    private data class Registration(val agentId: String, val trigger: NotificationTrigger, val yaml: String)
    private val registrations = mutableListOf<Registration>()
    private val lastTriggerTime = mutableMapOf<String, Long>()

    fun register(agentId: String, trigger: NotificationTrigger, yaml: String) {
        if (registrations.any { it.agentId == agentId && it.trigger == trigger }) return
        registrations.add(Registration(agentId, trigger, yaml))
        Log.i("MAR_TriggerRouter", "Registered notification trigger for agent: $agentId")
    }

    fun size(): Int = registrations.size

    fun clear() {
        registrations.clear()
    }

    fun match(context: Context, event: NotificationEvent): Boolean {
        if (event.packageName.isBlank()) return false
        if (event.packageName == context.packageName) return false // prevent self-trigger loops
        val now = System.currentTimeMillis()
        for (reg in registrations) {
            val last = lastTriggerTime[reg.agentId] ?: 0L
            val t = reg.trigger
            val cooldown = (t.cooldownSeconds ?: 10L) * 1000L
            if (now - last < cooldown) continue 
            if (t.packageName != null && t.packageName != event.packageName) continue
            if (t.textMatch != null) {
                val regex = try { t.textMatch.toRegex() } catch (e: Exception) { null }
                if (regex == null || !regex.containsMatchIn(event.text ?: "")) continue
            }
            if (t.category != null && t.category != event.category) continue

            // Time Window check
            if (t.timeWindowStart != null && t.timeWindowEnd != null) {
                try {
                    val nowTime = java.time.LocalTime.now()
                    val start = java.time.LocalTime.parse(t.timeWindowStart)
                    val end = java.time.LocalTime.parse(t.timeWindowEnd)
                    if (start.isBefore(end)) {
                        if (nowTime.isBefore(start) || nowTime.isAfter(end)) continue
                    } else {
                        // Spans midnight (e.g. 22:00 to 06:00)
                        if (nowTime.isAfter(end) && nowTime.isBefore(start)) continue
                    }
                } catch (e: Exception) {
                    Log.w("MAR_TriggerRouter", "Invalid time window config: ${e.message}")
                }
            }

            var yaml = reg.yaml
            yaml = yaml.replace("{trigger.package}", event.packageName)
            yaml = yaml.replace("{trigger.title}", event.title ?: "")
            yaml = yaml.replace("{trigger.text}", event.text ?: "")
            yaml = yaml.replace("{trigger.category}", event.category ?: "")

            Log.i("MAR_TriggerRouter", "Triggered ${reg.agentId} from ${event.packageName}: ${event.text?.take(60)}")
            lastTriggerTime[reg.agentId] = now
            MarWorkScheduler(context).executeAgentNow(reg.agentId, yamlWorkflow = yaml)
            return true
        }
        return false
    }

    fun registerAllFromFilesDir(context: Context) {
        val repo = WorkflowRepository(context)
        val enabledWorkflows = repo.getEnabledBlocking()
        val enabledIds = enabledWorkflows.map { it.id }.toSet()
        val filesDir = context.filesDir ?: return
        val yamlFiles = filesDir.listFiles { f -> f.name.endsWith(".yaml") } ?: return
        val parser = MarYamlParser()
        for (file in yamlFiles) {
            val agentId = file.nameWithoutExtension
            if (agentId !in enabledIds) continue
            try {
                val text = file.readText()
                val config = parser.parse(text.byteInputStream())
                for (trigger in config.triggers) {
                    if (trigger.type == "notification" && trigger.notification != null) {
                        register(agentId, trigger.notification, text)
                    }
                }
            } catch (e: Exception) {
                Log.w("MAR_TriggerRouter", "Failed to parse ${file.name}: ${e.message}")
            }
        }
    }
}
