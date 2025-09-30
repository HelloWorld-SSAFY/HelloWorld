package com.ms.helloworld.repository

import com.ms.helloworld.dto.request.MaternalHealthCreateRequest
import com.ms.helloworld.dto.request.MaternalHealthUpdateRequest
import com.ms.helloworld.dto.response.MaternalHealthGetResponse
import com.ms.helloworld.dto.response.MaternalHealthUpdateResponse
import com.ms.helloworld.dto.response.MaternalHealthListResponse
import com.ms.helloworld.network.api.HealthApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MaternalHealthRepository @Inject constructor(
    private val healthApi: HealthApi
) {
    suspend fun getMaternalHealthById(maternalId: Long): Result<MaternalHealthGetResponse> {
        return try {
            val response = healthApi.getMaternalHealthById(maternalId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to get maternal health data: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createMaternalHealth(request: MaternalHealthCreateRequest): Result<Unit> {
        return try {
            val response = healthApi.createMaternalHealth(request)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to create maternal health data: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateMaternalHealth(
        maternalId: Long,
        request: MaternalHealthUpdateRequest
    ): Result<MaternalHealthUpdateResponse> {
        return try {
            val response = healthApi.updateMaternalHealth(maternalId, request)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to update maternal health data: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteMaternalHealth(maternalId: Long): Result<Unit> {
        return try {
            val response = healthApi.deleteMaternalHealth(maternalId)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to delete maternal health data: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMaternalHealthList(
        from: String? = null,
        to: String? = null
    ): Result<MaternalHealthListResponse> {
        return try {
            val response = healthApi.getMaternalHealthList(from, to)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to get maternal health list: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTodayMaternalHealth(): Result<MaternalHealthGetResponse?> {
        return try {
            val today = java.time.LocalDate.now().toString()
            val listResult = getMaternalHealthList(from = today, to = today)

            if (listResult.isSuccess) {
                val records = listResult.getOrNull()?.records
                if (records != null && records.isNotEmpty()) {
                    val todayRecord = records.first()
                    val healthData = MaternalHealthGetResponse(
                        recordDate = todayRecord.recordDate,
                        weight = todayRecord.weight,
                        bloodPressure = todayRecord.bloodPressure,
                        bloodSugar = todayRecord.bloodSugar
                    )
                    Result.success(healthData)
                } else {
                    Result.success(null)
                }
            } else {
                Result.failure(listResult.exceptionOrNull() ?: Exception("Failed to get today's health data"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}