package com.ms.helloworld.dto.request

import com.google.gson.annotations.SerializedName

data class MemberRegisterRequest(
    @SerializedName("nickname") val nickname: String,
    @SerializedName("gender") val gender: String, // "female" or "male"
    @SerializedName("age") val age: Int
)