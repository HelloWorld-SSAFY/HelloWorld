package com.ms.helloworld.repository

import android.util.Log
import com.google.gson.JsonSyntaxException
import com.ms.helloworld.dto.request.GoogleLoginRequest
import com.ms.helloworld.dto.request.RefreshTokenRequest
import com.ms.helloworld.dto.response.LoginResponse
import com.ms.helloworld.dto.response.TokenRefreshResponse
import com.ms.helloworld.network.api.AuthApi
import retrofit2.HttpException
import java.io.EOFException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AuthRepository"

@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val fcmRepository: FcmRepository
) {

    suspend fun socialLogin(request: GoogleLoginRequest): LoginResponse? {
        return try {
            Log.d(TAG, "소셜 로그인 API 호출 시작")
            Log.d(TAG, "Provider: Google")
            Log.d(TAG, "Token length: ${request.idToken.length}")

            val response = authApi.socialLogin(request)

            if (response != null) {
                Log.d(TAG, "소셜 로그인 성공")
                Log.d(TAG, "MemberId: ${response.memberId}")
                Log.d(TAG, "AccessToken 존재: ${response.accessToken.isNotBlank()}")
                Log.d(TAG, "RefreshToken 존재: ${response.refreshToken.isNotBlank()}")

                // FCM 토큰 등록
                fcmRepository.registerTokenAsync(platform = "ANDROID")

                response
            } else {
                Log.e(TAG, "소셜 로그인 응답이 null")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "소셜 로그인 실패: ${e.javaClass.simpleName} - ${e.message}")
            logDetailedError(e)
            null
        }
    }

    suspend fun refreshToken(refreshToken: String): TokenRefreshResponse? {
        return try {
            Log.d(TAG, "토큰 갱신 API 호출 시작")
            Log.d(TAG, "RefreshToken 길이: ${refreshToken.length}")

            val response = authApi.refreshToken(RefreshTokenRequest(refreshToken))

            Log.d(TAG, "토큰 갱신 응답 상태: ${response.code()}")

            when {
                response.isSuccessful -> {
                    val body = response.body()
                    if (body != null) {
                        Log.d(TAG, "토큰 갱신 성공")
                        Log.d(TAG, "새 AccessToken 존재: ${body.accessToken.isNotBlank()}")
                        Log.d(TAG, "새 RefreshToken 존재: ${body.refreshToken?.isNotBlank() ?: false}")
                        body
                    } else {
                        Log.e(TAG, "토큰 갱신 응답 본문이 null")
                        null
                    }
                }
                response.code() == 401 -> {
                    Log.e(TAG, "토큰 만료 또는 유효하지 않은 토큰 (401)")
                    null
                }
                response.code() == 403 -> {
                    Log.e(TAG, "토큰 갱신 권한 없음 (403)")
                    null
                }
                else -> {
                    Log.e(TAG, "토큰 갱신 실패: HTTP ${response.code()}")
                    logErrorBody(response.errorBody()?.string())
                    null
                }
            }
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "토큰 갱신 타임아웃")
            null
        } catch (e: UnknownHostException) {
            Log.e(TAG, "토큰 갱신 네트워크 연결 실패")
            null
        } catch (e: EOFException) {
            Log.e(TAG, "토큰 갱신 서버 응답 없음 (EOF)")
            null
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "토큰 갱신 JSON 파싱 실패", e)
            null
        } catch (e: HttpException) {
            Log.e(TAG, "토큰 갱신 HTTP 오류: ${e.code()}")
            logErrorBody(e.response()?.errorBody()?.string())
            null
        } catch (e: Exception) {
            Log.e(TAG, "토큰 갱신 중 예상치 못한 오류: ${e.javaClass.simpleName} - ${e.message}")
            null
        }
    }

    suspend fun logout(): Boolean {
        return try {
            Log.d(TAG, "로그아웃 API 호출 시작")

            val response = authApi.logout()

            Log.d(TAG, "로그아웃 응답 상태: ${response.code()}")

            val isSuccessful = response.isSuccessful
            Log.d(TAG, "로그아웃 ${if (isSuccessful) "성공" else "실패"}")

            isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "로그아웃 실패: ${e.javaClass.simpleName} - ${e.message}")
            logDetailedError(e)
            false
        }
    }

    private fun logDetailedError(exception: Exception) {
        when (exception) {
            is HttpException -> {
                Log.e(TAG, "HTTP 오류 코드: ${exception.code()}")
                logErrorBody(exception.response()?.errorBody()?.string())
            }
            is SocketTimeoutException -> {
                Log.e(TAG, "요청 타임아웃")
            }
            is UnknownHostException -> {
                Log.e(TAG, "네트워크 연결 실패 - 인터넷 연결을 확인하세요")
            }
            else -> {
                Log.e(TAG, "기타 오류: ${exception.message}")
            }
        }
    }

    private fun logErrorBody(errorBody: String?) {
        if (!errorBody.isNullOrBlank()) {
            Log.e(TAG, "오류 응답 본문: $errorBody")
        }
    }
}