package com.example.andoridappforreimagine.service

import android.graphics.Bitmap
import android.util.Base64
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class GeminiService(apiKey: String) {
    private val textModel = GenerativeModel(
        modelName = "gemini-pro",
        apiKey = apiKey,
        generationConfig = generationConfig {
            temperature = 0.7f
            topK = 40
            topP = 0.95f
            maxOutputTokens = 1024
        }
    )

    private val visionModel = GenerativeModel(
        modelName = "gemini-pro-vision",
        apiKey = apiKey
    )

    suspend fun processCommand(
        command: String,
        screenshot: Bitmap? = null
    ): AIResponse = withContext(Dispatchers.IO) {
        try {
            val response = if (screenshot != null) {
                // Process command with screenshot
                visionModel.generateContent(
                    content {
                        text(buildPromptWithScreenshot(command))
                        image(screenshot)
                    }
                ).text ?: "Error: No response from Gemini"
            } else {
                // Process text-only command
                textModel.generateContent(
                    content {
                        text(buildPrompt(command))
                    }
                ).text ?: "Error: No response from Gemini"
            }

            parseResponse(response)
        } catch (e: Exception) {
            AIResponse(
                type = ResponseType.ERROR,
                message = "Error processing command: ${e.message}",
                action = null
            )
        }
    }

    private fun buildPrompt(command: String): String {
        return """
            You are an AI assistant that helps users navigate through Android apps and perform actions.
            Based on the user's command, you should decide what UI actions to take.
            
            IMPORTANT: You must respond with a complete, valid JSON object and nothing else.
            Do not include any explanatory text outside the JSON.
            
            Command: $command
            
            Response format:
            For navigation commands (e.g., "open Chrome", "go to Settings"):
            {
                "type": "NAVIGATE",
                "message": "Brief description of the navigation action",
                "action": {
                    "type": "NAVIGATE",
                    "target": "exact name of the app or element to navigate to"
                }
            }
            
            For click actions (e.g., "tap button", "press OK"):
            {
                "type": "CLICK",
                "message": "Brief description of the click action",
                "action": {
                    "type": "CLICK",
                    "target": "exact text or description of what to click"
                }
            }
            
            For errors or unknown commands:
            {
                "type": "ERROR",
                "message": "Error description",
                "action": null
            }
        """.trimIndent()
    }

    private fun buildPromptWithScreenshot(command: String): String {
        return """
            You are an AI assistant that helps users navigate through Android apps and perform actions.
            Analyze the provided screenshot and the user's command to decide what UI action to take.
            
            IMPORTANT: You must respond with a complete, valid JSON object and nothing else.
            Do not include any explanatory text outside the JSON.
            
            Command: $command
            
            Response format:
            For navigation commands (e.g., "open Chrome", "go to Settings"):
            {
                "type": "NAVIGATE",
                "message": "Brief description of what you see and the navigation action",
                "action": {
                    "type": "NAVIGATE",
                    "target": "exact name of the visible app or element to navigate to"
                }
            }
            
            For click actions (e.g., "tap button", "press OK"):
            {
                "type": "CLICK",
                "message": "Brief description of what you see and the click action",
                "action": {
                    "type": "CLICK",
                    "target": "exact visible text or element description to click"
                }
            }
            
            For errors or when target is not visible:
            {
                "type": "ERROR",
                "message": "Description of what you see and why the action cannot be performed",
                "action": null
            }
        """.trimIndent()
    }

    private fun parseResponse(response: String): AIResponse {
        try {
            // Find the complete JSON object in the response
            val start = response.indexOf("{")
            val end = response.lastIndexOf("}") + 1
            if (start == -1 || end == -1 || end <= start) {
                return AIResponse(
                    type = ResponseType.ERROR,
                    message = "Invalid response format: No valid JSON found",
                    action = null
                )
            }

            val jsonStr = response.substring(start, end)
            
            // Parse JSON
            val json = org.json.JSONObject(jsonStr)
            val type = json.optString("type", "ERROR")
            val message = json.optString("message", "Unknown message")
            
            val action = if (json.has("action") && !json.isNull("action")) {
                val actionJson = json.getJSONObject("action")
                UIAction(
                    type = try {
                        ActionType.valueOf(actionJson.optString("type", "NAVIGATE"))
                    } catch (e: IllegalArgumentException) {
                        ActionType.NAVIGATE
                    },
                    target = actionJson.optString("target", ""),
                    coordinates = if (actionJson.has("coordinates") && !actionJson.isNull("coordinates")) {
                        val coords = actionJson.getJSONArray("coordinates")
                        if (coords.length() >= 2) {
                            Pair(coords.optDouble(0, 0.0).toFloat(), coords.optDouble(1, 0.0).toFloat())
                        } else null
                    } else null
                )
            } else null

            return AIResponse(
                type = try {
                    ResponseType.valueOf(type)
                } catch (e: IllegalArgumentException) {
                    ResponseType.ERROR
                },
                message = message,
                action = action
            )
        } catch (e: Exception) {
            return AIResponse(
                type = ResponseType.ERROR,
                message = "Error parsing response: ${e.message}",
                action = null
            )
        }
    }
}

data class AIResponse(
    val type: ResponseType,
    val message: String,
    val action: UIAction?
)

enum class ResponseType {
    NAVIGATE,
    CLICK,
    TYPE,
    SCROLL,
    ERROR
}

data class UIAction(
    val type: ActionType,
    val target: String,
    val coordinates: Pair<Float, Float>?
)

enum class ActionType {
    NAVIGATE,
    CLICK,
    TYPE,
    SCROLL
}
