package com.ms.helloworld.dto.response

import com.google.gson.annotations.SerializedName

data class CalendarEventResponse(
    val eventId: Long,      // 서버는 카멜케이스로 응답
    val coupleId: Long,
    val writerId: Long?,
    val title: String,
    val startAt: String,    // ISO 8601 format
    val endAt: String?,
    val memo: String?,
    val orderNo: Int?,
    val remind: Boolean     // 서버는 "remind"로 응답 ("isRemind" 아님)
)