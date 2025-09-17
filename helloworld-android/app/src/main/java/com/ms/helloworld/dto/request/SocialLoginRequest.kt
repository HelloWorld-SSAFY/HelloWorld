package com.ms.helloworld.dto.request

data class SocialLoginRequest(
    val provider: String, // "google" or "kakao"
    val token: String // ID Token (Google) or Access Token (Kakao)
)