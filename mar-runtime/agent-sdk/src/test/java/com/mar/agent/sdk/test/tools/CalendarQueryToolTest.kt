package com.mar.agent.sdk.test.tools

import android.content.Context
import android.database.MatrixCursor
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
import com.mar.agent.sdk.tools.CalendarQueryTool
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class CalendarQueryToolTest {

    private lateinit var context: Context
    private lateinit var tool: CalendarQueryTool

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        tool = CalendarQueryTool(context)
        setupMockCalendarData()
    }

    private fun setupMockCalendarData() {
        val shadowResolver: ShadowContentResolver = Shadows.shadowOf(context.contentResolver)
        val uri = CalendarContract.Events.CONTENT_URI

        // Mock cursor for Robolectric
        val cursor = MatrixCursor(arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND
        ))

        val now = System.currentTimeMillis()
        cursor.addRow(arrayOf(1, "Alice's Birthday", now, now + 100000))
        cursor.addRow(arrayOf(2, "Doctor Appointment", now + 200000, now + 300000))

        shadowResolver.setCursor(uri, cursor)
    }

    @Test
    fun `test querying birthday event returns mapped name`() {
        // Using "today" and eventType "birthday" Should extract "Alice"
        val result = tool.queryEvents(query = "today", eventType = "birthday")
        
        assertNotNull("Result should not be null", result)
        assertEquals("Name extracted must match 'Alice'", "Alice", result?.get("name"))
    }

    @Test
    fun `test querying generic event returns title`() {
        // Generic search for "Doctor"
        val result = tool.queryEvents(query = "Doctor", eventType = null)
        
        assertNotNull("Result should not be null", result)
        assertEquals("Should return the literal title", "Doctor Appointment", result?.get("title"))
    }
}
