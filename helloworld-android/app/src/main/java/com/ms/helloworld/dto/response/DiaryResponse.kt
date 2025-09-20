package com.ms.helloworld.dto.response

import com.google.gson.annotations.SerializedName

data class DiaryResponse(
    @SerializedName("diary_id") val diaryId: Long,
    @SerializedName("couple_id") val coupleId: Long,
    @SerializedName("author_id") val authorId: Long,
    @SerializedName("author_role") val authorRole: String, // "FATHER" or "MOTHER"
    @SerializedName("diary_title") val diaryTitle: String?,
    @SerializedName("diary_content") val diaryContent: String?,
    @SerializedName("target_date") val targetDate: String, // "yyyy-MM-dd" format
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String
)