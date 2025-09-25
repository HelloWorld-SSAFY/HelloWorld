package com.ms.helloworld.network.api

import com.ms.helloworld.dto.request.StepsRequest
import com.ms.helloworld.dto.request.MaternalHealthCreateRequest
import com.ms.helloworld.dto.request.MaternalHealthUpdateRequest
import com.ms.helloworld.dto.response.ContractionsResponse
import com.ms.helloworld.dto.response.FetalMovementResponse
import com.ms.helloworld.dto.response.MaternalHealthGetResponse
import com.ms.helloworld.dto.response.MaternalHealthUpdateResponse
import com.ms.helloworld.dto.response.MaternalHealthListResponse
import retrofit2.Response
import retrofit2.http.*

interface HealthApi {
    @POST("/health/api/steps")
    suspend fun submitSteps(@Body request: StepsRequest): Response<Unit>

    @GET("/health/api/maternal-health/{maternalId}")
    suspend fun getMaternalHealthById(@Path("maternalId") maternalId: Long): Response<MaternalHealthGetResponse>

    @POST("/health/api/maternal-health")
    suspend fun createMaternalHealth(@Body request: MaternalHealthCreateRequest): Response<Unit>

    @PUT("/health/api/maternal-health/{maternalId}")
    suspend fun updateMaternalHealth(
        @Path("maternalId") maternalId: Long,
        @Body request: MaternalHealthUpdateRequest
    ): Response<MaternalHealthUpdateResponse>

    @DELETE("/health/api/maternal-health/{maternalId}")
    suspend fun deleteMaternalHealth(@Path("maternalId") maternalId: Long): Response<Unit>

    @GET("/health/api/maternal-health")
    suspend fun getMaternalHealthList(
        @Query("from") from: String? = null,
        @Query("to") to: String? = null
    ): Response<MaternalHealthListResponse>

    @GET("/health/api/contractions")
    suspend fun getContractions(
        @Query("from") from: String? = null,  // 시작 날짜 (선택적)
        @Query("to") to: String? = null       // 종료 날짜 (선택적)
    ): Response<ContractionsResponse>

    @GET("/health/api/fetal-movement")
    suspend fun getFetalMovement(
        @Query("from") from: String? = null,  // 시작 날짜 (선택적)
        @Query("to") to: String? = null       // 종료 날짜 (선택적)
    ): Response<FetalMovementResponse>
}