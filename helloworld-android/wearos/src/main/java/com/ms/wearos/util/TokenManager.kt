package com.ms.wearos.util

import android.util.Base64
import android.util.Log
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "싸피_TokenManager"
@Singleton
class TokenManager @Inject constructor(
    private val encryptedDataStore: EncryptedDataStore
) {

    // JWT 페이로드 추출
    private fun getPayload(token: String): JSONObject? {
        return try {
            val payload = token.split(".")[1]
            val decodedBytes = Base64.decode(payload, Base64.URL_SAFE)
            JSONObject(String(decodedBytes, Charsets.UTF_8))
        } catch (e: Exception) {
            null
        }
    }

    // 사용자 ID 추출
    fun getUserId(token: String? = null): String? {
        val targetToken = token ?: getAccessToken() ?: return null
        return getPayload(targetToken)?.optString("sub")?.takeIf { it.isNotBlank() }
    }

    // 토큰 저장
    suspend fun saveTokens(accessToken: String, refreshToken: String) {
        encryptedDataStore.saveTokens(accessToken, refreshToken)
    }

    // 액세스 토큰 조회
    fun getAccessToken(): String? = encryptedDataStore.getAccessToken()

    // 리프레시 토큰 조회
    fun getRefreshToken(): String? = encryptedDataStore.getRefreshToken()

    // 토큰 삭제
    suspend fun clearTokens() {
        encryptedDataStore.clearTokens()
    }

    // 현재 로그인된 사용자 ID
    fun getCurrentUserId(): String? = getUserId()

    // 토큰 존재 여부 (간단한 로그인 상태 체크)
    fun hasTokens(): Boolean {
        return !getAccessToken().isNullOrBlank() && !getRefreshToken().isNullOrBlank()
    }
}