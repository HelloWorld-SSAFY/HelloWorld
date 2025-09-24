package com.ms.wearos.dto.request

data class FcmRegisterRequest(
    val token: String,
//    val platform: String // "ANDROID", "WATCH" 등 서버 규약대로
)
object Platforms {
    const val ANDROID = "ANDROID"
    const val WATCH = "WATCH"
}