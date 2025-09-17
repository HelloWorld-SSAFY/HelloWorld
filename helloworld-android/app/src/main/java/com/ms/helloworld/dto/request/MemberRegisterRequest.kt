package com.ms.helloworld.dto.request

data class MemberRegisterRequest(
    val nickname: String,
    val gender: String, // "female" or "male"
    val age: Int,
    val menstrual_date: String? = null, // "yyyy-MM-dd" format, nullable
    val is_childbirth: Boolean? = null, // nullable로 변경
    val pregnancyWeek: Int? = null,
    val due_date: String? = null, // "yyyy-MM-dd" format, nullable
    val invitationCode: String? = null // 아빠용 초대 코드
)