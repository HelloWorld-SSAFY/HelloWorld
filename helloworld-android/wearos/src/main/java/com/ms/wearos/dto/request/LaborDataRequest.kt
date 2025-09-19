package com.ms.wearos.dto.request

data class LaborDataRequest(
    val isActive: Boolean,
    val duration: String?,
    val interval: String?,
    val timestamp: Long = System.currentTimeMillis()
)