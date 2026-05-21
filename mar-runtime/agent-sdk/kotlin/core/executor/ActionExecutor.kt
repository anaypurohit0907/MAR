package com.mar.agent.sdk.core.executor

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import android.util.Log
import com.mar.agent.sdk.tools.AccessibilityServiceManager
import com.mar.agent.sdk.tools.ContentQueryTool
import com.mar.agent.sdk.tools.NotificationListenerManager
import com.mar.agent.sdk.ui.AgentNotificationManager

/**
 * ActionExecutor: Decouples OS intent routing and physical hardware interaction
 * from the worker logic.
 */
class ActionExecutor(private val context: Context) {

    suspend fun executeActions(jsonPayload: String): Map<String, Any?> {
        val results = mutableMapOf<String, Any?>()
        try {
            val jsonArray = JSONArray(jsonPayload)
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                if (!item.has("action")) continue
                
                val action = item.getString("action")
                results[action] = executeSingleAction(action, item)
            }
        } catch(e: Exception) {
            Log.e("MAR_ActionExecutor", "Failed to parse or execute JSON payload: ${e.message}")
        }
        return results
    }

    suspend fun executeSingleAction(action: String, item: org.json.JSONObject): Any? {
        try {
            return when (action) {
                "ObserveScreen" -> {
                    Log.i("MAR_ActionExecutor", "Triggering Vision Screen Capture & OCR Parsing...")
                    val tool = com.mar.agent.sdk.tools.ObserveScreenTool(context)
                    tool.execute(emptyMap())
                }
                "CalendarQuery" -> {
                    val query = item.optString("query")
                    val type = item.optString("event_type").ifEmpty { item.optString("eventType") }
                    val tool = com.mar.agent.sdk.tools.CalendarQueryTool(context)
                    val result = tool.queryEvents(query, type)
                    Log.i("MAR_ActionExecutor", "CalendarQuery result: $result")
                    result // null = no events found, WorkflowRunner routes to on_empty
                }
                "UITapper" -> {
                    val app = item.optString("app")
                    val contact = item.optString("target_contact")
                    val msg = item.optString("input_text")
                    // sub_action comes from params.action in YAML, forwarded by WorkflowRunner
                    val tapAction = item.optString("sub_action", item.optString("action", ""))
                    Log.i("MAR_ActionExecutor", "UITapper $tapAction for $app")

                    if (tapAction == "send" || tapAction == "send_message") {
                        try {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                if (app.isNotBlank()) setPackage(app)
                                putExtra(Intent.EXTRA_TEXT, msg)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            AgentNotificationManager.showActionRequiredNotification(
                                context = context, intent = intent,
                                component = app.ifBlank { "Messaging App" },
                                description = "Draft ready for $contact. Tap to review & send.",
                                messageBody = msg
                            )
                            Log.i("MAR_ActionExecutor", "Review notification shown for $app -> $contact")
                        } catch (e: Exception) {
                            Log.e("MAR_ActionExecutor", "Review notification failed: ${e.message}")
                        }
                        true
                    } else {
                        Log.w("MAR_ActionExecutor", "Unknown UITapper action: $tapAction")
                        false
                    }
                }
                "ui_compose" -> {
                    val targetApp = item.optString("target_app")
                    val contact = item.optString("contact")
                    val message = item.optString("message")
                    val a11y = AccessibilityServiceManager.activeService

                    if (a11y == null) {
                        Log.w("MAR_ActionExecutor", "ui_compose requires AccessibilityService to be enabled")
                        false
                    } else if (targetApp.isBlank()) {
                        Log.w("MAR_ActionExecutor", "ui_compose missing target_app")
                        false
                    } else {
                        Log.i("MAR_ActionExecutor", "ui_compose: opening $targetApp for $contact")
                        a11y.scheduleCompose(targetApp, contact, message)
                        mapOf("status" to "composed", "app" to targetApp, "contact" to contact)
                    }
                }
                "hardware_flashlight" -> {
                    try {
                        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                        val cameraIds = cameraManager.cameraIdList
                        if (cameraIds.isEmpty()) {
                            Log.w("MAR_ActionExecutor", "No camera found for flashlight")
                            return@executeSingleAction false
                        }
                        val cameraId = cameraIds[0]
                        val state = item.optString("state", "on") == "on"
                        cameraManager.setTorchMode(cameraId, state)
                        true
                    } catch (e: SecurityException) {
                        Log.e("MAR_ActionExecutor", "Flashlight requires CAMERA permission: ${e.message}")
                        false
                    }
                }
                "launch_app" -> {
                    if (item.optString("target") == "settings") {
                        val intent = Intent(Settings.ACTION_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                        context.startActivity(intent)
                    }
                    true
                }
                "set_timer" -> {
                    val seconds = item.optInt("seconds", -1)
                    if (seconds <= 0) {
                        Log.w("MAR_ActionExecutor", "set_timer missing or invalid 'seconds'")
                        return@executeSingleAction false
                    }
                    val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                        putExtra(AlarmClock.EXTRA_MESSAGE, item.optString("message", "Agent Timer"))
                        putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                    }
                    context.startActivity(intent)
                    true
                }
                "open_url" -> {
                    val url = item.optString("url")
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }

                    // Delegate securely instead of interrupting. 
                    AgentNotificationManager.showActionRequiredNotification(
                        context = context,
                        intent = intent,
                        component = "Web Browser",
                        description = "Tap to open: $url"
                    )

                    Log.i("MAR_ActionExecutor", "Delegated Browser Intent to Notification")
                    true
                }
                "content_query" -> {
                    val uri = item.optString("uri", "")
                    if (uri.isBlank()) {
                        Log.w("MAR_ActionExecutor", "content_query missing 'uri'")
                        false
                    } else {
                        val projection = item.optJSONArray("projection")
                            ?.let { arr -> (0 until arr.length()).map { arr.getString(it) } }
                        val selection = item.optString("selection", "")
                            .ifBlank { null }
                        val selectionArgs = item.optJSONArray("selection_args")
                            ?.let { arr -> (0 until arr.length()).map { arr.getString(it) } }
                            ?.ifEmpty { null }
                        val sortOrder = item.optString("sort_order", "")
                            .ifBlank { null }

                        val tool = ContentQueryTool(context)
                        val raw = tool.query(uri, projection, selection, selectionArgs, sortOrder)
                        val json = org.json.JSONObject(raw)
                        mapOf(
                            "json" to raw,
                            "count" to json.optInt("count", 0).toString()
                        )
                    }
                }
                "read_notifications" -> {
                    val count = item.optInt("count", 5)
                    val packageFilter = item.optString("package_filter", null)?.takeIf { it.isNotBlank() }
                    if (!NotificationListenerManager.isConnected) {
                        Log.w("MAR_ActionExecutor", "NotificationListenerService not connected")
                        mapOf("status" to "error", "reason" to "NotificationListenerService not enabled")
                    } else {
                        val notifications = NotificationListenerManager.getRecentNotifications(count)
                            .filter { n -> packageFilter == null || n.packageName == packageFilter }
                            .let { filtered ->
                                if (packageFilter != null) {
                                    // Only keep the most recent per filtered package
                                    filtered.groupBy { it.packageName }.mapValues { it.value.last() }.values.toList()
                                } else filtered
                            }
                        val maxLen = item.optInt("max_text_length", 200)
                        val notifsJson = JSONArray()
                        notifications.forEach { n ->
                            val nObj = JSONObject().apply {
                                put("package", n.packageName)
                                put("title", n.title ?: "")
                                put("text", n.text?.take(maxLen) ?: "")
                                put("category", n.category ?: "")
                                put("timestamp", n.timestamp)
                            }
                            notifsJson.put(nObj)
                        }
                        val joinedTexts = notifications.joinToString(" | ") { n ->
                            listOfNotNull(
                                n.title?.takeIf { it.isNotBlank() },
                                n.text?.take(maxLen)?.takeIf { it.isNotBlank() }
                            ).joinToString(" — ")
                        }
                        mapOf(
                            "status" to "ok",
                            "count" to notifsJson.length(),
                            "notifications" to notifsJson.toString(),
                            "texts" to joinedTexts
                        )
                    }
                }
                "read_sms" -> {
                    try {
                        val limit = item.optInt("limit", 5)
                        val tool = ContentQueryTool(context)
                        val raw = tool.query(
                            "content://sms/inbox",
                            listOf("address", "body", "date"),
                            null, null, "date DESC LIMIT $limit"
                        )
                        val json = org.json.JSONObject(raw)
                        mapOf("json" to raw, "count" to json.optInt("count", 0).toString())
                    } catch (e: SecurityException) {
                        Log.e("MAR_ActionExecutor", "read_sms requires READ_SMS permission: ${e.message}")
                        mapOf("status" to "error", "reason" to "READ_SMS permission required")
                    } catch (e: Exception) {
                        Log.e("MAR_ActionExecutor", "read_sms failed: ${e.message}")
                        mapOf("status" to "error", "reason" to e.message)
                    }
                }
                "query_contacts" -> {
                    val name = item.optString("name", "")
                    val tool = ContentQueryTool(context)
                    val raw = tool.query(
                        "content://com.android.contacts/data",
                        listOf("display_name", "data1", "mimetype"),
                        if (name.isNotBlank()) "display_name LIKE ?" else null,
                        if (name.isNotBlank()) listOf("%$name%") else null,
                        null
                    )
                    val json = org.json.JSONObject(raw)
                    mapOf("json" to raw, "count" to json.optInt("count", 0).toString())
                }
                "notify" -> {
                    val title = item.optString("title", "MAR Agent")
                    val message = item.optString("message", "")
                    val component = item.optString("component", "Agent")

                    try {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            setPackage("com.android.settings")
                        }
                        AgentNotificationManager.showActionRequiredNotification(
                            context = context, intent = intent,
                            component = component,
                            description = title,
                            messageBody = message
                        )
                        Log.i("MAR_ActionExecutor", "Notified: $title")
                        mapOf("status" to "sent", "title" to title)
                    } catch (e: Exception) {
                        Log.e("MAR_ActionExecutor", "Notify failed: ${e.message}")
                        false
                    }
                }
                "web_fetch" -> {
                    val url = item.optString("url", "")
                    if (url.isBlank()) {
                        mapOf("status" to "error", "reason" to "url param required")
                    } else {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            var lastError: String? = null
                            for (attempt in 1..3) {
                                try {
                                    val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                                    conn.connectTimeout = 15000
                                    conn.readTimeout = 30000
                                    conn.requestMethod = "GET"
                                    conn.setRequestProperty("User-Agent", "MAR-Agent/1.0")
                                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                                    val plain = text.replace(Regex("<[^>]+>"), " ")
                                        .replace(Regex("\\s+"), " ")
                                        .trim()
                                    Log.i("MAR_ActionExecutor", "Fetched ${url.take(80)}: ${plain.take(100)}...")
                                    return@withContext mapOf("status" to "ok", "text" to plain.take(5000), "url" to url)
                                } catch (e: Exception) {
                                    lastError = e.message
                                    Log.w("MAR_ActionExecutor", "web_fetch attempt $attempt/3 failed: ${e.message}")
                                    if (attempt < 3) Thread.sleep(1000L * attempt)
                                }
                            }
                            mapOf("status" to "error", "reason" to lastError)
                        }
                    }
                }
                "diagnose" -> {
                    val notifs = NotificationListenerManager.getRecentNotifications(50)
                    val logLines = mutableListOf<String>()
                    logLines.add("=== DIAGNOSE ===")
                    logLines.add("NLS connected: ${NotificationListenerManager.isConnected}")
                    logLines.add("Notifications in buffer: ${notifs.size}")
                    notifs.forEachIndexed { i, n ->
                        logLines.add("  [$i] ${n.packageName} title=${n.title} text=${n.text?.take(60)} ts=${n.timestamp}")
                    }
                    logLines.add("Registered triggers: ${com.mar.agent.sdk.tools.NotificationTriggerRouter.size()}")
                    logLines.add("Model loaded: ${com.mar.runtime.core.MultiAgentRuntimeManager.isModelLoaded}")
                    logLines.add("Engine initialized: ${com.mar.runtime.core.MultiAgentRuntimeManager.isEngineInitialized}")
                    val prefs = context.getSharedPreferences("com.mar.demo_preferences", Context.MODE_PRIVATE)
                    logLines.add("API key set: ${!prefs.getString("api_key", "").isNullOrBlank()}")
                    logLines.add("Base URL: ${prefs.getString("base_url", "default")}")
                    logLines.add("Model: ${prefs.getString("model_name", "default")}")
                    logLines.add("Model path: ${com.mar.runtime.core.MultiAgentRuntimeManager.findLocalModelPath(context)}")
                    logLines.forEach { Log.i("MAR_DIAGNOSE", it) }
                    mapOf("status" to "ok", "diagnostics" to logLines.joinToString("\n"))
                }
                else -> {
                    Log.w("MAR_ActionExecutor", "Unknown action requested: $action")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e("MAR_ActionExecutor", "Failed to execute action '$action': ${e.message}")
            return null
        }
    }
}
