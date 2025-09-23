package com.ms.helloworld.dto.response

import com.google.gson.annotations.SerializedName

data class CoupleDetailResponse(
    val couple: CoupleProfile,
    val userA: UserDetail,
    val userB: UserDetail?
)

data class UserDetail(
    val id: Long,
    @SerializedName("nickname") val nickname: String?,
    @SerializedName("image_url") val imageUrl: String?,
    @SerializedName("gender") val gender: String?,
    @SerializedName("age") val age: Int?
)