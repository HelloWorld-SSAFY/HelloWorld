package com.ms.helloworld.dto.response

// Todo: 실제 API 응답에 맞게 필드 수정 필요
data class RefreshTokenResponse(
    val accessToken: String,
    val refreshToken: String?
)