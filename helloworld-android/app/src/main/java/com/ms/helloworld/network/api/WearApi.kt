package com.ms.helloworld.network.api

import com.ms.helloworld.dto.response.MeditationResponse
import retrofit2.http.GET
import retrofit2.http.Header

interface WearApi {
    @GET("/ai/v1/delivery/meditation")
    suspend fun getMeditationRecommendations(
        @Header("X-App-Token") appToken: String
    ): MeditationResponse
}