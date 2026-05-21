package com.mar.agent.sdk.tools

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

object AccessibilityNodeHelper {
    private const val TAG = "MAR_A11Y_NODE"

    fun findTextInput(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        return findTextInputRecursive(root)
    }

    fun typeText(node: AccessibilityNodeInfo, text: String): Boolean {
        val bundle = android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                && node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
    }

    private fun findTextInputRecursive(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable && node.isEnabled && node.isVisibleToUser) {
            return node
        }
        if (node.className?.toString()?.contains("EditText", ignoreCase = true) == true
            && node.isEnabled && node.isVisibleToUser) {
            return node
        }
        if (node.childCount == 0) return null
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findTextInputRecursive(child)
            if (found != null) {
                child.recycle()
                return found
            }
            child.recycle()
        }
        return null
    }
}
