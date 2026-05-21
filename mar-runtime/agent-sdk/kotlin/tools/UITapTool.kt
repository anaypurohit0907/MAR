package com.mar.agent.sdk.tools

import com.mar.agent.sdk.MarTool
import org.json.JSONObject

class UITapTool : MarTool("ui_tap") {

    override fun call(params: Map<String, Any>): String {
        val x = (params["x"] as? Number)?.toFloat() ?: return errorResp("Missing 'x' coordinate")
        val y = (params["y"] as? Number)?.toFloat() ?: return errorResp("Missing 'y' coordinate")
        val service = AccessibilityServiceManager.activeService ?: return errorResp("AccessibilityService not active")

        val result = service.tap(x, y)
        return JSONObject().put("status", if (result) "success" else "failed").toString()
    }

    private fun errorResp(msg: String): String {
        return JSONObject().put("error", msg).toString()
    }
}
