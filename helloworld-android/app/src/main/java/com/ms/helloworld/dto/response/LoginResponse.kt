package com.ms.helloworld.dto.response

data class LoginResponse(
    val memberId: Long,
    val accessToken: String,
    val refreshToken: String,
    val gender: String? = null
)