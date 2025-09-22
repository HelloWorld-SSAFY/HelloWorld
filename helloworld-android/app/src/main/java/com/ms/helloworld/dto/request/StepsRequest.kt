package com.ms.helloworld.dto.request

data class StepsRequest(
    val date: String, // ISO 8601 format
    val steps: Int,
    val latitude: Double,
    val longitude: Double
)