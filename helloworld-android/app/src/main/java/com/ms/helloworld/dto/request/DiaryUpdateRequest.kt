package com.ms.helloworld.dto.request

data class DiaryUpdateRequest(
    val diaryTitle: String,
    val diaryContent: String,
    val targetDate: String, // "yyyy-MM-dd" format
    val updatedAt: String
)