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
            val contentSize = response.content?.size ?: 0
            Log.d(TAG, "✅ 일기 목록 조회 성공: ${contentSize}개")

            // 전체 일기 목록 상세 출력
            if (response.content != null && response.content.isNotEmpty()) {
                Log.d(TAG, "📋 전체 일기 목록:")
                response.content.forEachIndexed { index, diary ->
                    Log.d(TAG, "  [$index] ID: ${diary.diaryId}")
                    Log.d(TAG, "       제목: ${diary.diaryTitle}")
                    Log.d(TAG, "       역할: ${diary.authorRole}")
                    Log.d(TAG, "       targetDate: ${diary.targetDate}")
                    Log.d(TAG, "       coupleId: ${diary.coupleId}")
                    Log.d(TAG, "       authorId: ${diary.authorId}")
                }
            }

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
            Log.d(TAG, "✍️ 일기 생성 시작")
            Log.d(TAG, "📝 Request 정보:")
            Log.d(TAG, "  - entryDate: ${request.entryDate}")
            Log.d(TAG, "  - diaryTitle: ${request.diaryTitle}")
            Log.d(TAG, "  - diaryContent: ${request.diaryContent}")
            Log.d(TAG, "  - imageUrl: ${request.imageUrl}")
            Log.d(TAG, "  - coupleId: ${request.coupleId}")
            Log.d(TAG, "  - authorId: ${request.authorId}")
            Log.d(TAG, "  - authorRole: ${request.authorRole}")
            Log.d(TAG, "  - targetDate: ${request.targetDate}")
            Log.d(TAG, "🌐 API 호출: POST calendar/diary")

            val response = diaryApi.createDiary(request)

            Log.d(TAG, "✅ 일기 생성 성공!")
            Log.d(TAG, "📋 Response 정보:")
            Log.d(TAG, "  - diaryId: ${response.diaryId}")
            Log.d(TAG, "  - diaryTitle: ${response.diaryTitle}")
            Log.d(TAG, "  - authorRole: ${response.authorRole}")
            Log.d(TAG, "  - targetDate: ${response.targetDate}")

            Result.success(response)
        } catch (e: Exception) {
            Log.e(TAG, "❌ 일기 생성 실패")
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
            Log.e(TAG, "Exception message: ${e.message}")

            if (e is retrofit2.HttpException) {
                try {
                    val errorCode = e.code()
                    val errorBody = e.response()?.errorBody()?.string()
                    Log.e(TAG, "🚨 HTTP Error Details:")
                    Log.e(TAG, "  - Status Code: $errorCode")
                    Log.e(TAG, "  - Error Body: $errorBody")
                    Log.e(TAG, "  - Response Headers: ${e.response()?.headers()}")

                    // Request 정보도 다시 로그
                    Log.e(TAG, "🔄 Failed Request Details:")
                    Log.e(TAG, "  - diaryTitle: ${request.diaryTitle}")
                    Log.e(TAG, "  - diaryContent: ${request.diaryContent}")
                    Log.e(TAG, "  - targetDate: ${request.targetDate}")
                } catch (ioException: Exception) {
                    Log.e(TAG, "Failed to read error body: ${ioException.message}")
                }
            }

            Log.e(TAG, "Stack trace:", e)
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
            val contentSize = response.content?.size ?: 0
            Log.d(TAG, "✅ 주간 일기 조회 성공: ${contentSize}개")
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
            val contentSize = response.content?.size ?: 0
            Log.d(TAG, "✅ 날짜별 일기 조회 성공: ${contentSize}개")
            Result.success(response)
        } catch (e: Exception) {
            Log.e(TAG, "❌ 날짜별 일기 조회 실패: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getDiariesByWeek(coupleId: Long, week: Int, lmpDate: String): Result<DiaryListResponse> {
        return try {
            Log.d(TAG, "📅 주차별 일기 조회 - coupleId: $coupleId, week: $week, lmpDate: $lmpDate")
            val response = diaryApi.getDiariesByWeek(coupleId, week, lmpDate)
            val contentSize = response.content?.size ?: 0
            Log.d(TAG, "✅ 주차별 일기 조회 성공: ${contentSize}개 (content null: ${response.content == null})")
            Result.success(response)
        } catch (e: Exception) {
            Log.e(TAG, "❌ 주차별 일기 조회 실패: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getDiariesByDay(coupleId: Long, day: Int, lmpDate: String): Result<DiaryListResponse> {
        return try {
            Log.d(TAG, "📆 일별 일기 조회 시작")
            Log.d(TAG, "  - coupleId: $coupleId")
            Log.d(TAG, "  - day: $day")
            Log.d(TAG, "  - lmpDate: $lmpDate")
            Log.d(TAG, "🌐 API 호출: GET calendar/diary/day")
            Log.d(TAG, "🔗 Full URL: calendar/diary/day?coupleId=$coupleId&day=$day&lmpDate=$lmpDate")
            Log.d(TAG, "📋 Request parameters:")
            Log.d(TAG, "  - coupleId: $coupleId (type: ${coupleId.javaClass.simpleName})")
            Log.d(TAG, "  - day: $day (type: ${day.javaClass.simpleName})")
            Log.d(TAG, "  - lmpDate: '$lmpDate' (type: ${lmpDate.javaClass.simpleName})")

            val response = diaryApi.getDiariesByDay(coupleId, day, lmpDate)

            val contentSize = response.content?.size ?: 0
            Log.d(TAG, "✅ 일별 일기 조회 성공!")
            Log.d(TAG, "  - 조회된 일기 수: ${contentSize}개")
            Log.d(TAG, "  - content null 여부: ${response.content == null}")

            if (response.content != null && response.content.isNotEmpty()) {
                Log.d(TAG, "📋 조회된 일기 목록:")
                response.content.forEachIndexed { index, diary ->
                    Log.d(TAG, "  [$index] ID: ${diary.diaryId}, 제목: ${diary.diaryTitle}, 역할: ${diary.authorRole}, 날짜: ${diary.targetDate}")
                }
            } else {
                Log.d(TAG, "📋 해당 날짜에 등록된 일기가 없습니다")
            }

            Result.success(response)
        } catch (e: Exception) {
            Log.e(TAG, "❌ 일별 일기 조회 실패")
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
            Log.e(TAG, "Exception message: ${e.message}")

            if (e is retrofit2.HttpException) {
                try {
                    val errorCode = e.code()
                    val errorBody = e.response()?.errorBody()?.string()
                    Log.e(TAG, "🚨 HTTP Error Details:")
                    Log.e(TAG, "  - Status Code: $errorCode")
                    Log.e(TAG, "  - Error Body: $errorBody")
                } catch (ioException: Exception) {
                    Log.e(TAG, "Failed to read error body: ${ioException.message}")
                }
            }

            Log.e(TAG, "Stack trace:", e)
            Result.failure(e)
        }
    }
}