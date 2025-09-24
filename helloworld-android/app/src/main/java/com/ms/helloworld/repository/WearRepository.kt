package com.ms.helloworld.repository

import com.ms.helloworld.dto.response.ContractionsResponse
import com.ms.helloworld.dto.response.FetalMovementResponse
import com.ms.helloworld.network.api.HealthApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WearRepository @Inject constructor(
    private val apiService: HealthApi
) {
    suspend fun getContractions(
        from: String? = null,
        to: String? = null
    ): Result<ContractionsResponse> {
        return try {
            val response = apiService.getContractions(from, to)
            if (response.isSuccessful) {
                response.body()?.let { contractionsResponse ->
                    Result.success(contractionsResponse)
                } ?: Result.failure(Exception("Empty response body"))
            } else {
                Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getFetalMovement(
        from: String? = null,
        to: String? = null
    ): Result<FetalMovementResponse> {
        return try {
            val response = apiService.getFetalMovement(from, to)
            if (response.isSuccessful) {
                response.body()?.let { fetalMovementResponse ->
                    Result.success(fetalMovementResponse)
                } ?: Result.failure(Exception("Empty response body"))
            } else {
                Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}