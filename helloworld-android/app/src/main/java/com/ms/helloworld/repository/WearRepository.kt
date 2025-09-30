package com.ms.helloworld.repository

import android.util.Log
import com.ms.helloworld.dto.response.ContractionsResponse
import com.ms.helloworld.dto.response.FetalMovementResponse
import com.ms.helloworld.dto.response.WearableResponse
import com.ms.helloworld.network.api.HealthApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WearRepository @Inject constructor(
    private val apiService: HealthApi
) {
    companion object {
        private const val TAG = "WearRepository"
    }

    suspend fun getContractions(
        from: String? = null,
        to: String? = null
    ): Result<ContractionsResponse> {
        Log.d(TAG, "=== getContractions 호출 시작 ===")
        Log.d(TAG, "요청 파라미터 - from: $from, to: $to")

        return try {
            val response = apiService.getContractions(from, to)
            Log.d(TAG, "API 응답 코드: ${response.code()}")

            if (response.isSuccessful) {
                response.body()?.let { contractionsResponse ->
                    Log.d(TAG, "진통 세션 개수: ${contractionsResponse.sessions.size}")

                    contractionsResponse.sessions.forEachIndexed { index, session ->
                        Log.d(TAG, "시작 시간: ${session.startTime}")
                        Log.d(TAG, "종료 시간: ${session.endTime}")
                        Log.d(TAG, "지속 시간(초): ${session.durationSec}")
                        Log.d(TAG, "간격(분): ${session.intervalMin}")
                    }

                    Result.success(contractionsResponse)
                } ?: run {
                    Log.e(TAG, "응답 body가 null입니다")
                    Result.failure(Exception("Empty response body"))
                }
            } else {
                Log.e(TAG, "API 호출 실패 - HTTP ${response.code()}: ${response.message()}")
                Log.e(TAG, "에러 body: ${response.errorBody()?.string()}")
                Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getContractions 예외 발생", e)
            Log.e(TAG, "예외 메시지: ${e.message}")
            Log.e(TAG, "예외 타입: ${e.javaClass.simpleName}")
            Result.failure(e)
        } finally {
            Log.d(TAG, "=== getContractions 호출 종료 ===")
        }
    }

    suspend fun getFetalMovement(
        from: String? = null,
        to: String? = null
    ): Result<FetalMovementResponse> {
        Log.d(TAG, "=== getFetalMovement 호출 ===")
        Log.d(TAG, "요청 파라미터 - from: $from, to: $to")

        return try {
            val response = apiService.getFetalMovement(from, to)
            Log.d(TAG, "API 응답 코드: ${response.code()}")

            if (response.isSuccessful) {
                response.body()?.let { fetalMovementResponse ->
                    Log.d(TAG, "태동 기록 개수: ${fetalMovementResponse.records.size}")

                    fetalMovementResponse.records.forEachIndexed { index, record ->
                        Log.d(TAG, "--- 태동 기록 [$index] ---")
                        Log.d(TAG, "기록 시간: ${record.recordedAt}")
                        Log.d(TAG, "총 태동 횟수: ${record.totalCount}")
                    }

                    // 일별 통계 로그
                    val groupedByDate = fetalMovementResponse.records.groupBy {
                        try {
                            it.recordedAt.substring(0, 10) // YYYY-MM-DD 부분만 추출
                        } catch (e: Exception) {
                            "Unknown"
                        }
                    }

                    Log.d(TAG, "--- 일별 태동 통계 ---")
                    groupedByDate.forEach { (date, records) ->
                        val totalCount = records.sumOf { it.totalCount }
                        Log.d(TAG, "$date: ${records.size}개 기록, 총 태동 횟수: $totalCount")
                    }

                    val overallAverage = if (fetalMovementResponse.records.isNotEmpty()) {
                        fetalMovementResponse.records.sumOf { it.totalCount }.toDouble() / fetalMovementResponse.records.size
                    } else {
                        0.0
                    }
                    Log.d(TAG, "전체 평균 태동 횟수: $overallAverage")

                    Result.success(fetalMovementResponse)
                } ?: run {
                    Log.e(TAG, "응답 body가 null입니다")
                    Result.failure(Exception("Empty response body"))
                }
            } else {
                Log.e(TAG, "API 호출 실패 - HTTP ${response.code()}: ${response.message()}")
                Log.e(TAG, "에러 body: ${response.errorBody()?.string()}")
                Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getFetalMovement 예외 발생", e)
            Log.e(TAG, "예외 메시지: ${e.message}")
            Log.e(TAG, "예외 타입: ${e.javaClass.simpleName}")
            Result.failure(e)
        } finally {
            Log.d(TAG, "=== getFetalMovement 호출 종료 ===")
        }
    }

    suspend fun getLatestData(): Result<WearableResponse> {
        return try {
            val response = apiService.getLatestWearableData()
            if (response.isSuccessful) {
                response.body()?.let { data ->
                    Result.success(data)
                } ?: Result.failure(Exception("Response body is null"))
            } else {
                Result.failure(Exception("API call failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
