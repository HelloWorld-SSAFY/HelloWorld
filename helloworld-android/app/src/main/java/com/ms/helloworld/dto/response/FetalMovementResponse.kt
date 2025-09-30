package com.ms.helloworld.dto.response

import com.google.gson.annotations.SerializedName

data class FetalMovementRecord(
    @SerializedName("recorded_at")
    val recordedAt: String, // "2025-09-24T01:51:39.2782"
    @SerializedName("total_count")
    val totalCount: Int     // 태동 횟수
)

data class FetalMovementResponse(
    val records: List<FetalMovementRecord>
)