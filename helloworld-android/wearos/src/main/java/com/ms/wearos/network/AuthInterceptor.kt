package com.ms.wearos.network

import android.util.Log
import com.ms.wearos.util.TokenManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

private const val TAG = "싸피_AuthInterceptor"
class AuthInterceptor @Inject constructor(
    private val tokenManager: TokenManager
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val accessToken = runBlocking { tokenManager.getAccessToken() }
        Log.d(TAG, "accessToken 사용됨 = $accessToken")
        val newRequest = chain.request().newBuilder().apply {
            if (!accessToken.isNullOrEmpty()) {
                addHeader("Authorization", "Bearer $accessToken")
            }
        }.build()
        return chain.proceed(newRequest)
    }
}