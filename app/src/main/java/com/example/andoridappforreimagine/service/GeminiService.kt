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
            
            Command: $command
            
            Respond in the following JSON format:
            {
                "type": "NAVIGATE" | "CLICK" | "TYPE" | "SCROLL" | "ERROR",
                "message": "Your explanation of what you're going to do",
                "action": {
                    "type": "NAVIGATE" | "CLICK" | "TYPE" | "SCROLL",
                    "target": "Button/element to interact with or text to type",
                    "coordinates": [x, y] (optional, for click actions)
                }
            }
        """.trimIndent()
    }

    private fun buildPromptWithScreenshot(command: String): String {
        return """
            You are an AI assistant that helps users navigate through Android apps and perform actions.
            Analyze the provided screenshot and the user's command to decide what UI action to take.
            
            Command: $command
            
            Look at the screenshot and identify UI elements that match the user's intent.
            Consider buttons, text fields, links, and other interactive elements.
            
            Respond in the following JSON format:
            {
                "type": "NAVIGATE" | "CLICK" | "TYPE" | "SCROLL" | "ERROR",
                "message": "Your explanation of what you see and what action you'll take",
                "action": {
                    "type": "NAVIGATE" | "CLICK" | "TYPE" | "SCROLL",
                    "target": "Button/element to interact with or text to type",
                    "coordinates": [x, y] (required for click actions, based on visible elements)
                }
            }
        """.trimIndent()
    }

    private fun parseResponse(response: String): AIResponse {
        try {
            // Extract JSON from the response
            val jsonPattern = """\{[^}]*\}""".toRegex()
            val jsonMatch = jsonPattern.find(response)
            val jsonStr = jsonMatch?.value ?: return AIResponse(
                type = ResponseType.ERROR,
                message = "Invalid response format",
                action = null
            )

            // Parse JSON
            val json = org.json.JSONObject(jsonStr)
            val type = json.getString("type")
            val message = json.getString("message")
            
            val action = if (json.has("action")) {
                val actionJson = json.getJSONObject("action")
                UIAction(
                    type = ActionType.valueOf(actionJson.getString("type")),
                    target = actionJson.getString("target"),
                    coordinates = if (actionJson.has("coordinates")) {
                        val coords = actionJson.getJSONArray("coordinates")
                        Pair(coords.getDouble(0).toFloat(), coords.getDouble(1).toFloat())
                    } else null
                )
            } else null

            return AIResponse(
                type = ResponseType.valueOf(type),
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
