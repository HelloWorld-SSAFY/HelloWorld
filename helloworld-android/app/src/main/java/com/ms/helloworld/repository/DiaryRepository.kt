package com.ms.helloworld.repository

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.google.gson.Gson
import com.ms.helloworld.dto.request.DiaryCreateRequest
import com.ms.helloworld.dto.request.DiaryCreateWithFilesRequest
import com.ms.helloworld.dto.request.DiaryUpdateRequest
import com.ms.helloworld.dto.response.DiaryResponse
import com.ms.helloworld.dto.response.DiaryListResponse
import com.ms.helloworld.network.api.DiaryApi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
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
                    Log.d(TAG, "       원본 targetDate: ${diary.targetDate}")
                    Log.d(TAG, "       보정된 targetDate: ${diary.getCorrectedTargetDate()}")
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

    suspend fun createDiaryWithFiles(
        context: Context,
        entryDate: String,
        diaryTitle: String,
        diaryContent: String,
        targetDate: String,
        authorRole: String,
        authorId: Long,
        imageUris: List<Uri>,
        ultrasounds: List<Boolean>
    ): Result<DiaryResponse> {
        return try {
            Log.d(TAG, "📎 백엔드 API 맞춤 일기 생성 시작")
            Log.d(TAG, "📝 Parameters:")
            Log.d(TAG, "  - entryDate: $entryDate")
            Log.d(TAG, "  - diaryTitle: $diaryTitle")
            Log.d(TAG, "  - targetDate: $targetDate")
            Log.d(TAG, "  - authorRole: $authorRole")
            Log.d(TAG, "  - authorId: $authorId")
            Log.d(TAG, "  - imageUris count: ${imageUris.size}")
            Log.d(TAG, "  - ultrasounds: $ultrasounds")

            // 1. JSON payload 생성
            val now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            val requestData = mapOf(
                "entryDate" to entryDate,
                "diaryTitle" to diaryTitle,
                "diaryContent" to diaryContent,
                "imageUrl" to "", // 빈 문자열로 설정
                "coupleId" to 0L, // TODO: 실제 coupleId 필요
                "authorId" to authorId,
                "authorRole" to authorRole,
                "targetDate" to targetDate,
                "createdAt" to now,
                "updatedAt" to now
            )

            val jsonPayload = Gson().toJson(requestData)
            val payloadBody = jsonPayload.toRequestBody("application/json".toMediaType())

            Log.d(TAG, "📋 JSON Payload: $jsonPayload")

            // 2. 파일 Parts 생성
            val fileParts = mutableListOf<MultipartBody.Part>()
            imageUris.forEachIndexed { index, uri ->
                try {
                    val file = getFileFromUri(context, uri)
                    if (file != null && file.exists()) {
                        val requestFile = file.asRequestBody("image/*".toMediaType())
                        val part = MultipartBody.Part.createFormData("files", file.name, requestFile)
                        fileParts.add(part)
                        Log.d(TAG, "📎 파일[$index] 추가: ${file.name}, 크기: ${file.length()} bytes")
                    } else {
                        Log.w(TAG, "⚠️ 파일[$index] 생성 실패: URI=$uri")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 파일[$index] 처리 실패: ${e.message}", e)
                }
            }

            Log.d(TAG, "🌐 API 호출: POST calendar/diary (Multipart with payload)")
            val response = diaryApi.createDiaryWithFiles(
                payload = payloadBody,
                files = fileParts,
                ultrasounds = ultrasounds
            )

            Log.d(TAG, "✅ 일기 생성 성공!")
            Log.d(TAG, "📋 Response: $response")

            // Map에서 diaryId 추출하여 DiaryResponse 생성
            val diaryId = (response["diaryId"] as? Number)?.toLong() ?: 0L
            val mockDiaryResponse = DiaryResponse(
                diaryId = diaryId,
                coupleId = 0L,
                authorId = authorId,
                authorRole = authorRole,
                diaryTitle = diaryTitle,
                diaryTitleAlt = null,
                diaryContent = diaryContent,
                diaryContentAlt = null,
                thumbnailUrl = "",
                entryDate = entryDate,
                targetDate = targetDate,
                createdAt = now,
                updatedAt = now
            )

            Result.success(mockDiaryResponse)
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
                } catch (ioException: Exception) {
                    Log.e(TAG, "Failed to read error body: ${ioException.message}")
                }
            }

            Log.e(TAG, "Stack trace:", e)
            Result.failure(e)
        }
    }

    private fun getFileFromUri(context: Context, uri: Uri): File? {
        return try {
            val contentResolver = context.contentResolver
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val displayNameIndex = it.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                    val displayName = if (displayNameIndex >= 0) {
                        it.getString(displayNameIndex)
                    } else {
                        "image_${System.currentTimeMillis()}.jpg"
                    }

                    // 임시 파일 생성
                    val tempFile = File(context.cacheDir, displayName)
                    contentResolver.openInputStream(uri)?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    return@use tempFile // 명시적으로 반환값 지정
                } else {
                    null
                }
            } ?: run {
                Log.w(TAG, "Cursor is null for URI: $uri")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create file from URI: $uri", e)
            null
        }
    }

    suspend fun updateDiary(diaryId: Long, request: DiaryUpdateRequest): Result<DiaryResponse> {
        return try {
            Log.d(TAG, "📝 일기 수정 시작")
            Log.d(TAG, "📋 Request 정보:")
            Log.d(TAG, "  - diaryId: $diaryId")
            Log.d(TAG, "  - entryDate: ${request.entryDate}")
            Log.d(TAG, "  - diaryTitle: ${request.diaryTitle}")
            Log.d(TAG, "  - diaryContent: ${request.diaryContent}")
            Log.d(TAG, "  - imageUrl: ${request.imageUrl}")
            Log.d(TAG, "🌐 API 호출: PUT calendar/diary/$diaryId")

            val response = diaryApi.updateDiary(diaryId, request)

            Log.d(TAG, "✅ 일기 수정 성공!")
            Log.d(TAG, "📋 Response 정보:")
            Log.d(TAG, "  - diaryId: ${response.diaryId}")
            Log.d(TAG, "  - diaryTitle: ${response.diaryTitle}")
            Log.d(TAG, "  - authorRole: ${response.authorRole}")
            Log.d(TAG, "  - targetDate: ${response.targetDate}")

            Result.success(response)
        } catch (e: Exception) {
            Log.e(TAG, "❌ 일기 수정 실패")
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
                    Log.e(TAG, "  - diaryId: $diaryId")
                    Log.e(TAG, "  - diaryTitle: ${request.diaryTitle}")
                    Log.e(TAG, "  - diaryContent: ${request.diaryContent}")
                    Log.e(TAG, "  - entryDate: ${request.entryDate}")
                    Log.e(TAG, "  - imageUrl: ${request.imageUrl}")
                } catch (ioException: Exception) {
                    Log.e(TAG, "Failed to read error body: ${ioException.message}")
                }
            }

            Log.e(TAG, "Stack trace:", e)
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

    suspend fun getDiariesByWeek(week: Int, lmpDate: String): Result<DiaryListResponse> {
        return try {
            Log.d(TAG, "📅 주차별 일기 조회 - week: $week, lmpDate: $lmpDate")
            val response = diaryApi.getDiariesByWeek(week, lmpDate)

            // items 우선 사용, content는 fallback
            val actualContent = response.items ?: response.content
            val contentSize = actualContent?.size ?: 0
            Log.d(TAG, "✅ 주차별 일기 조회 성공: ${contentSize}개")
            Log.d(TAG, "  - items null 여부: ${response.items == null}")
            Log.d(TAG, "  - content null 여부: ${response.content == null}")

            if (actualContent != null && actualContent.isNotEmpty()) {
                Log.d(TAG, "📋 조회된 주간 일기 목록:")
                actualContent.forEachIndexed { index, diary ->
                    Log.d(TAG, "  [$index] ID: ${diary.diaryId}, 제목: ${diary.diaryTitle}, 날짜: ${diary.targetDate}")
                }
            } else {
                Log.d(TAG, "📋 해당 주차에 등록된 일기가 없습니다")
            }

            // items를 content로 치환한 새로운 response 생성
            val correctedResponse = response.copy(content = actualContent)
            Result.success(correctedResponse)
        } catch (e: Exception) {
            Log.e(TAG, "❌ 주차별 일기 조회 실패: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getDiariesByDay(day: Int, lmpDate: String): Result<DiaryListResponse> {
        return try {
            Log.d(TAG, "📆 일별 일기 조회 시작")
            Log.d(TAG, "  - day: $day")
            Log.d(TAG, "  - lmpDate: $lmpDate")
            Log.d(TAG, "🌐 API 호출: GET calendar/diary/day")
            Log.d(TAG, "📋 Request parameters:")
            Log.d(TAG, "  - day: $day (type: ${day.javaClass.simpleName})")
            Log.d(TAG, "  - lmpDate: '$lmpDate' (type: ${lmpDate.javaClass.simpleName})")

            val response = diaryApi.getDiariesByDay(day, lmpDate)

            // items 우선 사용, content는 fallback
            val actualContent = response.items ?: response.content
            val contentSize = actualContent?.size ?: 0
            Log.d(TAG, "✅ 일별 일기 조회 성공!")
            Log.d(TAG, "  - 조회된 일기 수: ${contentSize}개")
            Log.d(TAG, "  - items null 여부: ${response.items == null}")
            Log.d(TAG, "  - content null 여부: ${response.content == null}")

            if (actualContent != null && actualContent.isNotEmpty()) {
                Log.d(TAG, "📋 조회된 일기 목록:")
                actualContent.forEachIndexed { index, diary ->
                    Log.d(TAG, "  [$index] ID: ${diary.diaryId}, 제목: ${diary.diaryTitle}, 역할: ${diary.authorRole}")
                    Log.d(TAG, "       원본 날짜: ${diary.targetDate}")
                    Log.d(TAG, "       보정된 날짜: ${diary.getCorrectedTargetDate()}")
                }
            } else {
                Log.d(TAG, "📋 해당 날짜에 등록된 일기가 없습니다")
            }

            // items를 content로 치환한 새로운 response 생성
            val correctedResponse = response.copy(content = actualContent)
            Result.success(correctedResponse)
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