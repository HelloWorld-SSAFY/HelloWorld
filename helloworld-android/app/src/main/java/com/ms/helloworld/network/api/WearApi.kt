package com.ms.helloworld.network.api

import com.ms.helloworld.dto.response.MeditationResponse
import com.ms.helloworld.dto.response.MusicResponse
import com.ms.helloworld.dto.response.YogaResponse
import retrofit2.http.GET
import retrofit2.http.Header

interface WearApi {
    @GET("/ai/v1/delivery/meditation")
    suspend fun getMeditationRecommendations(
        @Header("X-App-Token") appToken: String
    ): MeditationResponse

    @GET("/ai/v1/delivery/yoga")
    suspend fun getYogaRecommendations(
        @Header("X-App-Token") appToken: String
    ): YogaResponse

    @GET("/ai/v1/delivery/music")
    suspend fun getMusicRecommendations(
        @Header("X-App-Token") appToken: String
    ): MusicResponse
}