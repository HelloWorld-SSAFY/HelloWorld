package com.ms.wearos.repository

import android.util.Log
import com.ms.wearos.network.api.WearApiService
import retrofit2.Retrofit
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

// API Response 모델
data class WearApiResponse(
    val success: Boolean,
    val message: String,
    val data: Any?
)

// 요청 데이터 모델들
data class HeartRateRequest(
    val heartRate: Double,
    val timestamp: Long = System.currentTimeMillis()
)

data class FetalMovementRequest(
    val timestamp: String,
    val recordedAt: Long = System.currentTimeMillis()
)

data class LaborDataRequest(
    val isActive: Boolean,
    val duration: String?,
    val interval: String?,
    val timestamp: Long = System.currentTimeMillis()
)

@Singleton
class WearApiRepository @Inject constructor(
    private val retrofit: Retrofit
) {
    companion object {
        private const val TAG = "WearRepository"
    }

    private val apiService = retrofit.create(WearApiService::class.java)

    // 사용자 데이터 조회
    suspend fun fetchUserData(authHeader: String): Response<WearApiResponse> {
        return try {
            Log.d(TAG, "Making API request for user data")
            apiService.getUserData(authHeader)
        } catch (e: Exception) {
            Log.e(TAG, "User data API request error", e)
            throw e
        }
    }

    // Wear 전용 데이터 조회
    suspend fun fetchWearData(authHeader: String): Response<WearApiResponse> {
        return try {
            Log.d(TAG, "Making API request for wear data")
            apiService.getWearSpecificData(authHeader)
        } catch (e: Exception) {
            Log.e(TAG, "Wear data API request error", e)
            throw e
        }
    }

    // 심박수 데이터 전송
    suspend fun sendHeartRateData(heartRate: Double, authHeader: String): Response<WearApiResponse> {
        return try {
            Log.d(TAG, "Sending heart rate data: $heartRate BPM")
            val request = HeartRateRequest(heartRate = heartRate)
            apiService.sendHeartRateData(authHeader, request)
        } catch (e: Exception) {
            Log.e(TAG, "Heart rate API request error", e)
            throw e
        }
    }

    // 태동 데이터 전송
    suspend fun sendFetalMovementData(timestamp: String, authHeader: String): Response<WearApiResponse> {
        return try {
            Log.d(TAG, "Sending fetal movement data: $timestamp")
            val request = FetalMovementRequest(timestamp = timestamp)
            apiService.sendFetalMovementData(authHeader, request)
        } catch (e: Exception) {
            Log.e(TAG, "Fetal movement API request error", e)
            throw e
        }
    }

    // 진통 데이터 전송
    suspend fun sendLaborData(
        isActive: Boolean,
        duration: String?,
        interval: String?,
        authHeader: String
    ): Response<WearApiResponse> {
        return try {
            Log.d(TAG, "Sending labor data - Active: $isActive, Duration: $duration, Interval: $interval")
            val request = LaborDataRequest(
                isActive = isActive,
                duration = duration,
                interval = interval
            )
            apiService.sendLaborData(authHeader, request)
        } catch (e: Exception) {
            Log.e(TAG, "Labor API request error", e)
            throw e
        }
    }
}