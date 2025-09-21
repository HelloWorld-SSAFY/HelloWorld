package com.ms.wearos.dto.request

data class HealthRecordRequest(
    val date: String,
    val stress: Int,
    val heartrate: Int,
)