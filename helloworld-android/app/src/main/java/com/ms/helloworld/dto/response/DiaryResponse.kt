package com.ms.helloworld.dto.response

import com.google.gson.annotations.SerializedName

data class DiaryImage(
    @SerializedName("diaryPhotoId") val diaryPhotoId: Long? = null,
    @SerializedName("imageUrl") val imageUrl: String,
    @SerializedName("ultrasound") val isUltrasound: Boolean = false
)

data class DiaryResponse(
    @SerializedName("id") val diaryId: Long,
    @SerializedName("coupleId") val coupleId: Long,
    @SerializedName("authorId") val authorId: Long? = null,
    @SerializedName("authorRole") val authorRole: String? = null, // "FATHER" or "MOTHER"
    @SerializedName("author_role") val authorRoleSnake: String? = null, // fallback
    @SerializedName("role") val role: String? = null, // 다른 가능한 필드명
    @SerializedName("title") val diaryTitle: String?,
    @SerializedName("diaryTitle") val diaryTitleAlt: String?, // 서버에서 diaryTitle로 올 수도 있음
    @SerializedName("content") val diaryContent: String?,
    @SerializedName("diaryContent") val diaryContentAlt: String?, // 서버에서 diaryContent로 올 수도 있음
    @SerializedName("thumbnailUrl") val thumbnailUrl: String? = null, // 썸네일 URL
    @SerializedName("images") val images: List<DiaryImage>? = null, // 이미지 목록
    @SerializedName("entryDate") val entryDate: String? = null, // 작성 날짜
    @SerializedName("targetDate") val targetDate: String, // "yyyy-MM-dd" format
    @SerializedName("createdAt") val createdAt: String,
    @SerializedName("updatedAt") val updatedAt: String? = null
) {
    // 실제 authorRole 값을 반환 (여러 필드 중 첫 번째로 찾은 값)
    fun getActualAuthorRole(): String? {
        return authorRole ?: authorRoleSnake ?: role
    }

    // 실제 diaryTitle 값을 반환 (여러 필드 중 첫 번째로 찾은 값)
    fun getActualTitle(): String? {
        return diaryTitle ?: diaryTitleAlt
    }

    // 실제 diaryContent 값을 반환 (여러 필드 중 첫 번째로 찾은 값)
    fun getActualContent(): String? {
        return diaryContent ?: diaryContentAlt
    }

    // authorId와 현재 사용자 정보를 기반으로 role을 추론하는 함수
    fun inferAuthorRole(currentUserId: Long?, currentUserGender: String?, userAId: Long? = null, userBId: Long? = null): String? {
        // 1순위: 서버에서 제공된 authorRole 정보 사용 (수정 후)
        val serverRole = getActualAuthorRole()
        if (serverRole != null) {
            println("🔍 inferAuthorRole - 서버 role 사용: $serverRole")
            return serverRole
        }

        // 2순위: authorId가 있으면 커플 정보와 비교하여 정확한 role 결정
        if (authorId != null && userAId != null && userBId != null) {
            val inferredRole = when (authorId) {
                userAId -> "FEMALE"  // userA는 보통 여성 (FEMALE)
                userBId -> "MALE"    // userB는 보통 남성 (MALE)
                else -> null
            }
            println("🔍 inferAuthorRole - 커플 정보로 추론: authorId=$authorId, userA=$userAId, userB=$userBId -> $inferredRole")
            return inferredRole
        }

        // 3순위: authorId가 있고 현재 사용자와 일치하는 경우
        if (authorId != null && currentUserId != null && authorId == currentUserId) {
            val genderRole = when (currentUserGender?.lowercase()) {
                "female" -> "FEMALE"
                "male" -> "MALE"
                else -> null
            }
            println("🔍 inferAuthorRole - 현재 사용자 매칭: authorId=$authorId, currentUserId=$currentUserId -> $genderRole")
            return genderRole
        }

        // 4순위: 모든 정보가 없으면 null 반환 (정확하지 않은 추정 제거)
        println("🔍 inferAuthorRole - 충분한 정보 없음: authorId=$authorId, currentUserId=$currentUserId")
        return null
    }
}