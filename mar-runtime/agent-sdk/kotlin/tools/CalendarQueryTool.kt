package com.mar.agent.sdk.tools

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import com.mar.agent.sdk.MarTool
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * Tool to safely query Android Calendar using ContentResolver.
 * Requires android.permission.READ_CALENDAR.
 */
class CalendarQueryTool(private val context: Context) : MarTool("calendar_query") {

    override fun call(params: Map<String, Any>): String {
        val todayOnly = params["today"] as? Boolean ?: true
        val resolver: ContentResolver = context.contentResolver
        val uri: Uri = CalendarContract.Events.CONTENT_URI

        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND
        )

        // Set time bounds for querying "today"
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

        var selection: String? = null
        var selectionArgs: Array<String>? = null

        if (todayOnly) {
            selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
            selectionArgs = arrayOf(startOfDay.timeInMillis.toString(), endOfDay.timeInMillis.toString())
        }

        val jsonArray = JSONArray()

        try {
            val cursor: Cursor? = resolver.query(uri, projection, selection, selectionArgs, null)
            cursor?.use {
                val titleIndex = it.getColumnIndex(CalendarContract.Events.TITLE)
                val startIndex = it.getColumnIndex(CalendarContract.Events.DTSTART)

                while (it.moveToNext()) {
                    val eventObj = JSONObject()
                    eventObj.put("title", it.getString(titleIndex))
                    eventObj.put("timestamp", it.getLong(startIndex))
                    jsonArray.put(eventObj)
                }
            }
        } catch (e: SecurityException) {
            return JSONObject().put("error", "Missing READ_CALENDAR permission").toString()
        }

        return JSONObject().put("events", jsonArray).toString()
    }
}
