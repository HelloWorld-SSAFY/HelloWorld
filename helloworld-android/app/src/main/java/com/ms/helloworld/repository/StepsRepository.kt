package com.ms.helloworld.repository

import com.ms.helloworld.dto.request.StepsRequest
import com.ms.helloworld.network.api.HealthApi
import com.ms.helloworld.util.HealthConnectManager
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StepsRepository @Inject constructor(
    private val healthApiService: HealthApi,
    private val healthConnectManager: HealthConnectManager
) {
    suspend fun submitStepsData(latitude: Double, longitude: Double): Result<Unit> {
        return try {
            // 걸음수 데이터 읽기
            val stepRecords = healthConnectManager.readStepCounts()
            val totalSteps = stepRecords.sumOf { it.count.toInt() }

            val request = StepsRequest(
                date = Instant.now().toString(), // ISO 8601 format
                steps = totalSteps,
                latitude = latitude,
                longitude = longitude
            )

            val response = healthApiService.submitSteps(request)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("API Error: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}