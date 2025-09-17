package com.ms.helloworld.dto.request

data class MemberUpdateRequest(
    val nickname: String? = null,
    val age: Int? = null,
    val menstrual_date: String? = null, // "yyyy-MM-dd" format
    val is_childbirth: Boolean? = null
)