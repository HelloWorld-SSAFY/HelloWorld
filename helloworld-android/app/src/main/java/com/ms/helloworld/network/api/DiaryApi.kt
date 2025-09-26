package com.ms.helloworld.network.api

import com.ms.helloworld.dto.request.DiaryCreateRequest
import com.ms.helloworld.dto.request.DiaryCreateWithFilesRequest
import com.ms.helloworld.dto.request.DiaryUpdateRequest
import com.ms.helloworld.dto.response.DiaryResponse
import com.ms.helloworld.dto.response.DiaryListResponse
import com.ms.helloworld.dto.response.FileUploadResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface DiaryApi {

    @GET("calendar/diary")
    suspend fun getDiaries(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
        @Query("sort") sort: String = "createdAt,desc",
        @Query("coupleId") coupleId: Long? = null
    ): DiaryListResponse

    @GET("calendar/diary/{diaryId}")
    suspend fun getDiary(
        @Path("diaryId") diaryId: Long
    ): DiaryResponse

    @POST("calendar/diary")
    suspend fun createDiary(
        @Body request: DiaryCreateRequest
    ): DiaryResponse

    // 백엔드 API에 맞는 Multipart 요청
    @Multipart
    @POST("calendar/diary")
    suspend fun createDiaryWithFiles(
        @Part("payload") payload: RequestBody, // JSON 문자열
        @Part files: List<MultipartBody.Part>, // 파일들
        @Query("ultrasounds") ultrasounds: List<Boolean> // Query 파라미터로 전송
    ): Map<String, Any>

    @PUT("calendar/diary/{diaryId}")
    suspend fun updateDiary(
        @Path("diaryId") diaryId: Long,
        @Body request: DiaryUpdateRequest
    ): DiaryResponse

    @DELETE("calendar/diary/{diaryId}")
    suspend fun deleteDiary(
        @Path("diaryId") diaryId: Long
    ): Response<Unit>

    // 특정 주차의 일기 조회
    @GET("calendar/diary/week")
    suspend fun getWeeklyDiaries(
        @Query("week") week: Int,
        @Query("year") year: Int = 2024
    ): DiaryListResponse

    // 특정 날짜의 일기 조회
    @GET("calendar/diary/day")
    suspend fun getDiariesByDate(
        @Query("date") date: String // "yyyy-MM-dd" format
    ): DiaryListResponse

    // 주차별 일기 조회 (calendar/diary/week)
    @GET("calendar/diary/week")
    suspend fun getDiariesByWeek(
        @Query("week") week: Int,
        @Query("lmpDate") lmpDate: String
    ): DiaryListResponse

    // 일별 일기 조회 (calendar/diary/day)
    @GET("calendar/diary/day")
    suspend fun getDiariesByDay(
        @Query("day") day: Int,
        @Query("lmpDate") lmpDate: String
    ): DiaryListResponse
}