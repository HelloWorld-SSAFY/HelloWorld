package com.ms.wearos.repository

import android.util.Log
import com.ms.wearos.dto.request.FetalMovementRequest
import com.ms.wearos.dto.request.HealthDataRequest
import com.ms.wearos.dto.request.HealthRecordRequest
import com.ms.wearos.dto.request.HeartRateRequest
import com.ms.wearos.dto.request.LaborDataRequest
import com.ms.wearos.network.api.WearApiService
import retrofit2.Retrofit
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "싸피_WearApiRepository"

@Singleton
class WearApiRepository @Inject constructor(
    private val retrofit: Retrofit
) {

    private val apiService = retrofit.create(WearApiService::class.java)

    // 건강 데이터 전송 (심박수 + 스트레스 지수)
    suspend fun sendHealthData(coupleId: Int, healthDataRequest: HealthDataRequest): Response<Any> {
        return try {
            Log.d(TAG, "건강 데이터 전송: 심박수=${healthDataRequest.heartrate}, 스트레스=${healthDataRequest.stress}, 날짜=$healthDataRequest.date")

            apiService.sendHealthData(coupleId,healthDataRequest)
        } catch (e: Exception) {
            Log.e(TAG, "건강 데이터 API 오류", e)
            throw e
        }
    }

    // 태동 기록 전송
    suspend fun sendFetalMovement(coupleId: Int, fetalMovementRequest: FetalMovementRequest): Response<Any> {
        return try {
            Log.d(TAG, "태동 기록 전송: 커플ID=$coupleId, 기록시간=${fetalMovementRequest.recordedAt}")

            apiService.sendFetalMovement(coupleId, fetalMovementRequest)
        } catch (e: Exception) {
            Log.e(TAG, "태동 기록 API 오류", e)
            throw e
        }
    }

    suspend fun sendLaborData(coupleId: Int, laborDataRequest: LaborDataRequest): Response<Any> {
        return try {
            Log.d(TAG, "진통 기록 전송: 커플ID=$coupleId, 시작=${laborDataRequest.startTime}, 종료=${laborDataRequest.endTime}")

            apiService.sendLaborData(coupleId, laborDataRequest)
        } catch (e: Exception) {
            Log.e(TAG, "진통 기록 API 오류", e)
            throw e
        }
    }

}