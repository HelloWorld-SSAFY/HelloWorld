package com.ms.helloworld.model

enum class OnboardingStatus {
    NOT_STARTED,        // 아직 시작 안함 (member 정보 없음)
    BASIC_COMPLETED,    // 기본 정보만 완료 (member 있지만 couple 불완전)
    FULLY_COMPLETED     // 모든 온보딩 완료
}

data class OnboardingCheckResult(
    val status: OnboardingStatus,
    val userGender: String? = null,
    val shouldGoToMomForm: Boolean = false,
    val shouldGoToDadForm: Boolean = false
)