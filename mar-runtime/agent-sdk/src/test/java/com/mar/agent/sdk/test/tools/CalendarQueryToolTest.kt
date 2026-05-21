package com.mar.agent.sdk.test.tools

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import com.mar.agent.sdk.tools.CalendarQueryTool
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class CalendarQueryToolTest {

    @Test
    fun `test birthday event extraction`() {
        val ctx = mock(Context::class.java)
        val resolver = mock(ContentResolver::class.java)
        `when`(ctx.contentResolver).thenReturn(resolver)

        val cols = arrayOf("_id", "title", "dtstart", "dtend")
        val cursor = mock(Cursor::class.java)
        `when`(cursor.moveToNext()).thenReturn(true, false)
        `when`(cursor.getString(anyInt())).thenReturn("Alice's Birthday")
        `when`(cursor.getColumnIndex(CalendarContract.Events.TITLE)).thenReturn(1)
        `when`(cursor.getColumnNames()).thenReturn(cols)

        `when`(resolver.query(
            any(Uri::class.java),
            any(),
            any(),
            any(),
            any()
        )).thenReturn(cursor)

        val tool = CalendarQueryTool(ctx)
        val result = tool.queryEvents("today", "birthday")

        assertNotNull("Calendar query returned null", result)
        assertEquals("Alice", result?.get("name"))
    }

    @Test
    fun `test null returned when no birthday events`() {
        val ctx = mock(Context::class.java)
        val resolver = mock(ContentResolver::class.java)
        `when`(ctx.contentResolver).thenReturn(resolver)

        val cols = arrayOf("_id", "title", "dtstart", "dtend")
        val cursor = mock(Cursor::class.java)
        `when`(cursor.moveToNext()).thenReturn(false)
        `when`(cursor.getColumnIndex(CalendarContract.Events.TITLE)).thenReturn(1)
        `when`(cursor.getColumnNames()).thenReturn(cols)

        `when`(resolver.query(
            any(Uri::class.java),
            any(),
            any(),
            any(),
            any()
        )).thenReturn(cursor)

        val tool = CalendarQueryTool(ctx)
        val result = tool.queryEvents("today", "birthday")

        assertNull(result)
    }
}
