package com.ms.helloworld.dto.request

data class DiaryUpdateRequest(
    val diaryTitle: String,
    val diaryContent: String
)