package com.ms.wearos.dto.request

data class HealthDataRequest(
    val date: String,
    val heartrate: Int,
    val stress: Int
)