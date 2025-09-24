package com.ms.helloworld.dto.response

data class ContractionSession(
    val id: String,
    val startTime: String, // ISO 8601 format: "2025-09-24T01:33:07.9772"
    val endTime: String,   // ISO 8601 format: "2025-09-24T01:33:07.9772"
    val durationSec: Int,  // 지속 시간 (초)
    val intervalMin: Int,  // 간격 (분)
    val alertSent: Boolean // 알림 전송 여부
)

data class ContractionsResponse(
    val sessions: List<ContractionSession>
)