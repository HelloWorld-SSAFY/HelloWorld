package com.ms.helloworld.dto.response

import com.google.gson.annotations.SerializedName

data class CaricatureResponse(
    @SerializedName("id") val id: Long,
    @SerializedName("diaryPhotoId") val diaryPhotoId: Long,
    @SerializedName("imageUrl") val imageUrl: String
)