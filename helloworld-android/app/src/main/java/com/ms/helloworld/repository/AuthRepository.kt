package com.ms.helloworld.repository

import android.util.Log
import com.ms.helloworld.dto.request.SocialLoginRequest
import com.ms.helloworld.dto.request.GoogleLoginRequest
import com.ms.helloworld.dto.request.RefreshTokenRequest
import com.ms.helloworld.dto.response.LoginResponse
import com.ms.helloworld.dto.response.RefreshTokenResponse
import com.ms.helloworld.network.api.AuthApi
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "싸피_AuthRepository"
@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val fcmRepository: FcmRepository
) {
    suspend fun socialLogin(request: GoogleLoginRequest): LoginResponse? {
        return try {
            Log.d(TAG, "Making API call to socialLogin")
            Log.d(TAG, "Target URL will be: BASE_URL/user/api/auth/google")
            Log.d(TAG, "Request provider: Google")
            Log.d(TAG, "Request token length: ${request.idToken.length}")
            Log.d(TAG, "Request token starts with: ${request.idToken.take(50)}...")
            Log.d(TAG, "Full request body: {\"idToken\":\"${request.idToken.take(100)}...\"}")

            // JSON 직렬화 확인
            val gson = com.google.gson.Gson()
            val jsonBody = gson.toJson(request)
            Log.d(TAG, "Gson serialized body: ${jsonBody.take(200)}...")

            val response = authApi.socialLogin(request)
            Log.d(TAG, "API response received: $response")
            Log.d(TAG, "Response accessToken: ${response?.accessToken}")
            Log.d(TAG, "Response refreshToken: ${response?.refreshToken}")

            // 로그인 성공 시 FCM 토큰 등록
            response?.let {
                Log.d(TAG, "로그인 성공 - FCM 토큰 등록 시작")
                fcmRepository.registerTokenAsync(platform = "ANDROID")
            }

            response
        } catch (e: Exception) {
            Log.e(TAG, "API call failed", e)
            Log.e(TAG, "Exception message: ${e.message}")
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")

            if (e is retrofit2.HttpException) {
                try {
                    val errorBody = e.response()?.errorBody()?.string()
                    Log.e(TAG, "HTTP Error Code: ${e.code()}")
                    Log.e(TAG, "HTTP Error Body: $errorBody")
                } catch (ioException: Exception) {
                    Log.e(TAG, "Failed to read error body: ${ioException.message}")
                }
            }
            null
        }
    }

    suspend fun refreshToken(refreshToken: String): RefreshTokenResponse? {
        return try {
            Log.d(TAG, "Making API call to refreshToken")
            Log.d(TAG, "Target URL will be: BASE_URL/api/auth/refresh")
            Log.d(TAG, "Refresh token length: ${refreshToken.length}")
            Log.d(TAG, "Refresh token starts with: ${refreshToken.take(50)}...")

            val request = RefreshTokenRequest(refreshToken = refreshToken)

            // JSON 직렬화 확인
            val gson = com.google.gson.Gson()
            val jsonBody = gson.toJson(request)
            Log.d(TAG, "Gson serialized body: ${jsonBody.take(200)}...")

            val response = authApi.refreshToken(request)
            Log.d(TAG, "Refresh API response received: $response")
            Log.d(TAG, "New accessToken: ${response?.accessToken}")
            Log.d(TAG, "New refreshToken: ${response?.refreshToken}")

            response
        } catch (e: Exception) {
            Log.e(TAG, "Refresh token API call failed", e)
            Log.e(TAG, "Exception message: ${e.message}")
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")

            if (e is retrofit2.HttpException) {
                try {
                    val errorBody = e.response()?.errorBody()?.string()
                    Log.e(TAG, "HTTP Error Code: ${e.code()}")
                    Log.e(TAG, "HTTP Error Body: $errorBody")

                    // 401, 403 등은 토큰이 만료되었거나 유효하지 않음을 의미
                    when (e.code()) {
                        401 -> Log.e(TAG, "Unauthorized - Refresh token expired or invalid")
                        403 -> Log.e(TAG, "Forbidden - Refresh token not allowed")
                        404 -> Log.e(TAG, "Not Found - Refresh endpoint not found")
                        500 -> Log.e(TAG, "Internal Server Error")
                    }
                } catch (ioException: Exception) {
                    Log.e(TAG, "Failed to read error body: ${ioException.message}")
                }
            }
            null
        }
    }

    suspend fun logout(): Boolean {
        return try {
            Log.d(TAG, "Making API call to logout")
            Log.d(TAG, "Target URL will be: BASE_URL/user/auth/logout")

            val response = authApi.logout()

            Log.d(TAG, "Logout API response code: ${response.code()}")
            Log.d(TAG, "Logout successful: ${response.isSuccessful}")

            response.isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "Logout API call failed", e)
            Log.e(TAG, "Exception message: ${e.message}")
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")

            if (e is retrofit2.HttpException) {
                try {
                    val errorBody = e.response()?.errorBody()?.string()
                    Log.e(TAG, "HTTP Error Code: ${e.code()}")
                    Log.e(TAG, "HTTP Error Body: $errorBody")
                } catch (ioException: Exception) {
                    Log.e(TAG, "Failed to read error body: ${ioException.message}")
                }
            }
            false
        }
    }
}