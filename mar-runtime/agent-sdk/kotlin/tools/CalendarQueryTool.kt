package com.mar.agent.sdk.tools

import android.content.Context
import android.provider.CalendarContract
import android.util.Log
import org.json.JSONObject
import java.util.Calendar

class CalendarQueryTool(private val context: Context) {

    fun queryEvents(query: String?, eventType: String?): Map<String, Any>? {
        val now = Calendar.getInstance()
        val startOfDay = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endOfDay = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        val beginMs = startOfDay.timeInMillis
        val endMs = endOfDay.timeInMillis

        Log.d(TAG, "Querying Calendar Instances from $beginMs to $endMs")

        val instancesUri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(beginMs.toString())
            .appendPath(endMs.toString())
            .build()

        val projection = listOf(
            CalendarContract.Instances._ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END
        )

        val tool = ContentQueryTool(context)
        val rawResult = tool.query(
            uriString = instancesUri.toString(),
            projection = projection,
            selection = null,
            selectionArgs = null,
            sortOrder = "${CalendarContract.Instances.BEGIN} DESC" // Get latest events first
        )

        val json = JSONObject(rawResult)
        val count = json.optInt("count", 0)
        if (count == 0) return null

        val rows = json.optJSONArray("rows") ?: return null
        
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            val title = row.optString(CalendarContract.Instances.TITLE, "")
            
            Log.d(TAG, "Checking event: $title")

            if (eventType?.equals("birthday", ignoreCase = true) == true) {
                if (title.contains("birthday", ignoreCase = true)) {
                    val name = title.replace("(?i)(?:'s)?\\s*birthday".toRegex(), "").trim()
                    Log.i(TAG, "Found birthday match: $name")
                    return mapOf("name" to name.ifEmpty { title })
                }
            } else if (query != null && title.contains(query, ignoreCase = true)) {
                Log.i(TAG, "Found query match: $title")
                return mapOf("title" to title)
            }
        }

        // Final fallback: if no match but we have events and no specific filter, return first
        if (query == null && eventType == null) {
            val firstTitle = rows.optJSONObject(0)?.optString(CalendarContract.Instances.TITLE, "") ?: ""
            return mapOf("title" to firstTitle)
        }

        return null
    }

    companion object {
        private const val TAG = "MAR_CalendarQuery"
    }
}
