package com.ms.wearos.util

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WearTokenManager @Inject constructor(
    @ApplicationContext private val context: Context
) : DataClient.OnDataChangedListener {

    companion object {
        private const val TAG = "WearTokenManager"
        private const val TOKEN_PATH = "/jwt_token"
        private const val ACCESS_TOKEN_KEY = "access_token"
        private const val REFRESH_TOKEN_KEY = "refresh_token"
        private const val TIMESTAMP_KEY = "timestamp"

        // 토큰 만료 시간 (24시간)
        private const val TOKEN_EXPIRY_TIME = 24 * 60 * 60 * 1000L
    }

    private val _accessToken = MutableStateFlow<String?>(null)
    val accessToken: StateFlow<String?> = _accessToken.asStateFlow()

    private val _refreshToken = MutableStateFlow<String?>(null)
    val refreshToken: StateFlow<String?> = _refreshToken.asStateFlow()

    private val _isTokenAvailable = MutableStateFlow(false)
    val isTokenAvailable: StateFlow<Boolean> = _isTokenAvailable.asStateFlow()

    private val _tokenTimestamp = MutableStateFlow<Long?>(null)
    val tokenTimestamp: StateFlow<Long?> = _tokenTimestamp.asStateFlow()

    init {
        Wearable.getDataClient(context).addListener(this)
        Log.d(TAG, "WearTokenManager initialized and listener added")

        // 초기화 시 기존 토큰 상태 확인
        checkTokenExpiry()
    }

    override fun onDataChanged(dataEventBuffer: DataEventBuffer) {
        Log.d(TAG, "WearTokenManager - Data changed event received")

        dataEventBuffer.forEach { dataEvent ->
            when (dataEvent.type) {
                DataEvent.TYPE_CHANGED -> {
                    val dataItem = dataEvent.dataItem
                    Log.d(TAG, "WearTokenManager - Data item changed: ${dataItem.uri}")

                    if (dataItem.uri.path == TOKEN_PATH) {
                        val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
                        val accessToken = dataMap.getString(ACCESS_TOKEN_KEY)
                        val refreshToken = dataMap.getString(REFRESH_TOKEN_KEY)
                        val timestamp = dataMap.getLong(TIMESTAMP_KEY)

                        Log.d(TAG, "WearTokenManager - Received tokens from mobile:")
                        Log.d(TAG, "WearTokenManager - Access token: ${accessToken?.take(20)}...")
                        Log.d(TAG, "WearTokenManager - Refresh token: ${refreshToken?.take(20)}...")
                        Log.d(TAG, "WearTokenManager - Timestamp: $timestamp")

                        if (accessToken.isNullOrEmpty()) {
                            // 토큰이 비어있으면 로그아웃 처리
                            clearTokensInternal()
                            Log.d(TAG, "WearTokenManager - Tokens cleared - user logged out")
                        } else {
                            // 토큰 업데이트
                            updateTokensInternal(accessToken, refreshToken, timestamp)
                            Log.d(TAG, "WearTokenManager - Tokens updated successfully")
                        }
                    }
                }
                DataEvent.TYPE_DELETED -> {
                    Log.d(TAG, "WearTokenManager - Data item deleted: ${dataEvent.dataItem.uri}")
                    if (dataEvent.dataItem.uri.path == TOKEN_PATH) {
                        clearTokensInternal()
                    }
                }
            }
        }
        dataEventBuffer.release()
    }

    private fun updateTokensInternal(accessToken: String, refreshToken: String?, timestamp: Long) {
        _accessToken.value = accessToken
        _refreshToken.value = refreshToken
        _tokenTimestamp.value = timestamp
        _isTokenAvailable.value = true

        Log.d(TAG, "WearTokenManager - Internal token update completed")
        Log.d(TAG, "WearTokenManager - Token timestamp: $timestamp")
        Log.d(TAG, "WearTokenManager - Current time: ${System.currentTimeMillis()}")
    }

    private fun clearTokensInternal() {
        _accessToken.value = null
        _refreshToken.value = null
        _tokenTimestamp.value = null
        _isTokenAvailable.value = false

        Log.d(TAG, "WearTokenManager - Internal tokens cleared")
    }

    // 토큰 만료 확인
    private fun checkTokenExpiry() {
        val timestamp = _tokenTimestamp.value
        if (timestamp != null) {
            val currentTime = System.currentTimeMillis()
            val isExpired = (currentTime - timestamp) > TOKEN_EXPIRY_TIME

            if (isExpired) {
                Log.w(TAG, "Token expired, clearing tokens")
                clearTokensInternal()
            } else {
                val timeLeft = TOKEN_EXPIRY_TIME - (currentTime - timestamp)
                val hoursLeft = timeLeft / (1000 * 60 * 60)
                Log.d(TAG, "Token valid for approximately $hoursLeft more hours")
            }
        }
    }

    // 토큰 유효성 검사 (만료 시간 포함)
    fun isTokenValid(): Boolean {
        if (!hasValidToken()) return false

        val timestamp = _tokenTimestamp.value ?: return false
        val currentTime = System.currentTimeMillis()
        val isExpired = (currentTime - timestamp) > TOKEN_EXPIRY_TIME

        if (isExpired) {
            Log.w(TAG, "Token has expired")
            clearTokensInternal()
            return false
        }

        return true
    }

    fun logCurrentTokenStatus() {
        Log.d(TAG, "=== Current Token Status ===")
        Log.d(TAG, "Access Token: ${_accessToken.value?.take(20)}...")
        Log.d(TAG, "Refresh Token: ${_refreshToken.value?.take(20)}...")
        Log.d(TAG, "Is Available: ${_isTokenAvailable.value}")
        Log.d(TAG, "Has Valid Token: ${hasValidToken()}")
        Log.d(TAG, "Is Token Valid (including expiry): ${isTokenValid()}")
        Log.d(TAG, "Token Timestamp: ${_tokenTimestamp.value}")

        val timestamp = _tokenTimestamp.value
        if (timestamp != null) {
            val currentTime = System.currentTimeMillis()
            val ageInHours = (currentTime - timestamp) / (1000 * 60 * 60)
            Log.d(TAG, "Token age: $ageInHours hours")
        }
        Log.d(TAG, "========================")
    }

    fun getAccessToken(): String? = if (isTokenValid()) _accessToken.value else null
    fun getRefreshToken(): String? = if (isTokenValid()) _refreshToken.value else null
    fun hasValidToken(): Boolean = !_accessToken.value.isNullOrEmpty()

    fun clearTokens() {
        clearTokensInternal()
        Log.d(TAG, "Tokens cleared manually")
    }

    // Authorization 헤더용 토큰 반환
    fun getAuthorizationHeader(): String? {
        val token = getAccessToken()
        return if (token != null) "Bearer $token" else null
    }
}