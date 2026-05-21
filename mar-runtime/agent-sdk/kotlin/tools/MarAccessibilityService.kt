package com.mar.agent.sdk.tools

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

open class MarAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        Log.i(TAG, "AccessibilityService connected")
        AccessibilityServiceManager.activeService = this
        lastLaunchedPackage = null
        composeContext = ComposeContext()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val ctx = composeContext ?: return
        val pkg = event.packageName?.toString()
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (pkg == ctx.targetPackage && !ctx.chatOpened) {
                    Log.i(TAG, "Target app $pkg opened")
                    handler.post { advanceCompose() }
                }
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (pkg == ctx.targetPackage) {
                    handler.post { advanceCompose() }
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "AccessibilityService interrupted")
    }

    override fun onDestroy() {
        Log.i(TAG, "AccessibilityService destroyed")
        AccessibilityServiceManager.activeService = null
        composeContext = null
        lastLaunchedPackage = null
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    fun tap(x: Float, y: Float): Boolean {
        val latch = CountDownLatch(1)
        var success = false
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 100)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gd: GestureDescription) { success = true; latch.countDown() }
            override fun onCancelled(gd: GestureDescription) { success = false; latch.countDown() }
        }, null)
        latch.await(5, TimeUnit.SECONDS)
        return success
    }

    fun findTextInput(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        try {
            return AccessibilityNodeHelper.findTextInput(root)
        } finally {
            root.recycle()
        }
    }

    fun launchApp(packageName: String): Boolean {
        return try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                lastLaunchedPackage = packageName
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(intent)
                true
            } else false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch $packageName: ${e.message}")
            false
        }
    }

    /* === Compose pipeline: navigate → type → send === */

    data class ComposeParams(val contact: String, val message: String)

    private data class ComposeContext(
        var targetPackage: String = "",
        var contact: String = "",
        var message: String = "",
        var step: Int = 0,
        var chatOpened: Boolean = false,
        var attempts: Int = 0
    )

    private var composeContext: ComposeContext? = null
    private var lastLaunchedPackage: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private val retryDelay = 1500L
    private val maxAttempts = 5

    fun scheduleCompose(packageName: String, contact: String, message: String) {
        val root = rootInActiveWindow
        val alreadyOnChat = root != null && run {
            try { AccessibilityNodeHelper.findTextInput(root) != null } finally { root.recycle() }
        }
        composeContext = ComposeContext(
            targetPackage = packageName,
            contact = contact,
            message = message,
            step = if (alreadyOnChat) 3 else 0,
            chatOpened = alreadyOnChat,
            attempts = 0
        )
        if (alreadyOnChat) {
            Log.i(TAG, "Already on a chat screen, jumping to step 3")
            handler.postDelayed({ advanceCompose() }, 500)
        }
        launchApp(packageName)
    }

    private fun advanceCompose() {
        val ctx = composeContext ?: return
        val root = rootInActiveWindow ?: return

        try {
            when (ctx.step) {
                0 -> stepFindNewChat(root, ctx)
                1 -> stepSearchContact(root, ctx)
                2 -> stepSelectContact(root, ctx)
                3 -> stepTypeAndSend(root, ctx)
            }
        } finally {
            root.recycle()
        }
    }

    /* Step 0: tap the "New conversation" / "New chat" button */
    private fun stepFindNewChat(root: AccessibilityNodeInfo, ctx: ComposeContext) {
        val btn = findButtonByDesc(root, "New conversation")
            ?: findButtonByDesc(root, "New chat")
            ?: findButtonByText(root, "New chat")
        if (btn != null) {
            btn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            btn.recycle()
            ctx.step = 1
            ctx.attempts = 0
            Log.i(TAG, "Tapped new chat button, moving to step 1 (search)")
        } else {
            retryOrTimeout(ctx, "findNewChat")
        }
    }

    /* Step 1: find the contact search input, type contact name */
    private fun stepSearchContact(root: AccessibilityNodeInfo, ctx: ComposeContext) {
        val input = AccessibilityNodeHelper.findTextInput(root)
        if (input != null) {
            AccessibilityNodeHelper.typeText(input, ctx.contact)
            input.recycle()
            ctx.step = 2
            ctx.attempts = 0
            Log.i(TAG, "Typed contact '${ctx.contact}', moving to step 2 (select)")
            // Give results time to render
            handler.postDelayed({ advanceCompose() }, 2000)
        } else {
            retryOrTimeout(ctx, "searchContact")
        }
    }

    /* Step 2: tap on the matching contact in the results list */
    private fun stepSelectContact(root: AccessibilityNodeInfo, ctx: ComposeContext) {
        val contact = findContactInList(root, ctx.contact)
        if (contact != null) {
            contact.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            contact.recycle()
            ctx.chatOpened = true
            ctx.step = 3
            ctx.attempts = 0
            Log.i(TAG, "Selected contact '${ctx.contact}', moving to step 3 (type + send)")
            handler.postDelayed({ advanceCompose() }, 1500)
        } else {
            retryOrTimeout(ctx, "selectContact")
        }
    }

    /* Step 3: type message into chat input and tap Send */
    private fun stepTypeAndSend(root: AccessibilityNodeInfo, ctx: ComposeContext) {
        val input = AccessibilityNodeHelper.findTextInput(root)
        if (input != null) {
            AccessibilityNodeHelper.typeText(input, ctx.message)
            input.recycle()
            Log.i(TAG, "Typed message into chat")

            val sendBtn = findSendButton(root)
            if (sendBtn != null) {
                sendBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                sendBtn.recycle()
                Log.i(TAG, "Message sent to ${ctx.contact}")
                composeContext = null
            } else {
                Log.w(TAG, "Send button not found — message typed but not sent")
                composeContext = null
            }
        } else {
            retryOrTimeout(ctx, "typeAndSend")
        }
    }

    /* === helpers === */

    private fun findButtonByText(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        return findNode(root) { n ->
            n.text?.toString()?.contains(text, ignoreCase = true) == true
                    && n.isClickable && n.isVisibleToUser
        }
    }

    private fun findButtonByDesc(root: AccessibilityNodeInfo, desc: String): AccessibilityNodeInfo? {
        return findNode(root) { n ->
            n.contentDescription?.toString()?.contains(desc, ignoreCase = true) == true
                    && n.isClickable && n.isVisibleToUser
        }
    }

    private fun findSendButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        return findNode(root) { n ->
            n.isClickable && n.isVisibleToUser && (
                    n.contentDescription?.toString()?.contains("send", ignoreCase = true) == true
                            || n.text?.toString()?.contains("send", ignoreCase = true) == true
                    )
        }
    }

    private fun findContactInList(root: AccessibilityNodeInfo, name: String): AccessibilityNodeInfo? {
        return findNode(root) { n ->
            n.isClickable && n.isVisibleToUser && (
                    n.text?.toString()?.contains(name, ignoreCase = true) == true
                            || n.contentDescription?.toString()?.contains(name, ignoreCase = true) == true
                    )
        }
    }

    private fun findNode(root: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (predicate(root)) return root
        if (root.childCount == 0) return null
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val found = findNode(child, predicate)
            if (found != null) {
                child.recycle()
                return found
            }
            child.recycle()
        }
        return null
    }

    private fun retryOrTimeout(ctx: ComposeContext, stepName: String) {
        ctx.attempts++
        if (ctx.attempts < maxAttempts) {
            Log.i(TAG, "Retrying $stepName (attempt ${ctx.attempts}/$maxAttempts)")
            handler.postDelayed({ advanceCompose() }, retryDelay)
        } else {
            Log.e(TAG, "Compose failed at step $stepName after $maxAttempts attempts")
            composeContext = null
        }
    }

    companion object {
        private const val TAG = "MAR_A11Y"
    }
}
