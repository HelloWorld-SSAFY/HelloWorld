package com.ms.helloworld.network.api

import com.ms.helloworld.dto.request.SocialLoginRequest
import com.ms.helloworld.dto.request.GoogleLoginRequest
import com.ms.helloworld.dto.response.LoginResponse
import com.ms.helloworld.dto.response.RefreshTokenResponse
import com.ms.helloworld.network.RefreshTokenRequest
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
    ): RefreshTokenResponse
}