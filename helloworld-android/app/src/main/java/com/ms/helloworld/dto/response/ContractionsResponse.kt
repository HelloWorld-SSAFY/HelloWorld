package com.ms.helloworld.dto.response

import com.google.gson.annotations.SerializedName

data class ContractionSession(
    val id: String,
    @SerializedName("start_time")
    val startTime: String, // ISO 8601 format: "2025-09-24T01:33:07.9772"
    @SerializedName("end_time")
    val endTime: String,   // ISO 8601 format: "2025-09-24T01:33:07.9772"
    @SerializedName("duration_sec")
    val durationSec: Int,  // 지속 시간 (초)
    @SerializedName("interval_min")
    val intervalMin: Int,  // 간격 (분)
    @SerializedName("alert_sent")
    val alertSent: Boolean // 알림 전송 여부
)

data class ContractionsResponse(
    val sessions: List<ContractionSession>
)