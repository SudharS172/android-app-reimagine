package com.example.andoridappforreimagine.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class UIAutomationService : AccessibilityService() {
    private var lastActionTimestamp = 0L
    private var lastActionTarget = ""
    private var isActionInProgress = false

    companion object {
        private const val ACTION_TIMEOUT = 5000L // 5 seconds
        private const val ACTION_RETRY_DELAY = 500L // 0.5 seconds
        private const val MAX_RETRIES = 3
        private const val CLICK_DURATION = 100L // Duration of click gesture

        private var instance: UIAutomationService? = null
        fun getInstance(): UIAutomationService? = instance
    }

    init {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let { handleAccessibilityEvent(it) }
    }

    override fun onInterrupt() {
        isActionInProgress = false
    }

    private fun handleAccessibilityEvent(event: AccessibilityEvent) {
        if (isActionInProgress && System.currentTimeMillis() - lastActionTimestamp < ACTION_TIMEOUT) {
            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                    if (event.text.any { it.contains(lastActionTarget, ignoreCase = true) }) {
                        isActionInProgress = false
                    }
                }
            }
        }
    }

    suspend fun performAction(action: String, target: String): Boolean = withContext(Dispatchers.Main) {
        var retryCount = 0
        while (retryCount < MAX_RETRIES) {
            try {
                val rootNode = rootInActiveWindow ?: return@withContext false
                
                lastActionTimestamp = System.currentTimeMillis()
                lastActionTarget = target
                isActionInProgress = true

                val success = when (action) {
                    "NAVIGATE" -> findAndClickByText(rootNode, target)
                    "CLICK" -> findAndClickByText(rootNode, target)
                    "TYPE" -> {
                        val node = findEditText(rootNode) ?: return@withContext false
                        performTypeAction(node, target)
                    }
                    "SCROLL" -> performScrollAction(rootNode, target)
                    else -> false
                }

                if (success) {
                    var waitTime = 0L
                    while (isActionInProgress && waitTime < ACTION_TIMEOUT) {
                        delay(100)
                        waitTime += 100
                    }
                    
                    if (!isActionInProgress) {
                        return@withContext verifyActionCompletion(action, target)
                    }
                }

                retryCount++
                if (retryCount < MAX_RETRIES) {
                    delay(ACTION_RETRY_DELAY)
                    continue
                }
            } catch (e: Exception) {
                retryCount++
                if (retryCount < MAX_RETRIES) {
                    delay(ACTION_RETRY_DELAY)
                    continue
                }
            } finally {
                isActionInProgress = false
            }
        }
        return@withContext false
    }

    private fun findAndClickByText(root: AccessibilityNodeInfo, text: String): Boolean {
        val nodes = root.findAccessibilityNodeInfosByText(text)
        if (nodes.isEmpty()) return false

        for (node in nodes) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            
            // Try clicking the node itself or its clickable parent
            if (node.isClickable || findClickableParent(node) != null) {
                return performClickGesture(rect.exactCenterX().toFloat(), rect.exactCenterY().toFloat())
            }
        }
        return false
    }

    private fun findClickableParent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable) {
                return parent
            }
            parent = parent.parent
        }
        return null
    }

    private fun performClickGesture(x: Float, y: Float): Boolean {
        val path = Path()
        path.moveTo(x, y)
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, CLICK_DURATION))
            .build()

        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                isActionInProgress = false
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                isActionInProgress = false
            }
        }, null)
    }

    private fun findEditText(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val nodes = root.findAccessibilityNodeInfosByText("")
        return nodes.find { it.isEditable }
    }

    private fun performTypeAction(node: AccessibilityNodeInfo, text: String): Boolean {
        val bundle = Bundle()
        bundle.putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
            text
        )
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
    }

    private fun performScrollAction(root: AccessibilityNodeInfo, direction: String): Boolean {
        val action = when (direction.uppercase()) {
            "UP" -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            "DOWN" -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            else -> return false
        }
        return root.performAction(action)
    }

    private fun verifyActionCompletion(action: String, target: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        
        return when (action) {
            "NAVIGATE", "CLICK" -> {
                rootNode.findAccessibilityNodeInfosByText(target).isNotEmpty()
            }
            "TYPE" -> {
                rootNode.findAccessibilityNodeInfosByText(target).isNotEmpty()
            }
            "SCROLL" -> {
                true // Basic scroll verification
            }
            else -> false
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo()
        info.apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY or
                    AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
            notificationTimeout = 100
        }
        serviceInfo = info
    }
}
