package com.mar.runtime.agent.vision

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

object VisionProcessor {
    // Robust, offline, on-device OCR provided by ML Kit
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Scans a Bitmap for text and returns a robust JSON representation of bounding boxes.
     * Yields a JSON array format like:
     * [{"text": "Submit", "bounds": {"center_x": 100, "center_y": 200, "w": 50, "h": 20}}, ...]
     */
    suspend fun extractTextLayout(bitmap: Bitmap): String {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = recognizer.process(image).await()
            val layoutArray = JSONArray()

            for (block in result.textBlocks) {
                for (line in block.lines) {
                    for (element in line.elements) {
                        val text = element.text
                        val box = element.boundingBox
                        if (box != null) {
                            val elementObj = JSONObject().apply {
                                put("text", text)
                                put("bounds", JSONObject().apply {
                                    put("center_x", box.centerX() as Int)
                                    put("center_y", box.centerY() as Int)
                                    put("w", box.width() as Int)
                                    put("h", box.height() as Int)
                                })
                            }
                            layoutArray.put(elementObj)
                        }
                    }
                }
            }
            layoutArray.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            "{\"error\": \"OCR parsing failed: ${e.message}\"}"
        }
    }
}
