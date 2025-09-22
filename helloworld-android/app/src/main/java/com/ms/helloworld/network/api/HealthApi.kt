package com.ms.helloworld.network.api

import com.ms.helloworld.dto.request.StepsRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface HealthApi {
    @POST("/health/api/steps")
    suspend fun submitSteps(@Body request: StepsRequest): Response<Unit>
}