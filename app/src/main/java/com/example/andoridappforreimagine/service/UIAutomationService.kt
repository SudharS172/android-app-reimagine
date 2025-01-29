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

import java.io.DataOutputStream
import java.io.IOException

class UIAutomationService {
    companion object {
        private var instance: UIAutomationService? = null
        fun getInstance(): UIAutomationService? {
            if (instance == null) {
                instance = UIAutomationService()
            }
            return instance
        }
    }

    private var process: Process? = null
    private var outputStream: DataOutputStream? = null

    init {
        try {
            // Request root access
            process = Runtime.getRuntime().exec("su")
            outputStream = DataOutputStream(process!!.outputStream)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    suspend fun performAction(action: String, target: String): Boolean = withContext(Dispatchers.IO) {
        try {
            when (action.uppercase()) {
                "CLICK" -> {
                    // Use input tap command to click at coordinates
                    executeCommand("input tap $target")
                }
                "TYPE" -> {
                    // Use input text command to type
                    executeCommand("input text ${target.replace(" ", "%s")}")
                }
                "SCROLL" -> {
                    // Use input swipe command to scroll
                    when (target.uppercase()) {
                        "UP" -> executeCommand("input swipe 500 800 500 200")
                        "DOWN" -> executeCommand("input swipe 500 200 500 800")
                    }
                }
                "NAVIGATE" -> {
                    // Use am start command to launch apps
                    executeCommand("am start -n $target")
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun executeCommand(command: String): Boolean {
        return try {
            outputStream?.writeBytes("$command\n")
            outputStream?.flush()
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    fun cleanup() {
        try {
            outputStream?.close()
            process?.destroy()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
