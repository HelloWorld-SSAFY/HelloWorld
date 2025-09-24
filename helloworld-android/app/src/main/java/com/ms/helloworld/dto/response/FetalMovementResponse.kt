package com.ms.helloworld.dto.response

data class FetalMovementRecord(
    val recordedAt: String, // "2025-09-24T01:51:39.2782"
    val totalCount: Int     // 태동 횟수
)

data class FetalMovementResponse(
    val records: List<FetalMovementRecord>
)