package com.ms.wearos.network.api

import com.ms.wearos.dto.request.FcmRegisterRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface FcmApi {

    @POST("user/api/fcm/register")
    suspend fun registerFcmToken(
        @Body body: FcmRegisterRequest
    ): Response<Unit> // 200 OK, body 없음

}