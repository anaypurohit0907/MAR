package com.mar.agent.sdk.tools

import android.content.Context
import android.provider.CalendarContract
import org.json.JSONObject
import java.util.Calendar

class CalendarQueryTool(private val context: Context) {

    fun queryEvents(query: String?, eventType: String?): Map<String, Any>? {
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
        val endOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
        }
        val beginMs = startOfDay.timeInMillis
        val endMs = endOfDay.timeInMillis

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
            selectionArgs = null
        )

        val json = JSONObject(rawResult)
        val count = json.optInt("count", 0)
        if (count == 0) return null

        val row = json.optJSONArray("rows")?.optJSONObject(0) ?: return null
        val title = row.optString(CalendarContract.Instances.TITLE, "")

        if (eventType?.equals("birthday", ignoreCase = true) == true) {
            val name = title.replace("(?i)(?:'s)?\\s*birthday".toRegex(), "").trim()
            return mapOf("name" to name.ifEmpty { title })
        } else if (query != null && title.contains(query, ignoreCase = true)) {
            return mapOf("title" to title)
        }
        return null
    }

    companion object {
        private const val TAG = "MAR_CalendarQuery"
    }
}
