package com.ms.wearos.network.api

import com.ms.wearos.dto.response.UserInfoResponse
import retrofit2.Response
import retrofit2.http.GET

interface AuthApiService {
    @GET("/user/api/couples/me/detail")
    suspend fun getUserInfo(): Response<UserInfoResponse>
}