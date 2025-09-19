package com.ms.wearos.network.api

import com.ms.wearos.repository.WearApiResponse
import com.ms.wearos.repository.HeartRateRequest
import com.ms.wearos.repository.FetalMovementRequest
import com.ms.wearos.repository.LaborDataRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface WearApiService {

    @GET("/api/user/profile")
    suspend fun getUserData(
        @Header("Authorization") authorization: String
    ): Response<WearApiResponse>

    @GET("/api/wear/data")
    suspend fun getWearSpecificData(
        @Header("Authorization") authorization: String
    ): Response<WearApiResponse>

    @POST("/api/health/heart-rate")
    suspend fun sendHeartRateData(
        @Header("Authorization") authorization: String,
        @Body request: HeartRateRequest
    ): Response<WearApiResponse>

    @POST("/api/pregnancy/fetal-movement")
    suspend fun sendFetalMovementData(
        @Header("Authorization") authorization: String,
        @Body request: FetalMovementRequest
    ): Response<WearApiResponse>

    @POST("/api/pregnancy/labor")
    suspend fun sendLaborData(
        @Header("Authorization") authorization: String,
        @Body request: LaborDataRequest
    ): Response<WearApiResponse>
}