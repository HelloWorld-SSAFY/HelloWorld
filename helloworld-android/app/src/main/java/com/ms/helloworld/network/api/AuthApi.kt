package com.ms.helloworld.network.api

import com.ms.helloworld.dto.request.GoogleLoginRequest
import com.ms.helloworld.dto.request.RefreshTokenRequest
import com.ms.helloworld.dto.response.LoginResponse
import com.ms.helloworld.dto.response.TokenRefreshResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface AuthApi {
    @POST("user/api/auth/google")
    @Headers(
        "Content-Type: application/json",
        "Accept: application/json"
    )
    suspend fun socialLogin(
        @Body request: GoogleLoginRequest
    ): LoginResponse

    @POST("user/api/auth/refresh")
    suspend fun refreshToken(
        @Body request: RefreshTokenRequest
    ): Response<TokenRefreshResponse>

    @POST("user/auth/logout")
    suspend fun logout(): Response<Unit>
}