package com.ms.helloworld.network

import android.util.Log
import com.ms.helloworld.util.TokenManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

private const val TAG = "싸피_AuthInterceptor"

class AuthInterceptor @Inject constructor(
    private val tokenManager: TokenManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()

        // 인증이 필요 없는 엔드포인트들
        val publicEndpoints = listOf(
            "/user/api/auth/google",
            "/user/api/auth/kakao",
            "/user/api/auth/refresh" // refresh 엔드포인트도 제외
        )

        val isPublicEndpoint = publicEndpoints.any { url.contains(it) }

        Log.d(TAG, "Request URL: $url, isPublic: $isPublicEndpoint")

        val newRequest = if (isPublicEndpoint) {
            request
        } else {
            // 토큰 유효성 검사
            runBlocking {
                val validationResult = tokenManager.validateTokens()

                when (validationResult) {
                    TokenManager.TokenValidationResult.VALID -> {
                        val accessToken = tokenManager.getAccessTokenSuspend()
                        if (!accessToken.isNullOrEmpty()) {
                            Log.d(TAG, "유효한 AccessToken으로 요청")
                            request.newBuilder()
                                .addHeader("Authorization", "Bearer $accessToken")
                                .build()
                        } else {
                            Log.w(TAG, "AccessToken이 null - 헤더 추가 안함")
                            request
                        }
                    }

                    TokenManager.TokenValidationResult.ACCESS_TOKEN_EXPIRED -> {
                        // AccessToken만 만료된 경우 - Authenticator가 처리할 수 있도록 일단 진행
                        val accessToken = tokenManager.getAccessTokenSuspend()
                        Log.d(TAG, "AccessToken 만료됨 - Authenticator가 갱신 처리 예정")
                        request.newBuilder()
                            .addHeader("Authorization", "Bearer $accessToken")
                            .build()
                    }

                    TokenManager.TokenValidationResult.REFRESH_TOKEN_EXPIRED,
                    TokenManager.TokenValidationResult.NO_TOKENS -> {
                        Log.w(TAG, "토큰 없음 또는 RefreshToken 만료 - Authorization 헤더 추가 안함")
                        request
                    }

                    TokenManager.TokenValidationResult.ERROR -> {
                        Log.e(TAG, "토큰 검증 오류")
                        request
                    }
                }
            }
        }

        return chain.proceed(newRequest)
    }
}