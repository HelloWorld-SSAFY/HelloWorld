package com.ms.helloworld.dto.request

import com.google.gson.annotations.SerializedName

data class CalendarUpdateRequest(
    val title: String? = null,
    @SerializedName("start_at")
    val startAt: String? = null, // ISO 8601 format
    @SerializedName("end_at")
    val endAt: String? = null,
    @SerializedName("is_remind")
    val isRemind: Boolean? = null,
    val memo: String? = null,
    @SerializedName("order_no")
    val orderNo: Int? = null
)