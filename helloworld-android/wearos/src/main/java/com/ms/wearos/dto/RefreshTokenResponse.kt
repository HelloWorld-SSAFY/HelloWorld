package com.ms.wearos.dto

data class RefreshTokenResponse(
    val accessToken: String,
    val refreshToken: String?
)