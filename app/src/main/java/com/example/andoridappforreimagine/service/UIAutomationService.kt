package com.example.andoridappforreimagine.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class UIAutomationService : AccessibilityService() {

    private val _currentScreen = MutableStateFlow<AccessibilityNodeInfo?>(null)
    val currentScreen: StateFlow<AccessibilityNodeInfo?> = _currentScreen.asStateFlow()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let {
            when (it.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                    rootInActiveWindow?.let { root ->
                        _currentScreen.value = root
                    }
                }
                else -> {
                    // Ignore other event types
                }
            }
        }
    }

    override fun onInterrupt() {
        // Handle interruption
    }

    fun click(x: Float, y: Float) {
        val path = Path().apply {
            moveTo(x, y)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()

        dispatchGesture(gesture, null, null)
    }

    fun findClickableNodeAtLocation(x: Float, y: Float): AccessibilityNodeInfo? {
        return rootInActiveWindow?.let { root ->
            findClickableNode(root, x, y)
        }
    }

    private fun findClickableNode(
        node: AccessibilityNodeInfo,
        x: Float,
        y: Float
    ): AccessibilityNodeInfo? {
        val rect = Rect()
        node.getBoundsInScreen(rect)

        if (rect.contains(x.toInt(), y.toInt())) {
            if (node.isClickable) {
                return node
            }
            // Check children
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { child ->
                    findClickableNode(child, x, y)?.let { found ->
                        return found
                    }
                }
            }
        }
        return null
    }

    fun findNodeByText(text: String): AccessibilityNodeInfo? {
        return rootInActiveWindow?.let { root ->
            findNodeByTextRecursive(root, text)
        }
    }

    private fun findNodeByTextRecursive(
        node: AccessibilityNodeInfo,
        text: String
    ): AccessibilityNodeInfo? {
        if (node.text?.contains(text, ignoreCase = true) == true) {
            return node
        }

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                findNodeByTextRecursive(child, text)?.let { found ->
                    return found
                }
            }
        }
        return null
    }

    fun navigateToApp(appName: String): Boolean {
        // Try to find and click on the app icon
        val node = findNodeByText(appName)
        if (node != null) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            click(rect.exactCenterX(), rect.exactCenterY())
            return true
        }
        return false
    }

    fun performAction(action: String, target: String): Boolean {
        return when (action.uppercase()) {
            "NAVIGATE" -> navigateToApp(target)
            "CLICK" -> {
                val node = findNodeByText(target)
                if (node != null) {
                    val rect = Rect()
                    node.getBoundsInScreen(rect)
                    click(rect.exactCenterX(), rect.exactCenterY())
                    true
                } else false
            }
            "TYPE" -> {
                // Find a text field and type into it
                val node = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                if (node?.isEditable == true) {
                    val arguments = Bundle()
                    arguments.putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        target
                    )
                    node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                    true
                } else false
            }
            "SCROLL" -> {
                rootInActiveWindow?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                    ?: false
            }
            else -> false
        }
    }

    companion object {
        private var instance: UIAutomationService? = null

        fun getInstance(): UIAutomationService? = instance

        internal fun setInstance(service: UIAutomationService) {
            instance = service
        }
    }

    init {
        setInstance(this)
    }
}
