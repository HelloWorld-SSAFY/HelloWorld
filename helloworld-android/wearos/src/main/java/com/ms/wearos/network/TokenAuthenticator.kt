package com.ms.wearos.network

import android.util.Log
import com.ms.wearos.network.api.AuthApi
import com.ms.wearos.util.TokenManager
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

// 토큰 갱신 요청 데이터 클래스
data class RefreshTokenRequest(
    val refreshToken: String
)

private const val TAG = "싸피_TokenAuthenticator"
@Singleton
class TokenAuthenticator @Inject constructor(
    private val tokenManager: TokenManager,
    private val authApiProvider: Provider<AuthApi>
) : Authenticator {

    private val refreshMutex = Mutex()

    override fun authenticate(route: Route?, response: Response): Request? {
        Log.d(TAG, "401 에러 감지 - 토큰 갱신 시도")

        return runBlocking {
            refreshMutex.withLock {
                val refreshToken = tokenManager.getRefreshToken()

                if (refreshToken.isNullOrBlank()) {
                    Log.e(TAG, "RefreshToken이 없음 - 로그아웃 필요")
                    return@withLock null
                }

                try {
                    Log.d(TAG, "RefreshToken으로 토큰 갱신 중...")

                    val refreshResponse = authApiProvider.get().refreshToken(
                        RefreshTokenRequest(refreshToken)
                    )

                    // 새 토큰 저장
                    tokenManager.saveTokens(
                        refreshResponse.accessToken,
                        refreshResponse.refreshToken ?: refreshToken // 새 리프레시 토큰이 없으면 기존 것 유지
                    )

                    Log.d(TAG, "토큰 갱신 성공")

                    // 실패한 요청을 새 토큰으로 재시도
                    response.request.newBuilder()
                        .header("Authorization", "Bearer ${refreshResponse.accessToken}")
                        .build()

                } catch (e: Exception) {
                    Log.e(TAG, "토큰 갱신 실패: ${e.message}")

                    // 갱신 실패 시 토큰 삭제 (로그아웃 처리)
                    try {
                        tokenManager.clearTokens()
                        Log.d(TAG, "🗑토큰 삭제됨 - 재로그인 필요")
                    } catch (clearException: Exception) {
                        Log.e(TAG, "토큰 삭제 실패", clearException)
                    }

                    null
                }
            }
        }
    }
}