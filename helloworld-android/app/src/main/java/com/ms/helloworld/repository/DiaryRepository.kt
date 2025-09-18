package com.ms.helloworld.repository

import android.util.Log
import com.ms.helloworld.dto.request.DiaryCreateRequest
import com.ms.helloworld.dto.request.DiaryUpdateRequest
import com.ms.helloworld.dto.response.DiaryResponse
import com.ms.helloworld.dto.response.DiaryListResponse
import com.ms.helloworld.network.api.DiaryApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiaryRepository @Inject constructor(
    private val diaryApi: DiaryApi
) {

    companion object {
        private const val TAG = "DiaryRepository"
    }

    suspend fun getDiaries(page: Int = 0, size: Int = 20): Result<DiaryListResponse> {
        return try {
            Log.d(TAG, "📖 일기 목록 조회 - page: $page, size: $size")
            val response = diaryApi.getDiaries(page, size)
            Log.d(TAG, "✅ 일기 목록 조회 성공: ${response.content.size}개")
            Result.success(response)
        } catch (e: Exception) {
            Log.e(TAG, "❌ 일기 목록 조회 실패: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getDiary(diaryId: Long): Result<DiaryResponse> {
        return try {
            Log.d(TAG, "📖 일기 상세 조회 - diaryId: $diaryId")
            val response = diaryApi.getDiary(diaryId)
            Log.d(TAG, "✅ 일기 상세 조회 성공: ${response.diaryTitle}")
            Result.success(response)
        } catch (e: Exception) {
            Log.e(TAG, "❌ 일기 상세 조회 실패: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun createDiary(request: DiaryCreateRequest): Result<DiaryResponse> {
        return try {
            Log.d(TAG, "✍️ 일기 생성 - title: ${request.diaryTitle}")
            val response = diaryApi.createDiary(request)
            Log.d(TAG, "✅ 일기 생성 성공: ${response.diaryId}")
            Result.success(response)
        } catch (e: Exception) {
            Log.e(TAG, "❌ 일기 생성 실패: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun updateDiary(diaryId: Long, request: DiaryUpdateRequest): Result<DiaryResponse> {
        return try {
            Log.d(TAG, "📝 일기 수정 - diaryId: $diaryId, title: ${request.diaryTitle}")
            val response = diaryApi.updateDiary(diaryId, request)
            Log.d(TAG, "✅ 일기 수정 성공")
            Result.success(response)
        } catch (e: Exception) {
            Log.e(TAG, "❌ 일기 수정 실패: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun deleteDiary(diaryId: Long): Result<Unit> {
        return try {
            Log.d(TAG, "🗑️ 일기 삭제 - diaryId: $diaryId")
            val response = diaryApi.deleteDiary(diaryId)
            if (response.isSuccessful) {
                Log.d(TAG, "✅ 일기 삭제 성공")
                Result.success(Unit)
            } else {
                Log.e(TAG, "❌ 일기 삭제 실패 - HTTP ${response.code()}")
                Result.failure(Exception("HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 일기 삭제 실패: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getWeeklyDiaries(week: Int, year: Int = 2024): Result<DiaryListResponse> {
        return try {
            Log.d(TAG, "📅 주간 일기 조회 - ${year}년 ${week}주차")
            val response = diaryApi.getWeeklyDiaries(week, year)
            Log.d(TAG, "✅ 주간 일기 조회 성공: ${response.content.size}개")
            Result.success(response)
        } catch (e: Exception) {
            Log.e(TAG, "❌ 주간 일기 조회 실패: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getDiariesByDate(date: String): Result<DiaryListResponse> {
        return try {
            Log.d(TAG, "📆 날짜별 일기 조회 - date: $date")
            val response = diaryApi.getDiariesByDate(date)
            Log.d(TAG, "✅ 날짜별 일기 조회 성공: ${response.content.size}개")
            Result.success(response)
        } catch (e: Exception) {
            Log.e(TAG, "❌ 날짜별 일기 조회 실패: ${e.message}", e)
            Result.failure(e)
        }
    }
}