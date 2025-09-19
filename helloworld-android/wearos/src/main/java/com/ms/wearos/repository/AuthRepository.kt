package com.ms.wearos.repository

import android.util.Log
import com.ms.wearos.dto.response.UserInfoResponse
import com.ms.wearos.network.api.AuthApiService
import com.ms.wearos.util.WearTokenManager
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "싸피_AuthRepository"
@Singleton
class AuthRepository @Inject constructor(
    private val apiService: AuthApiService,
    private val tokenManager: WearTokenManager
) {
    suspend fun getUserInfo(): UserInfoResponse? {
        return try {
            val accessToken = tokenManager.getAccessToken()
            if (accessToken.isNullOrEmpty()) {
                Log.e(TAG, "No access token available")
                return null
            }

            val response = apiService.getUserInfo()

            if (response.isSuccessful) {
                val userInfo = response.body()
                Log.d(TAG, "User info retrieved successfully")
                Log.d(TAG, "Couple ID: ${userInfo?.couple?.couple_id}")
                userInfo
            } else {
                Log.e(TAG, "Failed to get user info: ${response.code()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user info", e)
            null
        }
    }
}