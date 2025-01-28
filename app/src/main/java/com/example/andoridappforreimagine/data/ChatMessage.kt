package com.example.andoridappforreimagine.data

import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.SENT
)

enum class MessageStatus {
    SENT,
    PROCESSING,
    ERROR,
    ACTION_IN_PROGRESS,
    ACTION_COMPLETED,
    ACTION_FAILED
}
