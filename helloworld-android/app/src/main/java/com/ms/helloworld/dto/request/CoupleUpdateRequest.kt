package com.ms.helloworld.dto.request

import com.google.gson.annotations.SerializedName

data class CoupleUpdateRequest(
    val pregnancyWeek: Int? = null,
    @SerializedName("due_date") val due_date: String? = null, // "yyyy-MM-dd" format
    @SerializedName("menstrual_date") val menstrual_date: String? = null, // "yyyy-MM-dd" format
    @SerializedName("is_childbirth") val is_childbirth: Boolean? = null
)