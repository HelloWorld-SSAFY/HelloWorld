package com.ms.helloworld.repository

import android.util.Log
import com.ms.helloworld.dto.response.CaricatureResponse
import com.ms.helloworld.network.api.CaricatureApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CaricatureRepository @Inject constructor(
    private val caricatureApi: CaricatureApi
) {

    suspend fun getCaricatureFromPhoto(diaryPhotoId: Long): Result<CaricatureResponse?> {
        return try {
            Log.d("CaricatureRepository", "캐리커쳐 조회 API 호출: diaryPhotoId=$diaryPhotoId")
            val response = caricatureApi.getCaricatureFromPhoto(diaryPhotoId)

            if (response.isSuccessful) {
                // 응답 body가 비어있는지 먼저 확인 (response.body() 호출 전에)
                val contentLength = response.raw().body?.contentLength() ?: -1
                Log.d("CaricatureRepository", "응답 content-length: $contentLength")

                if (contentLength == 0L) {
                    // 빈 응답은 캐리커쳐가 없음을 의미
                    Log.d("CaricatureRepository", "캐리커쳐가 없음 (빈 응답)")
                    Result.success(null)
                } else {
                    val caricature = response.body()
                    Log.d("CaricatureRepository", "캐리커쳐 조회 성공: $caricature")
                    Result.success(caricature)
                }
            } else if (response.code() == 404) {
                // 404는 캐리커쳐가 없는 경우이므로 정상적인 상황
                Log.d("CaricatureRepository", "캐리커쳐가 없음 (404)")
                Result.success(null)
            } else {
                Log.e("CaricatureRepository", "캐리커쳐 조회 실패: ${response.code()} ${response.message()}")
                Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: java.io.EOFException) {
            // Gson 빈 응답 파싱 에러는 정상 상황으로 처리 (캐리커쳐가 없음)
            Log.d("CaricatureRepository", "캐리커쳐가 없음 (빈 JSON 응답)")
            Result.success(null)
        } catch (e: Exception) {
            Log.e("CaricatureRepository", "캐리커쳐 조회 예외: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun generateCaricature(diaryPhotoId: Long): Result<CaricatureResponse> {
        return try {
            Log.d("CaricatureRepository", "캐리커쳐 생성 API 호출: diaryPhotoId=$diaryPhotoId")
            val response = caricatureApi.generateCaricature(diaryPhotoId)

            if (response.isSuccessful) {
                val result = response.body()
                if (result != null) {
                    Log.d("CaricatureRepository", "캐리커쳐 생성 성공: $result")
                    Result.success(result)
                } else {
                    Log.e("CaricatureRepository", "캐리커쳐 생성 응답 본문이 null")
                    Result.failure(Exception("응답 본문이 null입니다"))
                }
            } else {
                Log.e("CaricatureRepository", "캐리커쳐 생성 실패: ${response.code()} ${response.message()}")
                Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e("CaricatureRepository", "캐리커쳐 생성 예외: ${e.message}", e)
            Result.failure(e)
        }
    }
}