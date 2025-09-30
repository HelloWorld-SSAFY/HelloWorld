package com.ms.helloworld.dto.request

data class DiaryUpdateRequest(
    val entryDate: String,
    val diaryTitle: String,
    val diaryContent: String,
    val imageUrl: String
)