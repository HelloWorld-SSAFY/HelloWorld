package com.ms.wearos.network

import android.util.Log
import com.ms.wearos.util.WearTokenManager
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

private const val TAG = "WearAuthInterceptor"

class AuthInterceptor @Inject constructor(
    private val wearTokenManager: WearTokenManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()

        // 인증이 필요 없는 엔드포인트들 (모바일과 동일)
        val publicEndpoints = listOf(
            "/user/api/auth/google",
            "/user/api/auth/kakao"
        )

        val isPublicEndpoint = publicEndpoints.any { url.contains(it) }

        Log.d(TAG, "WearOS Request URL: $url")
        Log.d(TAG, "Is public endpoint: $isPublicEndpoint")

        val newRequest = if (isPublicEndpoint) {
            Log.d(TAG, "Public endpoint - no token added")
            request
        } else {
            val accessToken = wearTokenManager.getAccessToken()
            Log.d(TAG, "WearOS accessToken 사용됨 = ${accessToken?.take(20)}...")

            if (!accessToken.isNullOrEmpty()) {
                request.newBuilder()
                    .addHeader("Authorization", "Bearer $accessToken")
                    .build()
            } else {
                Log.w(TAG, "No access token available for authenticated request")
                request
            }
        }

        return chain.proceed(newRequest)
    }
}