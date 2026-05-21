package com.mar.agent.sdk.core

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class ChatMessage(val role: String, val text: String)

class CloudClient(private val baseUrl: String, private val apiKey: String, private val model: String) {

    companion object {
        private const val TAG = "MAR_CloudClient"
    }

    private val isGemini: Boolean get() = baseUrl.contains("googleapis.com") || baseUrl.contains("generativelanguage")

    fun chat(messages: List<ChatMessage>, systemPrompt: String): String? {
        val body = if (isGemini) buildGeminiBody(messages, systemPrompt)
                   else buildOpenAiBody(messages, systemPrompt)
        val response = post(body)
        return response?.let { if (isGemini) extractGeminiText(it) else extractOpenAiText(it) }
    }

    fun generateWorkflow(description: String, systemPrompt: String): String? {
        val msg = listOf(ChatMessage("user", description))
        val response = chat(msg, systemPrompt)
        return response?.let { extractYaml(it) }
    }

    private fun buildGeminiBody(messages: List<ChatMessage>, systemPrompt: String): JSONObject {
        val contents = JSONArray()
        for (msg in messages) {
            contents.put(
                JSONObject()
                    .put("role", if (msg.role == "assistant") "model" else msg.role)
                    .put("parts", JSONArray().put(JSONObject().put("text", msg.text)))
            )
        }
        return JSONObject()
            .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))))
            .put("contents", contents)
    }

    private fun buildOpenAiBody(messages: List<ChatMessage>, systemPrompt: String): JSONObject {
        val msgs = JSONArray()
        msgs.put(JSONObject().put("role", "system").put("content", systemPrompt))
        for (msg in messages) {
            msgs.put(JSONObject().put("role", msg.role).put("content", msg.text))
        }
        return JSONObject()
            .put("model", model)
            .put("messages", msgs)
    }

    private fun post(body: JSONObject): String? {
        try {
            val urlStr = if (isGemini) "$baseUrl?key=$apiKey" else baseUrl
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            if (!isGemini) conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.doOutput = true
            conn.connectTimeout = 30000
            conn.readTimeout = 30000

            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

            val code = conn.responseCode
            if (code != 200) {
                val err = conn.errorStream?.bufferedReader()?.readText() ?: "no error body"
                Log.w(TAG, "API returned $code: $err")
                return null
            }

            return conn.inputStream.bufferedReader().readText()
        } catch (e: Exception) {
            Log.e(TAG, "HTTP request failed: ${e.message}")
            return null
        }
    }

    private fun extractGeminiText(json: String): String? {
        return try {
            val root = JSONObject(json)
            val candidates = root.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val content = candidates.getJSONObject(0).optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    parts.getJSONObject(0).optString("text", null)
                } else null
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Gemini response: ${e.message}")
            null
        }
    }

    private fun extractOpenAiText(json: String): String? {
        return try {
            val root = JSONObject(json)
            val choices = root.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                choices.getJSONObject(0).optJSONObject("message")?.optString("content", null)
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse OpenAI response: ${e.message}")
            null
        }
    }

    private fun extractYaml(json: String): String? {
        val raw = if (isGemini) extractGeminiText(json) else extractOpenAiText(json)
        if (raw == null) return null
        val yamlRegex = Regex("```(?:yaml)?\\s*\\n([\\s\\S]*?)```")
        val match = yamlRegex.find(raw)
        val result = match?.groupValues?.getOrElse(1) { raw.trim() } ?: raw.trim()
        return result
    }
}
