package com.mar.agent.sdk.core.executor

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import org.json.JSONArray
import android.util.Log

/**
 * ActionExecutor: Decouples OS intent routing and physical hardware interaction
 * from the worker logic.
 */
class ActionExecutor(private val context: Context) {

    fun executeActions(jsonPayload: String) {
        try {
            val jsonArray = JSONArray(jsonPayload)
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                if (!item.has("action")) continue
                
                val action = item.getString("action")
                executeSingleAction(action, item)
            }
        } catch(e: Exception) {
            Log.e("MAR_ActionExecutor", "Failed to parse or execute JSON payload: ${e.message}")
        }
    }

    private fun executeSingleAction(action: String, item: org.json.JSONObject) {
        try {
            when (action) {
                "hardware_flashlight" -> {
                    val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                    val cameraId = cameraManager.cameraIdList[0]
                    val state = item.optString("state", "on") == "on"
                    cameraManager.setTorchMode(cameraId, state)
                }
                "launch_app" -> {
                    if (item.optString("target") == "settings") {
                        val intent = Intent(Settings.ACTION_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                        context.startActivity(intent)
                    }
                }
                "set_timer" -> {
                    val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        putExtra(AlarmClock.EXTRA_LENGTH, item.getInt("seconds"))
                        putExtra(AlarmClock.EXTRA_MESSAGE, item.optString("message", "Agent Timer"))
                        putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                    }
                    context.startActivity(intent)
                }
                "open_url" -> {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.getString("url"))).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                }
                else -> {
                    Log.w("MAR_ActionExecutor", "Unknown action requested: $action")
                }
            }
        } catch (e: Exception) {
            Log.e("MAR_ActionExecutor", "Failed to execute action '$action': ${e.message}")
        }
    }
}
