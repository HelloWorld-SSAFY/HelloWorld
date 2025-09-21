package com.ms.helloworld.repository

import android.util.Log
import com.ms.helloworld.dto.request.CoupleInviteRequest
import com.ms.helloworld.dto.response.CoupleInviteCodeResponse
import com.ms.helloworld.dto.response.MemberRegisterResponse
import com.ms.helloworld.network.api.CoupleApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoupleRepository @Inject constructor(
    private val coupleApi: CoupleApi
) {

    companion object {
        private const val TAG = "CoupleRepository"
    }

    suspend fun generateInviteCode(): Result<CoupleInviteCodeResponse> {
        return try {
            Log.d(TAG, "🎫 초대 코드 생성 요청")
            val response = coupleApi.generateInviteCode()
            Log.d(TAG, "✅ 초대 코드 생성 성공: ${response.code}")
            Result.success(response)
        } catch (e: Exception) {
            Log.e(TAG, "❌ 초대 코드 생성 실패: ${e.message}", e)
            if (e is retrofit2.HttpException) {
                try {
                    val errorBody = e.response()?.errorBody()?.string()
                    Log.e(TAG, "HTTP Error Code: ${e.code()}")
                    Log.e(TAG, "HTTP Error Body: $errorBody")
                } catch (ioException: Exception) {
                    Log.e(TAG, "Failed to read error body: ${ioException.message}")
                }
            }
            Result.failure(e)
        }
    }

    suspend fun acceptInvite(code: String): Result<CoupleInviteCodeResponse> {
        return try {
            Log.d(TAG, "🤝 초대 코드 수락 시작")
            Log.d(TAG, "  - 입력된 코드: '$code'")
            Log.d(TAG, "  - 코드 길이: ${code.length}")
            Log.d(TAG, "  - 코드가 빈 문자열인가: ${code.isEmpty()}")
            Log.d(TAG, "  - 코드가 공백인가: ${code.isBlank()}")

            val request = CoupleInviteRequest(code = code)
            Log.d(TAG, "📤 CoupleInviteRequest 생성: $request")
            Log.d(TAG, "🌐 API 호출: POST user/api/couples/join")

            val response = coupleApi.joinCouple(request)
            Log.d(TAG, "✅ 초대 코드 수락 성공: $response")
            Result.success(response)
        } catch (e: Exception) {
            Log.e(TAG, "❌ 초대 코드 수락 실패: ${e.message}", e)
            if (e is retrofit2.HttpException) {
                try {
                    val errorBody = e.response()?.errorBody()?.string()
                    Log.e(TAG, "HTTP Error Code: ${e.code()}")
                    Log.e(TAG, "HTTP Error Body: $errorBody")
                } catch (ioException: Exception) {
                    Log.e(TAG, "Failed to read error body: ${ioException.message}")
                }
            }
            Result.failure(e)
        }
    }



    suspend fun disconnectCouple(): Result<MemberRegisterResponse> {
        return try {
            Log.d(TAG, "💔 커플 연결 해제")
            val response = coupleApi.disconnectCouple()
            Log.d(TAG, "✅ 커플 연결 해제 성공")
            Result.success(response)
        } catch (e: Exception) {
            Log.e(TAG, "❌ 커플 연결 해제 실패: ${e.message}", e)
            if (e is retrofit2.HttpException) {
                try {
                    val errorBody = e.response()?.errorBody()?.string()
                    Log.e(TAG, "HTTP Error Code: ${e.code()}")
                    Log.e(TAG, "HTTP Error Body: $errorBody")
                } catch (ioException: Exception) {
                    Log.e(TAG, "Failed to read error body: ${ioException.message}")
                }
            }
            Result.failure(e)
        }
    }
}