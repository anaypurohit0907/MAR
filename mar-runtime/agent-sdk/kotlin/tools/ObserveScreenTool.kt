package com.mar.agent.sdk.tools

import android.content.Context
import android.util.Log
import com.mar.runtime.agent.vision.ScreenCaptureService
import com.mar.runtime.agent.vision.VisionProcessor

class ObserveScreenTool(private val context: Context) {

    val name: String = "observe_screen"
    val description: String = "Captures the current screen and uses local OCR to map text to their exact (x,y) screen coordinates. Returns a JSON array of text and bounds."

    suspend fun execute(args: Map<String, Any>): String {
        return try {
            if (!ScreenCaptureService.isRunning) {
                return "{\"error\": \"Screen capture service is not running. User must grant media projection permission.\"}"
            }

            val bitmap = ScreenCaptureService.latestBitmap
            if (bitmap == null) {
                return "{\"error\": \"No screen frame available yet. Retrying might solve this.\"}"
            }

            Log.i("ObserveScreenTool", "Running local OCR over captured screen frame (${bitmap.width}x${bitmap.height})...")
            
            // Execute on-device OCR mapping
            val jsonLayout = VisionProcessor.extractTextLayout(bitmap)
            
            Log.i("ObserveScreenTool", "OCR extraction complete: ${jsonLayout.take(100)}...")
            
            jsonLayout
        } catch (e: Exception) {
            "{\"error\": \"Failed to observe screen: ${e.message}\"}"
        }
    }
}
