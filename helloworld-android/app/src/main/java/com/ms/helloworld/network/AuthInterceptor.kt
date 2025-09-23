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
            "/user/api/auth/kakao"
        )

        val isPublicEndpoint = publicEndpoints.any { url.contains(it) }

        Log.d(TAG, "Request URL: $url")
        Log.d(TAG, "Is public endpoint: $isPublicEndpoint")

        val newRequest = if (isPublicEndpoint) {
            Log.d(TAG, "Public endpoint - no token added")
            request
        } else {
            val accessToken = runBlocking { tokenManager.getAccessTokenSuspend() }
            Log.d(TAG, "accessToken 길이: ${accessToken?.length ?: 0}")
            Log.d(TAG, "accessToken 비어있음: ${accessToken.isNullOrEmpty()}")

            request.newBuilder().apply {
                if (!accessToken.isNullOrEmpty()) {
                    addHeader("Authorization", "Bearer $accessToken")
                    Log.d(TAG, "Authorization 헤더 추가됨: Bearer ${accessToken.take(20)}...")
                } else {
                    Log.e(TAG, "토큰이 없어서 Authorization 헤더 추가 안됨!")
                }
            }.build()
        }

        return chain.proceed(newRequest)
    }
}