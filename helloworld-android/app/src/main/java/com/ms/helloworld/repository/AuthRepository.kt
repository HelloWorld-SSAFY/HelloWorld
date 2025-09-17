package com.ms.helloworld.repository

import android.util.Log
import com.ms.helloworld.dto.request.SocialLoginRequest
import com.ms.helloworld.dto.request.GoogleLoginRequest
import com.ms.helloworld.dto.response.LoginResponse
import com.ms.helloworld.network.api.AuthApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApi
) {
    companion object {
        private const val TAG = "AuthRepository"
    }

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
}