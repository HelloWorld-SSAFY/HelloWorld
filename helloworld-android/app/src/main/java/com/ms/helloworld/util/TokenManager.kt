package com.ms.helloworld.util

import android.util.Base64
import android.util.Log
import com.ms.helloworld.service.TokenMessageListenerService
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TokenManager"

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
            Log.e(TAG, "JWT 페이로드 추출 실패: ${e.message}")
            null
        }
    }

    // 토큰 만료 시간 확인
    fun isTokenExpired(token: String): Boolean {
        return try {
            val payload = getPayload(token)
            val exp = payload?.optLong("exp") ?: return true
            val currentTime = System.currentTimeMillis() / 1000
            val isExpired = currentTime >= exp

            isExpired
        } catch (e: Exception) {
            Log.e(TAG, "토큰 만료 검사 실패: ${e.message}")
            true
        }
    }

    // 사용자 ID 추출
    fun getUserId(token: String? = null): String? {
        val targetToken = token ?: getAccessToken() ?: return null
        return getPayload(targetToken)?.optString("sub")?.takeIf { it.isNotBlank() }
    }

    // 사용자 ID 추출 (suspend 버전)
    suspend fun getUserIdSuspend(token: String? = null): String? {
        val targetToken = token ?: getAccessTokenSuspend() ?: return null
        return getPayload(targetToken)?.optString("sub")?.takeIf { it.isNotBlank() }
    }

    // 토큰 저장
    suspend fun saveTokens(accessToken: String, refreshToken: String) {
        Log.d(TAG, "토큰 저장 시작")
        try {
            // 토큰 유효성 검사
            if (accessToken.isBlank() || refreshToken.isBlank()) {
                Log.e(TAG, "빈 토큰은 저장할 수 없습니다")
                throw IllegalArgumentException("토큰이 비어있습니다")
            }

            // 토큰 만료 시간 로깅
            val accessPayload = getPayload(accessToken)
            val refreshPayload = getPayload(refreshToken)

            accessPayload?.let {
                val exp = it.optLong("exp")
                Log.d(TAG, "AccessToken 만료시간: $exp (${java.util.Date(exp * 1000)})")
            }

            refreshPayload?.let {
                val exp = it.optLong("exp")
                Log.d(TAG, "RefreshToken 만료시간: $exp (${java.util.Date(exp * 1000)})")
            }

            // 기존 토큰과 비교하여 실제로 변경되었는지 확인
            val currentAccessToken = encryptedDataStore.getAccessTokenSuspend()
            val isTokenChanged = currentAccessToken != accessToken

            // 토큰 저장
            encryptedDataStore.saveTokens(accessToken, refreshToken)
            Log.d(TAG, "토큰 저장 완료")

            // 토큰이 실제로 변경된 경우에만 워치에 동기화
            if (isTokenChanged) {
                Log.d(TAG, "토큰 변경 감지 - 워치로 동기화")
                TokenMessageListenerService.getInstance()?.syncTokensToWatch(accessToken, refreshToken)
            } else {
                Log.d(TAG, "토큰 변경 없음 - 워치 동기화 스킵")
            }

            // 저장 후 즉시 확인
            val savedAccess = encryptedDataStore.getAccessTokenSuspend()
            val savedRefresh = encryptedDataStore.getRefreshTokenSuspend()

            Log.d(TAG, "저장 확인 - AccessToken 일치: ${savedAccess == accessToken}")
            Log.d(TAG, "저장 확인 - RefreshToken 일치: ${savedRefresh == refreshToken}")

        } catch (e: Exception) {
            Log.e(TAG, "토큰 저장 실패: ${e.message}", e)
            throw e
        }
    }

    // 액세스 토큰 조회 (기존 동기 버전)
    fun getAccessToken(): String? {
        return try {
            val token = encryptedDataStore.getAccessToken()
            Log.d(TAG, "Access Token: $token")

            if (token != null && isTokenExpired(token)) {
                Log.w(TAG, "액세스 토큰이 만료되었습니다")
            }

            token
        } catch (e: Exception) {
            Log.e(TAG, "액세스 토큰 조회 실패: ${e.message}")
            null
        }
    }

    // 리프레시 토큰 조회 (기존 동기 버전)
    fun getRefreshToken(): String? {
        return try {
            val token = encryptedDataStore.getRefreshToken()
            Log.d(TAG, "Refresh Token: $token")

            if (token != null && isTokenExpired(token)) {
                Log.w(TAG, "리프레시 토큰이 만료되었습니다")
            }

            token
        } catch (e: Exception) {
            Log.e(TAG, "리프레시 토큰 조회 실패: ${e.message}")
            null
        }
    }

    // 액세스 토큰 조회 (suspend 버전)
    suspend fun getAccessTokenSuspend(): String? {
        return try {
            val token = encryptedDataStore.getAccessTokenSuspend()
            Log.d(TAG, "Access Token: $token")

            if (token != null && isTokenExpired(token)) {
                Log.w(TAG, "액세스 토큰이 만료되었습니다(suspend)")
            }

            token
        } catch (e: Exception) {
            Log.e(TAG, "액세스 토큰 조회(suspend) 실패: ${e.message}")
            null
        }
    }

    // 리프레시 토큰 조회 (suspend 버전)
    suspend fun getRefreshTokenSuspend(): String? {
        return try {
            val token = encryptedDataStore.getRefreshTokenSuspend()
            Log.d(TAG, "Refresh Token: $token")

            if (token != null && isTokenExpired(token)) {
                Log.w(TAG, "리프레시 토큰이 만료되었습니다(suspend)")
            }

            token
        } catch (e: Exception) {
            Log.e(TAG, "리프레시 토큰 조회(suspend) 실패: ${e.message}")
            null
        }
    }

    // 토큰 삭제
    suspend fun clearTokens() {
        try {
            // 삭제 전 확인
            val beforeAccess = encryptedDataStore.getAccessTokenSuspend()
            val beforeRefresh = encryptedDataStore.getRefreshTokenSuspend()

            Log.d(TAG, "삭제 전 - AccessToken: $beforeAccess")
            Log.d(TAG, "삭제 전 - RefreshToken: $beforeRefresh")

            // 토큰 삭제
            encryptedDataStore.clearTokens()

            // 워치에도 토큰 삭제 신호 전송
            TokenMessageListenerService.getInstance()?.clearTokensFromWatch()

            // 삭제 후 확인
            val afterAccess = encryptedDataStore.getAccessTokenSuspend()
            val afterRefresh = encryptedDataStore.getRefreshTokenSuspend()

            Log.d(TAG, "삭제 후 - AccessToken: $afterAccess")
            Log.d(TAG, "삭제 후 - RefreshToken: $afterRefresh")
            Log.d(TAG, "토큰 삭제 완료")

        } catch (e: Exception) {
            Log.e(TAG, "토큰 삭제 실패: ${e.message}")
            throw e
        }
    }

    // 현재 로그인된 사용자 ID
    fun getCurrentUserId(): String? = getUserId()

    // 현재 로그인된 사용자 ID (suspend 버전)
    suspend fun getCurrentUserIdSuspend(): String? = getUserIdSuspend()

    // 토큰 존재 여부 (간단한 로그인 상태 체크)
    fun hasTokens(): Boolean {
        return try {
            val hasTokens = !getAccessToken().isNullOrBlank() && !getRefreshToken().isNullOrBlank()
            Log.d(TAG, "토큰 존재 여부: $hasTokens")
            hasTokens
        } catch (e: Exception) {
            Log.e(TAG, "토큰 존재 여부 확인 실패: ${e.message}")
            false
        }
    }

    // 토큰 존재 여부 (suspend 버전)
    suspend fun hasTokensSuspend(): Boolean {
        return try {
            val hasTokens = encryptedDataStore.hasTokens()
            Log.d(TAG, "토큰 존재 여부: $hasTokens")
            hasTokens
        } catch (e: Exception) {
            Log.e(TAG, "토큰 존재 여부(suspend) 확인 실패: ${e.message}")
            false
        }
    }

    // 커플 ID 추출
    fun getCoupleId(token: String? = null): Long? {
        val targetToken = token ?: getAccessToken() ?: return null
        return getPayload(targetToken)?.optLong("coupleId")?.takeIf { it > 0 }
    }

    // 토큰 유효성 검사 (만료 여부 포함)
    suspend fun validateTokens(): TokenValidationResult {
        return try {
            val accessToken = getAccessTokenSuspend()
            val refreshToken = getRefreshTokenSuspend()

            when {
                accessToken.isNullOrBlank() || refreshToken.isNullOrBlank() -> {
                    Log.d(TAG, "토큰이 없음")
                    TokenValidationResult.NO_TOKENS
                }

                isTokenExpired(refreshToken) -> {
                    Log.d(TAG, "리프레시 토큰 만료")
                    TokenValidationResult.REFRESH_TOKEN_EXPIRED
                }

                isTokenExpired(accessToken) -> {
                    Log.d(TAG, "액세스 토큰 만료, 리프레시 토큰 유효")
                    TokenValidationResult.ACCESS_TOKEN_EXPIRED
                }

                else -> {
                    Log.d(TAG, "모든 토큰 유효")
                    TokenValidationResult.VALID
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "토큰 유효성 검사 실패: ${e.message}")
            TokenValidationResult.ERROR
        }
    }

    enum class TokenValidationResult {
        VALID,                    // 모든 토큰 유효
        ACCESS_TOKEN_EXPIRED,     // 액세스 토큰만 만료
        REFRESH_TOKEN_EXPIRED,    // 리프레시 토큰 만료
        NO_TOKENS,               // 토큰 없음
        ERROR                    // 검사 실패
    }
}