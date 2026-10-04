package com.mar.agent.sdk.core

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * ContextCompressor: Reduces tool outputs to fit within the SLM's token budget.
 * All tool results that will be injected into LLM prompts MUST pass through here.
 * 
 * Design philosophy: The SLM handles only genuinely non-deterministic work.
 * Everything else — including context preparation — is handled by deterministic tooling.
 */
object ContextCompressor {

    private const val TAG = "MAR_ContextCompressor"

    // Approximate chars-per-token for short English text
    private const val CHARS_PER_TOKEN = 4

    // Budget per tool result injected into a prompt (in tokens)
    const val MAX_TOOL_OUTPUT_TOKENS = 300
    const val MAX_WEB_CONTENT_TOKENS = 400
    const val MAX_NOTIFICATION_ITEMS = 5
    const val MAX_SMS_ITEMS = 3

    /**
     * Main entry point. Compresses a tool result map before template substitution.
     * Call this in WorkflowRunner after a step produces output, before storing in memoryContext.
     */
    fun compress(action: String, result: Map<String, Any?>): Map<String, Any?> {
        return try {
            when (action) {
                "web_fetch"                          -> compressWebFetch(result)
                "read_notifications", "NotificationListener" -> compressNotifications(result)
                "read_sms"                           -> compressSms(result)
                "CalendarQuery"                      -> compressCalendar(result)
                "query_contacts"                     -> compressContacts(result)
                "content_query"                      -> compressGenericJson(result)
                else                                 -> result  // pass through unchanged
            }
        } catch (e: Exception) {
            Log.w(TAG, "Compression failed for '${action}', using raw result: ${e.message}")
            result
        }
    }

    // --- Web Content ---
    // Strips HTML residue, collapses whitespace, truncates to token budget
    private fun compressWebFetch(result: Map<String, Any?>): Map<String, Any?> {
        val raw = result["text"]?.toString() ?: return result
        val compressed = raw
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_WEB_CONTENT_TOKENS * CHARS_PER_TOKEN)
        Log.d(TAG, "web_fetch: ${raw.length} -> ${compressed.length} chars")
        return result.toMutableMap().apply { put("text", compressed) }
    }

    // --- Notifications ---
    // Takes only the N most recent, deduplicates by package, truncates each body
    private fun compressNotifications(result: Map<String, Any?>): Map<String, Any?> {
        val rawJson = result["notifications"]?.toString() ?: return result
        return try {
            val arr = JSONArray(rawJson)
            val kept = mutableListOf<JSONObject>()
            val seenPackages = mutableSetOf<String>()
            for (i in 0 until arr.length()) {
                val n = arr.getJSONObject(i)
                val pkg = n.optString("package")
                if (pkg !in seenPackages && kept.size < MAX_NOTIFICATION_ITEMS) {
                    seenPackages.add(pkg)
                    // Flatten to: "AppName: Title — Body (truncated)"
                    val title = n.optString("title").trim()
                    val text = n.optString("text").take(80).trim()
                    val flat = buildString {
                        if (title.isNotBlank()) append(title)
                        if (text.isNotBlank()) { if (isNotEmpty()) append(" — "); append(text) }
                    }
                    kept.add(JSONObject().apply {
                        put("package", pkg)
                        put("summary", flat)
                    })
                }
            }
            val compressedArr = JSONArray(kept.map { it })
            val joinedTexts = kept.joinToString(" | ") { it.optString("summary") }
            Log.d(TAG, "notifications: ${arr.length()} -> ${kept.size} items")
            result.toMutableMap().apply {
                put("notifications", compressedArr.toString())
                put("texts", joinedTexts)
                put("text", joinedTexts)
                put("count", kept.size)
            }
        } catch (e: Exception) {
            result
        }
    }

    // --- SMS ---
    // Takes most recent N threads, truncates each message body
    private fun compressSms(result: Map<String, Any?>): Map<String, Any?> {
        val rawJson = result["json"]?.toString() ?: return result
        return try {
            val root = JSONObject(rawJson)
            val rows = root.optJSONArray("rows") ?: return result
            val kept = JSONArray()
            val seenAddresses = mutableSetOf<String>()
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                val addr = row.optString("address")
                if (addr !in seenAddresses && seenAddresses.size < MAX_SMS_ITEMS) {
                    seenAddresses.add(addr)
                    kept.put(JSONObject().apply {
                        put("from", addr)
                        put("body", row.optString("body").take(100))
                    })
                }
            }
            val compressed = JSONObject().apply {
                put("count", kept.length())
                put("rows", kept)
            }
            Log.d(TAG, "sms: ${rows.length()} -> ${kept.length()} threads")
            result.toMutableMap().apply {
                put("json", compressed.toString())
                put("count", kept.length().toString())
            }
        } catch (e: Exception) {
            result
        }
    }

    // --- Calendar ---
    // Flattens to a single human-readable sentence instead of raw JSON
    private fun compressCalendar(result: Map<String, Any?>): Map<String, Any?> {
        // CalendarQueryTool returns a direct map with name/title keys
        // Already pretty compact — just ensure text fields are truncated
        val name = result["name"]?.toString()?.take(60)
        val text = result["text"]?.toString()?.take(120)
        return result.toMutableMap().apply {
            if (name != null) put("name", name)
            if (text != null) put("text", text)
        }
    }

    // --- Contacts ---
    // Extracts only display_name + primary phone/email from raw content resolver JSON
    private fun compressContacts(result: Map<String, Any?>): Map<String, Any?> {
        val rawJson = result["json"]?.toString() ?: return result
        return try {
            val root = JSONObject(rawJson)
            val rows = root.optJSONArray("rows") ?: return result
            val contacts = mutableMapOf<String, String>() // name -> phone
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                val name = row.optString("display_name").trim()
                val data = row.optString("data1").trim()
                val mime = row.optString("mimetype")
                if (name.isNotBlank() && data.isNotBlank()) {
                    // Prefer phone number, fall back to email
                    if (mime.contains("phone") || !contacts.containsKey(name)) {
                        contacts[name] = data
                    }
                }
            }
            val summary = contacts.entries.take(5).joinToString(", ") { "${it.key}: ${it.value}" }
            Log.d(TAG, "contacts: ${rows.length()} rows -> ${contacts.size} unique contacts")
            result.toMutableMap().apply {
                put("summary", summary)
                put("json", JSONObject().apply {
                    put("count", contacts.size)
                    put("contacts", summary)
                }.toString())
                put("count", contacts.size.toString())
            }
        } catch (e: Exception) {
            result
        }
    }

    // --- Generic JSON (content_query) ---
    // Truncates the raw JSON string to token budget if it's too large
    private fun compressGenericJson(result: Map<String, Any?>): Map<String, Any?> {
        val rawJson = result["json"]?.toString() ?: return result
        val maxChars = MAX_TOOL_OUTPUT_TOKENS * CHARS_PER_TOKEN
        if (rawJson.length <= maxChars) return result
        Log.w(TAG, "content_query result truncated: ${rawJson.length} -> $maxChars chars")
        return result.toMutableMap().apply {
            put("json", rawJson.take(maxChars) + "...")
        }
    }

    /**
     * Truncates a plain string to fit within a token budget.
     * Use for any freeform text before prompt injection.
     */
    fun truncateToTokenBudget(text: String, maxTokens: Int = MAX_TOOL_OUTPUT_TOKENS): String {
        val maxChars = maxTokens * CHARS_PER_TOKEN
        return if (text.length > maxChars) {
            Log.d(TAG, "Text truncated: ${text.length} -> $maxChars chars")
            text.take(maxChars)
        } else text
    }
}
