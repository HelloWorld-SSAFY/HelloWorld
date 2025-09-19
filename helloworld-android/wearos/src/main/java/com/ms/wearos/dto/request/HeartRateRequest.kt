package com.ms.wearos.dto.request

data class HeartRateRequest(
    val heartRate: Double,
    val timestamp: Long = System.currentTimeMillis()
)