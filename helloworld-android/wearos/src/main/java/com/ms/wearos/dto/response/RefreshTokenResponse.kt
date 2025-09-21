package com.ms.wearos.dto.response

data class RefreshTokenResponse(
    val accessToken: String,
    val refreshToken: String?
)