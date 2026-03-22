package com.mar.agent.sdk.test.tools

import com.mar.agent.sdk.tools.AccessibilityServiceManager
import com.mar.agent.sdk.tools.UITapTool
import org.junit.Assert.assertTrue
import org.junit.Test

class UITapToolTest {

    @Test
    fun `test UITapTool fails gracefully when Accessibility Service is offline`() {
        val tool = UITapTool()
        AccessibilityServiceManager.activeService = null

        val result = tool.call(mapOf("x" to 100, "y" to 200))
        assertTrue("Should return error JSON if accessibility service is unattached", result.contains("error"))
        assertTrue("Should mention active status", result.contains("not active"))
    }
}
