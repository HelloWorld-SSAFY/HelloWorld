package com.ms.helloworld.network.api

import com.ms.helloworld.dto.request.UserInfoRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface OnboardingApiService {
    // Todo: 실제 API 엔드포인트에 맞게 경로 수정 필요
    @POST("api/user/onboarding")
    suspend fun submitUserInfo(
        @Body userInfo: UserInfoRequest
    ): Response<Response<Unit>>
}