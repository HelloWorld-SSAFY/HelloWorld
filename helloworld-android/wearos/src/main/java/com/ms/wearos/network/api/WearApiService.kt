package com.ms.wearos.network.api

import com.ms.wearos.dto.request.FetalMovementRequest
import com.ms.wearos.dto.request.HealthDataRequest
import com.ms.wearos.dto.request.LaborDataRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

interface WearApiService {

    // 건강 데이터 전송 (심박수 + 스트레스 지수)
    @POST("/health/api/wearable")
    suspend fun sendHealthData(
        @Query("coupleId") coupleId: Int,
        @Body healthData: HealthDataRequest
    ): Response<Any>

    // 태동 데이터 전송
    @POST("/health/api/fetal-movement")
    suspend fun sendFetalMovement(
        @Query("coupleId") coupleId: Int,
        @Body request: FetalMovementRequest
    ): Response<Any>

    // 진통 세션 전송
    @POST("/health/api/contractions")
    suspend fun sendLaborData(
        @Query("coupleId") coupleId: Int,
        @Body laborData: LaborDataRequest
    ): Response<Any>
}