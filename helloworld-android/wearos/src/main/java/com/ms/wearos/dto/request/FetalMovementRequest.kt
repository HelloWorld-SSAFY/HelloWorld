package com.ms.wearos.dto.request

data class FetalMovementRequest(
    val timestamp: String,
    val recordedAt: Long = System.currentTimeMillis()
)