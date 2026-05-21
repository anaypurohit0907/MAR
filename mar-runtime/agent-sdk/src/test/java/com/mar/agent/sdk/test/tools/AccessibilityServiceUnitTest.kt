package com.mar.agent.sdk.test.tools

import com.mar.agent.sdk.core.executor.ActionExecutor
import com.mar.agent.sdk.tools.AccessibilityNodeHelper
import com.mar.agent.sdk.tools.AccessibilityServiceManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AccessibilityServiceUnitTest {

    @Before
    fun setUp() {
        AccessibilityServiceManager.activeService = null
    }

    @Test
    fun `ActionExecutor ui_compose returns false when service offline`() = runBlocking {
        val item = org.json.JSONObject().apply {
            put("action", "ui_compose")
            put("target_app", "org.telegram.messenger")
            put("contact", "TestUser")
            put("message", "Hello")
        }
        val executor = ActionExecutor(mock(android.content.Context::class.java))
        val result = executor.executeSingleAction("ui_compose", item)
        assertEquals(false, result)
    }

    @Test
    fun `ActionExecutor ui_compose returns false when target_app blank`() = runBlocking {
        val item = org.json.JSONObject().apply {
            put("action", "ui_compose")
            put("target_app", "")
            put("contact", "TestUser")
            put("message", "Hello")
        }
        val executor = ActionExecutor(mock(android.content.Context::class.java))
        val result = executor.executeSingleAction("ui_compose", item)
        assertEquals(false, result)
    }

    @Test
    fun `ActionExecutor UITapper send_message returns true as fallback`() = runBlocking {
        val item = org.json.JSONObject().apply {
            put("action", "UITapper")
            put("sub_action", "send")
            put("target_contact", "Bob")
            put("input_text", "Draft for Bob")
            put("app", "org.telegram.messenger")
        }
        val ctx = mock(android.content.Context::class.java)
        `when`(ctx.applicationContext).thenReturn(ctx)
        val executor = ActionExecutor(ctx)
        val result = executor.executeSingleAction("UITapper", item)
        assertEquals(true, result)
    }

    @Test
    fun `ActionExecutor ui_compose returns success map when service available`() = runBlocking {
        val item = org.json.JSONObject().apply {
            put("action", "ui_compose")
            put("target_app", "org.telegram.messenger")
            put("contact", "Alice")
            put("message", "Happy Birthday!")
        }
        // Use open subclass instead of mocking final class
        val service = object : com.mar.agent.sdk.tools.MarAccessibilityService() {
            override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent) {}
            override fun onInterrupt() {}
        }
        AccessibilityServiceManager.activeService = service
        val executor = ActionExecutor(mock(android.content.Context::class.java))
        @Suppress("UNCHECKED_CAST")
        val result = executor.executeSingleAction("ui_compose", item) as? Map<*, *>
        assertNotNull("Expected success map but got false/nil", result)
        assertEquals("composed", result?.get("status"))
        assertEquals("org.telegram.messenger", result?.get("app"))
        assertEquals("Alice", result?.get("contact"))
        AccessibilityServiceManager.activeService = null
    }

    @Test
    fun `AccessibilityNodeHelper typeText calls performAction`() {
        val node = mock(android.view.accessibility.AccessibilityNodeInfo::class.java)
        `when`(node.performAction(anyInt())).thenReturn(true)
        `when`(node.performAction(anyInt(), any())).thenReturn(true)

        val result = AccessibilityNodeHelper.typeText(node, "test")
        assertTrue(result)
    }

    @Test
    fun `AccessibilityNodeHelper typeText returns false when focus fails`() {
        val node = mock(android.view.accessibility.AccessibilityNodeInfo::class.java)
        `when`(node.performAction(anyInt())).thenReturn(false)

        val result = AccessibilityNodeHelper.typeText(node, "test")
        assertFalse(result)
    }
}
