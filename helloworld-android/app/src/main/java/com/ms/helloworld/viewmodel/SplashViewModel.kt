package com.ms.helloworld.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.ms.helloworld.repository.AuthRepository
import com.ms.helloworld.repository.MomProfileRepository
import com.ms.helloworld.util.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val tokenManager: TokenManager,
    private val authRepository: AuthRepository,
    private val momProfileRepository: MomProfileRepository
) : ViewModel() {

    sealed class UiState {
        data object Loading : UiState()
        data object GoHome : UiState()
        data object GoOnboarding : UiState()
        data object GoLogin : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState = _uiState.asStateFlow()

    companion object {
        private const val TAG = "SplashViewModel"
        private const val MIN_SPLASH_TIME = 2000L
        private const val API_TIMEOUT = 10000L
        private const val TOKEN_PATH = "/jwt_token"
        private const val ACCESS_TOKEN_KEY = "access_token"
        private const val REFRESH_TOKEN_KEY = "refresh_token"
        private const val TIMESTAMP_KEY = "timestamp"
    }

    fun autoLogin(context: Context) {
        viewModelScope.launch {
            val startTime = System.currentTimeMillis()

            try {
                Log.d(TAG, "자동 로그인 시작")

                // 1. 토큰 존재 여부만 간단히 확인
                val hasTokens = tokenManager.hasTokensSuspend()
                Log.d(TAG, "토큰 존재 여부: $hasTokens")

                val result = if (!hasTokens) {
                    Log.d(TAG, "토큰 없음 -> 로그인 화면으로")
                    UiState.GoLogin
                } else {
                    Log.d(TAG, "토큰 있음 -> 커플 정보 확인")
                    checkCoupleStatus(context)
                }

                // 최소 스플래시 시간 보장
                ensureMinimumSplashTime(startTime)
                _uiState.value = result

            } catch (e: Exception) {
                Log.e(TAG, "자동 로그인 중 예외 발생: ${e.message}", e)
                ensureMinimumSplashTime(startTime)
                _uiState.value = UiState.GoLogin
            }
        }
    }

    private suspend fun checkCoupleStatus(context: Context): UiState {
        return try {
            Log.d(TAG, "커플 정보 API 호출")

            // getCoupleDetailInfo() 메서드 사용
            val response = withTimeoutOrNull(API_TIMEOUT) {
                momProfileRepository.getCoupleDetailInfo()
            }

            when {
                response == null -> {
                    Log.w(TAG, "API 호출 타임아웃 -> 토큰 문제로 추정하여 로그인")
                    clearTokensAndGoLogin()
                }

                response.isSuccessful -> {
                    val coupleDetail = response.body()
                    val coupleId = coupleDetail?.couple?.coupleId

                    Log.d(TAG, "API 호출 성공 - 커플 ID: $coupleId")

                    if (coupleId != null && coupleId > 0) {
                        Log.d(TAG, "커플 ID 있음 -> 홈 화면으로")

                        // 워치로 토큰 전송 (선택사항)
                        sendCurrentTokensToWearOS(context)

                        UiState.GoHome
                    } else {
                        Log.d(TAG, "커플 ID 없음 -> 온보딩 화면으로")
                        UiState.GoOnboarding
                    }
                }

                response.code() == 404 -> {
                    // 커플 정보가 아직 없음 (정상적인 상황)
                    Log.d(TAG, "커플 정보 없음 (404) -> 온보딩 화면으로")
                    UiState.GoOnboarding
                }

                response.code() == 401 || response.code() == 403 -> {
                    // 토큰 만료
                    Log.d(TAG, "인증 오류 (${response.code()}) -> 토큰 만료로 간주하여 로그인")
                    clearTokensAndGoLogin()
                }

                else -> {
                    Log.w(TAG, "API 호출 실패 (${response.code()}) -> 네트워크 오류로 추정하여 온보딩")
                    UiState.GoOnboarding
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "커플 정보 확인 중 예외: ${e.message}")

            // 인증 관련 오류면 토큰 삭제 후 로그인
            if (isAuthenticationError(e)) {
                Log.d(TAG, "인증 오류 감지 -> 토큰 삭제 후 로그인")
                clearTokensAndGoLogin()
            } else {
                Log.d(TAG, "네트워크 오류로 추정 -> 온보딩 화면으로")
                UiState.GoOnboarding
            }
        }
    }

    // 토큰 갱신이 필요한 경우 처리 (선택사항)
    private suspend fun attemptTokenRefreshAndRetry(context: Context): UiState {
        return try {
            Log.d(TAG, "토큰 갱신 시도...")

            val refreshToken = tokenManager.getRefreshTokenSuspend()
            if (refreshToken.isNullOrBlank()) {
                Log.d(TAG, "리프레시 토큰 없음 -> 로그인")
                return clearTokensAndGoLogin()
            }

            val tokenResponse = withTimeoutOrNull(API_TIMEOUT) {
                authRepository.refreshToken(refreshToken)
            }

            if (tokenResponse != null) {
                Log.d(TAG, "토큰 갱신 성공 -> 새 토큰 저장 후 재시도")

                val newAccessToken = tokenResponse.accessToken
                val newRefreshToken = tokenResponse.refreshToken ?: refreshToken

                tokenManager.saveTokens(newAccessToken, newRefreshToken)
                sendTokenToWearOS(context, newAccessToken, newRefreshToken)

                // 토큰 갱신 후 다시 커플 상태 확인
                checkCoupleStatus(context)
            } else {
                Log.w(TAG, "토큰 갱신 실패 -> 로그인")
                clearTokensAndGoLogin()
            }
        } catch (e: Exception) {
            Log.e(TAG, "토큰 갱신 중 예외: ${e.message}")
            clearTokensAndGoLogin()
        }
    }

    private suspend fun clearTokensAndGoLogin(): UiState {
        Log.d(TAG, "토큰 삭제 후 로그인으로 이동")
        try {
            tokenManager.clearTokens()
        } catch (e: Exception) {
            Log.e(TAG, "토큰 삭제 중 오류: ${e.message}")
        }
        return UiState.GoLogin
    }

    private fun isAuthenticationError(exception: Exception): Boolean {
        val message = exception.message?.lowercase() ?: ""
        return message.contains("401") ||
                message.contains("403") ||
                message.contains("unauthorized") ||
                message.contains("forbidden")
    }

    private suspend fun ensureMinimumSplashTime(startTime: Long) {
        val elapsedTime = System.currentTimeMillis() - startTime
        val remainingTime = MIN_SPLASH_TIME - elapsedTime

        if (remainingTime > 0) {
            Log.d(TAG, "최소 스플래시 시간 대기: ${remainingTime}ms")
            delay(remainingTime)
        }
    }

    private suspend fun sendCurrentTokensToWearOS(context: Context) {
        try {
            val accessToken = tokenManager.getAccessTokenSuspend()
            val refreshToken = tokenManager.getRefreshTokenSuspend()

            if (!accessToken.isNullOrBlank() && !refreshToken.isNullOrBlank()) {
                sendTokenToWearOS(context, accessToken, refreshToken)
            }
        } catch (e: Exception) {
            Log.e(TAG, "현재 토큰 워치 전송 실패", e)
        }
    }

    private suspend fun sendTokenToWearOS(context: Context, accessToken: String, refreshToken: String) {
        try {
            Log.d(TAG, "워치로 토큰 전송 시작...")
            val dataClient = Wearable.getDataClient(context)
            val nodeClient = Wearable.getNodeClient(context)

            val nodes = nodeClient.connectedNodes.await()
            Log.d(TAG, "연결된 워치 노드: ${nodes.size}개")

            if (nodes.isEmpty()) {
                Log.w(TAG, "연결된 워치가 없습니다")
                return
            }

            val putDataMapRequest = PutDataMapRequest.create(TOKEN_PATH).apply {
                dataMap.putString(ACCESS_TOKEN_KEY, accessToken)
                dataMap.putString(REFRESH_TOKEN_KEY, refreshToken)
                dataMap.putLong(TIMESTAMP_KEY, System.currentTimeMillis())
            }

            val putDataRequest = putDataMapRequest.asPutDataRequest()
            putDataRequest.setUrgent()

            val result = dataClient.putDataItem(putDataRequest).await()
            Log.d(TAG, "워치로 토큰 전송 완료 - URI: ${result.uri}")

        } catch (e: Exception) {
            Log.e(TAG, "워치 토큰 전송 실패", e)
        }
    }
}