package com.example.seaquakeiacourse

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val isFemale: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
