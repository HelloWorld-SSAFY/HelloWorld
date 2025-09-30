package com.ms.helloworld.repository

import com.ms.helloworld.dto.response.WeeklyDietsResponse
import com.ms.helloworld.dto.response.WeeklyInfoResponse
import com.ms.helloworld.dto.response.WeeklyWorkoutsResponse
import com.ms.helloworld.network.api.WeeklyApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeeklyRepository @Inject constructor(
    private val weeklyApi: WeeklyApi
) {

    suspend fun getWeeklyInfo(weekNo: Int): Result<WeeklyInfoResponse> {
        return try {
            val response = weeklyApi.getWeeklyInfo(weekNo)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getWeeklyWorkouts(weekNo: Int): Result<WeeklyWorkoutsResponse> {
        return try {
            val response = weeklyApi.getWeeklyWorkouts(weekNo)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getWeeklyDiets(weekNo: Int, from: Int = 1, to: Int = 7): Result<WeeklyDietsResponse> {
        return try {
            val response = weeklyApi.getWeeklyDiets(weekNo, from, to)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}