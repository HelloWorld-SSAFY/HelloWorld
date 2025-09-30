package com.ms.wearos.dto.request

data class HealthDataRequest(
    val date: String,
    val stress: Int,
    val heartrate: Int
)