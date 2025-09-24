package com.ms.helloworld.network.api

import com.ms.helloworld.dto.response.WeeklyDietsResponse
import com.ms.helloworld.dto.response.WeeklyInfoResponse
import com.ms.helloworld.dto.response.WeeklyWorkoutsResponse
import retrofit2.http.*

interface WeeklyApi {

    @GET("/weekly/weekly/{weekNo}/info")
    suspend fun getWeeklyInfo(@Path("weekNo") weekNo: Int): WeeklyInfoResponse

    @GET("/weekly/weekly/{weekNo}/workouts")
    suspend fun getWeeklyWorkouts(@Path("weekNo") weekNo: Int): WeeklyWorkoutsResponse

    @GET("/weekly/weekly/{weekNo}/diets")
    suspend fun getWeeklyDiets(
        @Path("weekNo") weekNo: Int,
        @Query("from") from: Int = 1,
        @Query("to") to: Int = 7
    ): WeeklyDietsResponse
}