package com.ms.helloworld.dto.request

data class DiaryCreateRequest(
    val diaryTitle: String,
    val diaryContent: String
)