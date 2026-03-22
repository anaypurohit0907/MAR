package com.mar.agent.sdk.tools

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import java.util.Calendar

/**
 * Tool to safely query Android Calendar using ContentResolver.
 * Requires android.permission.READ_CALENDAR.
 */
class CalendarQueryTool(private val context: Context) {

    fun queryEvents(query: String?, eventType: String?): Map<String, Any>? {
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

        val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
        val selectionArgs = arrayOf(startOfDay.timeInMillis.toString(), endOfDay.timeInMillis.toString())

        try {
            val cursor: Cursor? = resolver.query(uri, projection, selection, selectionArgs, null)
            cursor?.use {
                val titleIndex = it.getColumnIndex(CalendarContract.Events.TITLE)

                while (it.moveToNext()) {
                    val title = it.getString(titleIndex)
                    
                    // Simple NLP check: does it match "Birthday"?
                    if (eventType?.equals("birthday", ignoreCase = true) == true) {
                        if (title.contains("birthday", ignoreCase = true)) {
                            // Extract name (e.g. "Alice's Birthday" -> "Alice")
                            val name = title.replace("(?i)'s birthday".toRegex(), "").trim()
                            return mapOf("name" to name) // Matches {step_1.output.name}
                        }
                    } else if (query != null && title.contains(query, ignoreCase = true)) {
                        return mapOf("title" to title)
                    }
                }
            }
        } catch (e: SecurityException) {
            return mapOf("error" to "Missing READ_CALENDAR permission: ${e.message}")
        }

        return null // No events found
    }
}
