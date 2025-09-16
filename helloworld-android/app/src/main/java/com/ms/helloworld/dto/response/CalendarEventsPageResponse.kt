package com.ms.helloworld.dto.response

import com.google.gson.annotations.SerializedName

data class CalendarEventsPageResponse(
    val events: List<CalendarEventResponse>,
    val page: Int,
    val size: Int,
    @SerializedName("total_elements")
    val totalElements: Long
)