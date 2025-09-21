package com.ms.wearos.dto.response

data class UserInfoResponse(
    val couple: CoupleInfo,
    val userA: MemberInfo,
    val userB: MemberInfo
)

data class MemberInfo(
    val id: Long,
    val nickname: String,
    val image_url: String?,
    val gender: String
)

data class CoupleInfo(
    val couple_id: Int,
    val user_a_id: Long,
    val user_b_id: Long,
    val pregnancyWeek: Int,
    val due_date: String,
    val menstrual_date: String,
    val is_childbirth: Boolean
)
