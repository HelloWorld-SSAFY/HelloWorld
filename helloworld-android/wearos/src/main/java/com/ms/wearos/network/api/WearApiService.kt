package com.ms.wearos.network.api

import com.ms.wearos.dto.request.HealthDataRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

interface WearApiService {

    // 건강 데이터 전송 (심박수 + 스트레스 지수)
    @POST("/health/api/records")
    suspend fun sendHealthData(
        @Query("coupleId") coupleId: Int,
        @Body healthData: HealthDataRequest
    ): Response<Any>


}