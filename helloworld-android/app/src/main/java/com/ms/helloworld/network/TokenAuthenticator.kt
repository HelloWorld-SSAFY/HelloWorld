package com.ms.helloworld.network

import android.util.Log
import com.ms.helloworld.dto.request.RefreshTokenRequest
import com.ms.helloworld.network.api.AuthApi
import com.ms.helloworld.util.TokenManager
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

private const val TAG = "싸피_TokenAuthenticator"
@Singleton
class TokenAuthenticator @Inject constructor(
    private val tokenManager: TokenManager,
    private val authApiProvider: Provider<AuthApi>
) : Authenticator {

    private val refreshMutex = Mutex()

    override fun authenticate(route: Route?, response: Response): Request? {

        return runBlocking {
            refreshMutex.withLock {
                val refreshToken = tokenManager.getRefreshToken()

                if (refreshToken.isNullOrBlank()) {
                    Log.e(TAG, "RefreshToken 없음")
                    return@withLock null
                }

                try {

                    val refreshResponse = authApiProvider.get().refreshToken(
                        RefreshTokenRequest(refreshToken)
                    )

                    // Response가 성공적인지 확인
                    if (refreshResponse.isSuccessful) {
                        val tokenResponse = refreshResponse.body()

                        if (tokenResponse != null) {
                            // 새 토큰 저장
                            tokenManager.saveTokens(
                                tokenResponse.accessToken,
                                tokenResponse.refreshToken ?: refreshToken // 새 리프레시 토큰이 없으면 기존 것 유지
                            )

                            Log.d(TAG, "토큰 갱신 성공")
                            Log.d(TAG, "New Access Token: ${tokenResponse.accessToken}")
                            Log.d(TAG, "New Refresh Token: ${tokenResponse.refreshToken ?: refreshToken}")

                            // 실패한 요청을 새 토큰으로 재시도
                            response.request.newBuilder()
                                .header("Authorization", "Bearer ${tokenResponse.accessToken}")
                                .build()
                        } else {
                            Log.e(TAG, "토큰 갱신 응답 본문이 null")
                            tokenManager.clearTokens()
                            Log.d(TAG, "🗑토큰 삭제됨 - 재로그인 필요")
                            null
                        }
                    } else {
                        Log.e(TAG, "토큰 갱신 실패: ${refreshResponse.code()}")
                        tokenManager.clearTokens()
                        Log.d(TAG, "🗑토큰 삭제됨 - 재로그인 필요")
                        null
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "토큰 갱신 실패: ${e.message}")

                    // 갱신 실패 시 토큰 삭제 (로그아웃 처리)
                    try {
                        tokenManager.clearTokens()
                        Log.d(TAG, "토큰 삭제됨 - 재로그인 필요")
                    } catch (clearException: Exception) {
                        Log.e(TAG, "토큰 삭제 실패", clearException)
                    }

                    null
                }
            }
        }
    }
}