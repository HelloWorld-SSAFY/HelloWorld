package com.ms.helloworld.dto.request

import com.google.gson.annotations.SerializedName

data class CalendarUpdateRequest(
    val title: String? = null,
    val startAt: String? = null, // ISO 8601 format
    val endAt: String? = null,
    val isRemind: Boolean? = null,
    val memo: String? = null,
    val orderNo: Int? = null
)