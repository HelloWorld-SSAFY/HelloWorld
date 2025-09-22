package com.ms.helloworld.dto.response

data class TokenRefreshResponse(
    val accessToken: String,
    val refreshToken: String?
)