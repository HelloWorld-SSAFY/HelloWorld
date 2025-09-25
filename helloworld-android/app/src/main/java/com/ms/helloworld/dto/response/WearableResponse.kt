package com.ms.helloworld.dto.response

data class WearableResponse(
    val step: StepsData,
    val heartrate: HeartrateData
)

data class StepsData(
    val steps_id: String,
    val date: String,
    val steps: Int,
    val latitude: Double,
    val longitude: Double
)

data class HeartrateData(
    val health_id: String,
    val date: String,
    val hr: Int,
    val stress: Int
)