package com.ms.wearos.dto.response

data class UserInfoResponse(
    val member: MemberInfo,
    val couple: CoupleInfo?
)

data class MemberInfo(
    val id: Long,
    val google_email: String,
    val nickname: String,
    val gender: String,
    val age: Int,
    val menstrual_date: String?,
    val is_childbirth: Boolean,
    val image_url: String?
)

data class CoupleInfo(
    val couple_id: Int,
    val user_a_id: Long,
    val user_b_id: Long,
    val pregnancy_week: Int,
    val due_date: String
)