package com.ms.helloworld.dto.request

data class CoupleUpdateRequest(
    val pregnancyWeek: Int? = null,
    val due_date: String? = null // "yyyy-MM-dd" format
)