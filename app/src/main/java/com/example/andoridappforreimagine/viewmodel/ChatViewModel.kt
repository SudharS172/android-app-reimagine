package com.example.andoridappforreimagine.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.andoridappforreimagine.data.ChatMessage
import com.example.andoridappforreimagine.data.MessageStatus
import com.example.andoridappforreimagine.service.ActionType
import com.example.andoridappforreimagine.service.GeminiService
import com.example.andoridappforreimagine.service.ResponseType
import com.example.andoridappforreimagine.service.ScreenCaptureManager
import com.example.andoridappforreimagine.service.UIAutomationService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(
    private val context: Context,
    private val geminiApiKey: String
) : ViewModel() {
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val geminiService = GeminiService(geminiApiKey)
    private val screenCaptureManager = ScreenCaptureManager(context)
    private var automationService: UIAutomationService? = null

    fun setAutomationService(service: UIAutomationService?) {
        automationService = service
    }

    fun onInputTextChanged(text: String) {
        _inputText.value = text
    }

    fun sendMessage() {
        val text = _inputText.value.trim()
        if (text.isEmpty() || _isProcessing.value) return

        viewModelScope.launch {
            try {
                // Add user message
                val userMessage = ChatMessage(
                    content = text,
                    isUser = true
                )
                _messages.value = _messages.value + userMessage
                _inputText.value = ""
                _isProcessing.value = true

                // Add AI processing message
                val processingMessage = ChatMessage(
                    content = "Processing your request...",
                    isUser = false,
                    status = MessageStatus.PROCESSING
                )
                _messages.value = _messages.value + processingMessage

                // Capture screenshot if available
                val screenshot = try {
                    screenCaptureManager.captureScreen()
                } catch (e: Exception) {
                    null
                }

                // Process with Gemini
                val response = geminiService.processCommand(text, screenshot)

                // Update processing message with response
                val updatedMessages = _messages.value.toMutableList()
                val processingIndex = updatedMessages.indexOf(processingMessage)
                if (processingIndex != -1) {
                    val status = when {
                        response.type == ResponseType.ERROR -> MessageStatus.ERROR
                        automationService == null -> {
                            response.message + "\n\nError: Accessibility service not enabled. Please enable it in Settings."
                            MessageStatus.ERROR
                        }
                        else -> MessageStatus.ACTION_IN_PROGRESS
                    }
                    
                    updatedMessages[processingIndex] = processingMessage.copy(
                        content = response.message,
                        status = status
                    )
                    _messages.value = updatedMessages

                    // Execute action if available and service is ready
                    if (response.type != ResponseType.ERROR && automationService != null) {
                        response.action?.let { action ->
                            try {
                                val success = automationService?.performAction(
                                    action = action.type.name,
                                    target = action.target
                                ) ?: false
                                
                                updateActionStatus(
                                    messageIndex = processingIndex,
                                    success = success,
                                    errorMessage = if (!success) "Failed to perform action: ${action.type} on ${action.target}" else null
                                )
                            } catch (e: Exception) {
                                updateActionStatus(
                                    messageIndex = processingIndex,
                                    success = false,
                                    errorMessage = "Error performing action: ${e.message}"
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Add error message with more details
                _messages.value = _messages.value + ChatMessage(
                    content = "Error processing request: ${e.message}\n\nPlease try again or check if all permissions are granted.",
                    isUser = false,
                    status = MessageStatus.ERROR
                )
            } finally {
                _isProcessing.value = false
            }
        }
    }

    private fun updateActionStatus(messageIndex: Int, success: Boolean, errorMessage: String? = null) {
        val updatedMessages = _messages.value.toMutableList()
        if (messageIndex != -1 && messageIndex < updatedMessages.size) {
            val currentMessage = updatedMessages[messageIndex]
            updatedMessages[messageIndex] = currentMessage.copy(
                content = if (errorMessage != null) "${currentMessage.content}\n\n$errorMessage" else currentMessage.content,
                status = if (success) MessageStatus.ACTION_COMPLETED else MessageStatus.ACTION_FAILED
            )
            _messages.value = updatedMessages
        }
    }

    fun clearChat() {
        _messages.value = emptyList()
    }

    override fun onCleared() {
        super.onCleared()
        screenCaptureManager.release()
    }
}
