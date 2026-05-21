package com.mar.agent.sdk.tools

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

class ContentQueryTool(private val context: Context) {

    fun query(
        uriString: String,
        projection: List<String>? = null,
        selection: String? = null,
        selectionArgs: List<String>? = null,
        sortOrder: String? = null
    ): String {
        val resolver: ContentResolver = context.contentResolver
        val uri = Uri.parse(uriString)
        val projArray = projection?.toTypedArray()

        return try {
            val cursor = resolver.query(uri, projArray, selection, selectionArgs?.toTypedArray(), sortOrder)
            cursor?.use { c ->
                val results = JSONArray()
                val cols = c.columnNames
                while (c.moveToNext()) {
                    val row = JSONObject()
                    for (col in cols) {
                        val idx = c.getColumnIndex(col)
                        val raw = c.getString(idx)
                        val value = if (raw == null) JSONObject.NULL else raw
                        row.put(col, value)
                    }
                    results.put(row)
                }
                JSONObject().apply {
                    put("count", results.length())
                    put("rows", results)
                }.toString()
            } ?: JSONObject().apply {
                put("count", 0)
                put("rows", JSONArray())
                put("error", "Cursor returned null")
            }.toString()
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied: ${e.message}")
            JSONObject().apply {
                put("count", 0)
                put("rows", JSONArray())
                put("error", "Permission denied: ${e.message}")
            }.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Query failed: ${e.message}")
            JSONObject().apply {
                put("count", 0)
                put("rows", JSONArray())
                put("error", e.message ?: "Unknown error")
            }.toString()
        }
    }

    companion object {
        private const val TAG = "MAR_ContentQuery"
    }
}
