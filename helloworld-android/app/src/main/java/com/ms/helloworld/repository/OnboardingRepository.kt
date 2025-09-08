package com.ms.helloworld.repository

import com.ms.helloworld.dto.request.UserInfoRequest
import com.ms.helloworld.network.api.OnboardingApiService
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnboardingRepository @Inject constructor(
    private val apiService: OnboardingApiService
) {
    suspend fun submitUserInfo(userInfo: UserInfoRequest): Result<Response<Unit>> {
        return try {
            val response = apiService.submitUserInfo(userInfo)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("서버 오류: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}