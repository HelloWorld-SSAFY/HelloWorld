package com.ms.wearos.network.api

import com.ms.wearos.dto.RefreshTokenResponse
import com.ms.wearos.network.RefreshTokenRequest
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    @POST("auth/refresh")
    suspend fun refreshToken(
        @Body request: RefreshTokenRequest
    ): RefreshTokenResponse
}