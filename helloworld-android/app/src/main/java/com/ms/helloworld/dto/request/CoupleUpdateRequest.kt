package com.ms.helloworld.dto.request

data class CoupleUpdateRequest(
    val pregnancyWeek: Int? = null,
    val due_date: String? = null, // "yyyy-MM-dd" format
    val menstrual_date: String? = null, // "yyyy-MM-dd" format
    val is_childbirth: Boolean? = null
)