package com.ms.helloworld.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.ms.helloworld.model.OnboardingStatus
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
        private const val REFRESH_TIMEOUT = 5000L
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

                val refreshToken = tokenManager.getRefreshToken()
                val accessToken = tokenManager.getAccessToken()

                Log.d(TAG, "저장된 accessToken = $accessToken")
                Log.d(TAG, "저장된 refreshToken = $refreshToken")

                val result = if (refreshToken.isNullOrBlank()) {
                    Log.d(TAG, "refreshToken 없음 -> 로그인 화면으로")
                    UiState.GoLogin
                } else {
                    Log.d(TAG, "토큰 갱신 요청 시도 중...")

                    // 토큰 갱신 시도
                    val newTokenResponse = withTimeoutOrNull(REFRESH_TIMEOUT) {
                        authRepository.refreshToken(refreshToken)
                    }

                    if (newTokenResponse != null) {
                        Log.d(TAG, "토큰 갱신 성공")

                        // 새 토큰 변수 정의
                        val newAccessToken = newTokenResponse.accessToken
                        val newRefreshToken = newTokenResponse.refreshToken ?: refreshToken

                        // 새 토큰 저장 (refreshToken이 응답에 없으면 기존 것 유지)
                        tokenManager.saveTokens(
                            accessToken = newTokenResponse.accessToken,
                            refreshToken = newTokenResponse.refreshToken ?: refreshToken
                        )

                        // 자동 로그인 성공 시 워치로 토큰 전송
                        sendTokenToWearOS(context, newAccessToken, newRefreshToken)

                        // 온보딩 상태 체크
                        Log.d(TAG, "온보딩 상태 체크 중...")
                        val onboardingResult = withTimeoutOrNull(REFRESH_TIMEOUT) {
                            momProfileRepository.checkOnboardingStatus()
                        }

                        if (onboardingResult != null) {
                            when (onboardingResult.status) {
                                OnboardingStatus.FULLY_COMPLETED -> {
                                    Log.d(TAG, "온보딩 완료 -> 홈 화면으로")
                                    UiState.GoHome
                                }
                                OnboardingStatus.BASIC_COMPLETED,
                                OnboardingStatus.NOT_STARTED -> {
                                    Log.d(TAG, "온보딩 미완료 -> 온보딩 화면으로")
                                    UiState.GoOnboarding
                                }
                            }
                        } else {
                            Log.e(TAG, "온보딩 상태 체크 타임아웃 -> 온보딩 화면으로")
                            UiState.GoOnboarding
                        }
                    } else {
                        Log.d(TAG, "토큰 갱신 실패 -> 로그인 화면으로")
                        tokenManager.clearTokens()
                        UiState.GoLogin
                    }
                }

                // 최소 스플래시 시간 보장
                val elapsedTime = System.currentTimeMillis() - startTime
                val remainingTime = MIN_SPLASH_TIME - elapsedTime

                if (remainingTime > 0) {
                    Log.d(TAG, "최소 스플래시 시간 대기: ${remainingTime}ms")
                    delay(remainingTime)
                }

                _uiState.value = result

            } catch (e: Exception) {
                Log.e(TAG, "자동 로그인 중 예외 발생: ${e.message}", e)

                // 예외 발생 시에도 최소 스플래시 시간 유지
                val elapsedTime = System.currentTimeMillis() - startTime
                val remainingTime = MIN_SPLASH_TIME - elapsedTime

                if (remainingTime > 0) {
                    delay(remainingTime)
                }

                tokenManager.clearTokens()
                _uiState.value = UiState.GoLogin
            }
        }
    }

    private suspend fun sendTokenToWearOS(context: Context, accessToken: String, refreshToken: String) {
        try {
            Log.d(TAG, "워치로 토큰 전송 시작...")
            val dataClient = Wearable.getDataClient(context)
            val nodeClient = Wearable.getNodeClient(context)

            // 연결된 노드 확인
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