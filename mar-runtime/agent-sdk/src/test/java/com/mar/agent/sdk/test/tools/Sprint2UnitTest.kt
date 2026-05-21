package com.mar.agent.sdk.test.tools

import com.mar.agent.sdk.tools.ContentQueryTool
import com.mar.agent.sdk.tools.NotificationEvent
import com.mar.agent.sdk.tools.NotificationListenerManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Sprint2UnitTest {

    @Test
    fun `NotificationListenerManager stores and retrieves events`() {
        NotificationListenerManager.clear()
        assertNull(NotificationListenerManager.getLatestNotification())

        val evt = NotificationEvent(
            packageName = "com.test.app",
            title = "Title",
            text = "Body text",
            category = "alarm",
            key = "key1"
        )
        NotificationListenerManager.addNotification(evt)

        val latest = NotificationListenerManager.getLatestNotification()
        assertNotNull(latest)
        assertEquals("com.test.app", latest?.packageName)
        assertEquals("Title", latest?.title)
        assertEquals("Body text", latest?.text)
        assertEquals("alarm", latest?.category)
    }

    @Test
    fun `NotificationListenerManager respects max capacity`() {
        NotificationListenerManager.clear()
        for (i in 1..60) {
            NotificationListenerManager.addNotification(
                NotificationEvent(packageName = "pkg$i", title = "t$i", text = "b$i", category = null, key = "k$i")
            )
        }
        val recent = NotificationListenerManager.getRecentNotifications(100)
        assertEquals(50, recent.size)
        assertEquals("pkg11", recent.first().packageName)
        assertEquals("pkg60", recent.last().packageName)
    }

    @Test
    fun `ContentQueryTool returns error on invalid URI`() {
        val ctx = mock(android.content.Context::class.java)
        val resolver = mock(android.content.ContentResolver::class.java)
        `when`(ctx.contentResolver).thenReturn(resolver)
        val tool = ContentQueryTool(ctx)
        val result = tool.query("not-a-uri")
        assertTrue(result.contains("error"))
    }

    @Test
    fun `NotificationListenerManager returns empty when no events`() {
        NotificationListenerManager.clear()
        val recent = NotificationListenerManager.getRecentNotifications()
        assertTrue(recent.isEmpty())
        assertNull(NotificationListenerManager.getLatestNotification())
    }

    @Test
    fun `NotificationListenerManager starts disconnected`() {
        assertFalse(NotificationListenerManager.isConnected)
    }

    @Test
    fun `NotificationEvent data class stores all fields`() {
        val evt = NotificationEvent(
            packageName = "com.test",
            title = "Test Title",
            text = "Test text body",
            category = "msg",
            key = "abc123",
            timestamp = 1000L
        )
        assertEquals("com.test", evt.packageName)
        assertEquals("Test Title", evt.title)
        assertEquals("Test text body", evt.text)
        assertEquals("msg", evt.category)
        assertEquals("abc123", evt.key)
        assertEquals(1000L, evt.timestamp)
    }
}
