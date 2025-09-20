package com.ms.helloworld.dto.response

import com.google.gson.annotations.SerializedName

data class MemberProfileResponse(
    val member: MemberProfile,
    val couple: CoupleProfile?
)

data class MemberProfile(
    val id: Long,
    @SerializedName("google_email") val googleEmail: String?,
    val nickname: String?,
    val gender: String?, // "FEMALE" or "MALE"
    @SerializedName("image_url") val imageUrl: String?,
    val age: Int?
)

data class CoupleProfile(
    @SerializedName("couple_id") val id: Long,
    @SerializedName("user_a_id") val userAId: Long?,
    @SerializedName("user_b_id") val userBId: Long?,
    @SerializedName("pregnancy_week") val pregnancyWeek: Int?,
    @SerializedName("due_date") val dueDate: String?, // "yyyy-MM-dd" format
    @SerializedName("menstrual_date") val menstrualDate: String?, // "yyyy-MM-dd" format
    @SerializedName("is_childbirth") val isChildbirth: Boolean?
)

data class PartnerInfo(
    val id: Long,
    val nickname: String?,
    val gender: String?,
    val imageUrl: String?
)