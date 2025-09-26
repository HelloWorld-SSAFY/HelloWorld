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
        // 401 Unauthorized가 아니면 처리하지 않음
        if (response.code != 401) {
            return null
        }

        return runBlocking {
            refreshMutex.withLock {
                // 이미 Authorization 헤더가 갱신된 요청인지 확인
                val currentToken = tokenManager.getAccessTokenSuspend()
                val requestToken = response.request.header("Authorization")?.removePrefix("Bearer ")

                // 현재 저장된 토큰과 요청에 사용된 토큰이 다르다면 이미 갱신됨
                if (currentToken != null && currentToken != requestToken) {
                    Log.d(TAG, "토큰이 이미 갱신됨. 새 토큰으로 재시도")
                    return@withLock response.request.newBuilder()
                        .header("Authorization", "Bearer $currentToken")
                        .build()
                }

                val refreshToken = tokenManager.getRefreshTokenSuspend()

                if (refreshToken.isNullOrBlank()) {
                    Log.e(TAG, "RefreshToken 없음 - 로그아웃 처리")
                    tokenManager.clearTokens()
                    return@withLock null
                }

                // RefreshToken도 만료되었는지 확인
                if (tokenManager.isTokenExpired(refreshToken)) {
                    Log.e(TAG, "RefreshToken 만료됨 - 로그아웃 처리")
                    tokenManager.clearTokens()
                    return@withLock null
                }

                try {
                    Log.d(TAG, "토큰 갱신 시도 중...")

                    val refreshResponse = authApiProvider.get().refreshToken(
                        RefreshTokenRequest(refreshToken)
                    )

                    if (refreshResponse.isSuccessful) {
                        val tokenResponse = refreshResponse.body()

                        if (tokenResponse != null && !tokenResponse.accessToken.isNullOrBlank()) {
                            // 새 토큰 저장
                            tokenManager.saveTokens(
                                tokenResponse.accessToken,
                                tokenResponse.refreshToken ?: refreshToken
                            )

                            Log.d(TAG, "토큰 갱신 성공")

                            // 실패한 요청을 새 토큰으로 재시도
                            return@withLock response.request.newBuilder()
                                .header("Authorization", "Bearer ${tokenResponse.accessToken}")
                                .build()
                        } else {
                            Log.e(TAG, "토큰 갱신 응답이 유효하지 않음")
                            tokenManager.clearTokens()
                            return@withLock null
                        }
                    } else {
                        Log.e(TAG, "토큰 갱신 실패: ${refreshResponse.code()} - ${refreshResponse.message()}")

                        // 401이나 403이면 RefreshToken이 유효하지 않음
                        if (refreshResponse.code() == 401 || refreshResponse.code() == 403) {
                            Log.e(TAG, "RefreshToken 유효하지 않음 - 로그아웃 처리")
                            tokenManager.clearTokens()
                        }
                        return@withLock null
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "토큰 갱신 중 예외 발생: ${e.message}", e)

                    // 네트워크 오류 등의 경우 토큰을 삭제하지 않음
                    // 단, 인증 관련 오류라면 토큰 삭제
                    return@withLock null
                }
            }
        }
    }
}