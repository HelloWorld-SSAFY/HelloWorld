package com.ms.wearos.network.api

import com.ms.wearos.dto.request.FetalMovementRequest
import com.ms.wearos.dto.request.HealthDataRequest
import com.ms.wearos.dto.request.LaborDataRequest
import com.ms.wearos.dto.response.AiResponse
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

interface WearApiService {

    // 건강 데이터 전송 (심박수 + 스트레스 지수)
    @POST("/health/api/wearable")
    suspend fun sendHealthData(
        @Body healthData: HealthDataRequest
    ): Response<AiResponse>

    // 태동 데이터 전송
    @POST("/health/api/fetal-movement")
    suspend fun sendFetalMovement(
        @Body body: RequestBody
    ): Response<Any>

    // 진통 세션 전송
    @POST("/health/api/contractions")
    suspend fun sendLaborData(
        @Body laborData: LaborDataRequest
    ): Response<Any>
}