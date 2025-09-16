package com.ms.helloworld.dto.response

import com.google.gson.annotations.SerializedName

data class CalendarEventResponse(
    @SerializedName("eventId")
    val eventId: Long,
    @SerializedName("coupleId")
    val coupleId: Long,
    @SerializedName("writerId")
    val writerId: Long?,
    val title: String,
    @SerializedName("startAt")
    val startAt: String, // ISO 8601 format
    @SerializedName("endAt")
    val endAt: String?,
    val memo: String?,
    @SerializedName("orderNo")
    val orderNo: Int?,
    @SerializedName("isRemind")
    val isRemind: Boolean
)