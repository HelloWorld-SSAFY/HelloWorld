package com.ms.helloworld.data

data class CalendarPost(
    val id: String = "",
    val date: String, // "2025-01-15" 형식
    val title: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)