package com.ms.wearos.dto.request

import com.google.gson.annotations.SerializedName

data class FetalMovementRequest(
    @SerializedName("recorded_at")
    val recordedAt: String
)