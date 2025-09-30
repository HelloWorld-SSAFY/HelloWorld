package com.ms.helloworld.dto.request

import com.google.gson.annotations.SerializedName

data class CalendarCreateRequest(
    val title: String,
    val startAt: String, // ISO 8601 format - 카멜케이스로 전송
    val endAt: String? = null,
    val isRemind: Boolean = false,
    val memo: String? = null,
    val orderNo: Int? = null
)