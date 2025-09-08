package com.ms.helloworld.dto.request

data class UserInfoRequest(
    val nickname: String,
    val gender: String, // "엄마" or "아빠"
    val age: Int,
    val isFirstPregnancy: Boolean,
    val pregnancyCount: Int? = null,
    val lastMenstrualDate: String, // "yyyy-MM-dd" format
    val menstrualCycle: Int
)
