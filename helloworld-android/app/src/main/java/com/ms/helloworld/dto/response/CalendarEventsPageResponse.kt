package com.ms.helloworld.dto.response

import com.google.gson.annotations.SerializedName

data class CalendarEventsPageResponse(
    val content: List<CalendarEventResponse>,  // 서버 응답 "content" 필드 매핑
    val page: Int? = null,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int? = null,
    val numberOfElements: Int? = null,
    val first: Boolean? = null,
    val last: Boolean? = null,
    val empty: Boolean? = null
)