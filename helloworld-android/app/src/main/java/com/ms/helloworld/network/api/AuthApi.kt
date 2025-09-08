package com.ms.helloworld.network.api

import com.ms.helloworld.dto.response.RefreshTokenResponse
import com.ms.helloworld.network.RefreshTokenRequest
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    @POST("auth/refresh")
    suspend fun refreshToken(
        @Body request: RefreshTokenRequest
    ): RefreshTokenResponse
}