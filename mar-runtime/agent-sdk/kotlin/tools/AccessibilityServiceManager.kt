package com.mar.agent.sdk.tools

import android.accessibilityservice.AccessibilityService

object AccessibilityServiceManager {
    @Volatile
    var activeService: MarAccessibilityService? = null
}
