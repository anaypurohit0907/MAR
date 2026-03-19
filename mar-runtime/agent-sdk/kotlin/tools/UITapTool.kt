package com.mar.agent.sdk.tools

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import com.mar.agent.sdk.MarTool
import org.json.JSONObject

/**
 * Singleton to hold a reference to the active AccessibilityService.
 */
object AccessibilityServiceManager {
    var activeService: AccessibilityService? = null
}

/**
 * Tool for dispatching taps via AccessibilityService.
 */
class UITapTool : MarTool("ui_tap") {

    override fun call(params: Map<String, Any>): String {
        val x = (params["x"] as? Number)?.toFloat() ?: return errorResp("Missing 'x' coordinate")
        val y = (params["y"] as? Number)?.toFloat() ?: return errorResp("Missing 'y' coordinate")
        val service = AccessibilityServiceManager.activeService ?: return errorResp("AccessibilityService not active")

        val path = Path()
        path.moveTo(x, y)
        
        val gestureBuilder = GestureDescription.Builder()
        val stroke = GestureDescription.StrokeDescription(path, 0, 100)
        gestureBuilder.addStroke(stroke)

        val result = service.dispatchGesture(gestureBuilder.build(), null, null)
        
        return JSONObject().put("status", if (result) "success" else "failed").toString()
    }

    private fun errorResp(msg: String): String {
        return JSONObject().put("error", msg).toString()
    }
}
