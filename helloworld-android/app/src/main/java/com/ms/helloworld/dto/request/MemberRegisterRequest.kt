package com.ms.helloworld.dto.request

import com.google.gson.annotations.SerializedName

data class MemberRegisterRequest(
    @SerializedName("nickname") val nickname: String,
    @SerializedName("gender") val gender: String, // "female" or "male"
    @SerializedName("age") val age: Int,
    @SerializedName("menstrual_date") val menstrual_date: String? = null, // "yyyy-MM-dd" format, nullable
    @SerializedName("is_childbirth") val is_childbirth: Boolean? = null, // nullable로 변경
    @SerializedName("pregnancyWeek") val pregnancyWeek: Int? = null,
    @SerializedName("due_date") val due_date: String? = null // "yyyy-MM-dd" format, nullable
)