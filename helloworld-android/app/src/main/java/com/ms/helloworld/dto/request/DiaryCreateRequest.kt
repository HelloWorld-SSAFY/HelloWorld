package com.ms.helloworld.dto.request

import com.google.gson.annotations.SerializedName

data class DiaryCreateRequest(
    @SerializedName("entryDate") val entryDate: String,
    @SerializedName("diaryTitle") val diaryTitle: String,
    @SerializedName("diaryContent") val diaryContent: String,
    @SerializedName("imageUrl") val imageUrl: String,
    @SerializedName("coupleId") val coupleId: Long,
    @SerializedName("authorId") val authorId: Long,
    @SerializedName("authorRole") val authorRole: String,
    @SerializedName("targetDate") val targetDate: String,
    @SerializedName("createdAt") val createdAt: String,
    @SerializedName("updatedAt") val updatedAt: String
)