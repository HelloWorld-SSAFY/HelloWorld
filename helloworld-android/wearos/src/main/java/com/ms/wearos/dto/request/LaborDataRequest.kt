package com.ms.wearos.dto.request

import com.google.gson.annotations.SerializedName

data class LaborDataRequest(
    @SerializedName("start_time")
    val startTime: String,
    @SerializedName("end_time")
    val endTime: String
)