package com.ms.wearos.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ms.wearos.repository.WearApiRepository
import com.ms.wearos.util.WearTokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WearMainUiState(
    val isAuthenticated: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val tokenAge: Long? = null
)

@HiltViewModel
class WearMainViewModel @Inject constructor(
    private val wearRepository: WearApiRepository,
    private val tokenManager: WearTokenManager
) : ViewModel() {

    companion object {
        private const val TAG = "WearMainViewModel"
    }

    private val _uiState = MutableStateFlow(WearMainUiState())
    val uiState: StateFlow<WearMainUiState> = _uiState.asStateFlow()

    init {
        // 토큰 상태 변화 감지
        viewModelScope.launch {
            combine(
                tokenManager.isTokenAvailable,
                tokenManager.tokenTimestamp
            ) { isAvailable, timestamp ->
                val isValid = tokenManager.isTokenValid()
                Log.d(TAG, "Token state changed - Available: $isAvailable, Valid: $isValid")

                _uiState.value = _uiState.value.copy(
                    isAuthenticated = isValid,
                    tokenAge = timestamp
                )
            }.collect { }
        }
    }

    // 토큰 상태 수동 확인
    fun checkTokenStatus(): String {
        val hasToken = tokenManager.hasValidToken()
        val isValid = tokenManager.isTokenValid()
        val accessToken = tokenManager.getAccessToken()

        Log.d(TAG, "Manual token check - Has token: $hasToken, Is valid: $isValid")
        tokenManager.logCurrentTokenStatus()

        return when {
            !hasToken -> "토큰 없음"
            !isValid -> "토큰 만료됨"
            accessToken != null -> "토큰 유효 (${accessToken.take(10)}...)"
            else -> "토큰 상태 불명"
        }
    }

    // 인증이 필요한 작업 실행 전 토큰 검증
    private fun executeWithAuth(
        operation: suspend () -> Unit,
        errorMessage: String = "인증이 필요합니다"
    ) {
        viewModelScope.launch {
            if (!tokenManager.isTokenValid()) {
                Log.w(TAG, "Authentication required for operation")
                _uiState.value = _uiState.value.copy(
                    errorMessage = errorMessage
                )
                return@launch
            }

            try {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
                operation()
                _uiState.value = _uiState.value.copy(isLoading = false)
            } catch (e: Exception) {
                Log.e(TAG, "Operation failed", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "작업 실행 중 오류: ${e.message}"
                )
            }
        }
    }

    fun sendHeartRateData(heartRate: Double) {
        executeWithAuth(
            operation = {
                val authHeader = tokenManager.getAuthorizationHeader()
                if (authHeader != null) {
                    Log.d(TAG, "Sending heart rate data: $heartRate BPM")
                    val response = wearRepository.sendHeartRateData(heartRate, authHeader)

                    if (response.isSuccessful) {
                        Log.d(TAG, "Heart rate data sent successfully")
                        _uiState.value = _uiState.value.copy(
                            successMessage = "심박수 데이터 전송 완료"
                        )
                    } else {
                        Log.e(TAG, "Failed to send heart rate data: ${response.code()}")
                        _uiState.value = _uiState.value.copy(
                            errorMessage = "심박수 데이터 전송 실패"
                        )
                    }
                } else {
                    throw Exception("인증 헤더 생성 실패")
                }
            },
            errorMessage = "심박수 데이터 전송을 위해 모바일 로그인이 필요합니다"
        )
    }

    fun sendFetalMovementData(timestamp: String) {
        executeWithAuth(
            operation = {
                val authHeader = tokenManager.getAuthorizationHeader()
                if (authHeader != null) {
                    Log.d(TAG, "Sending fetal movement data: $timestamp")
                    val response = wearRepository.sendFetalMovementData(timestamp, authHeader)

                    if (response.isSuccessful) {
                        Log.d(TAG, "Fetal movement data sent successfully")
                        _uiState.value = _uiState.value.copy(
                            successMessage = "태동 기록 전송 완료"
                        )
                    } else {
                        Log.e(TAG, "Failed to send fetal movement data: ${response.code()}")
                        _uiState.value = _uiState.value.copy(
                            errorMessage = "태동 기록 전송 실패"
                        )
                    }
                } else {
                    throw Exception("인증 헤더 생성 실패")
                }
            },
            errorMessage = "태동 기록을 위해 모바일 로그인이 필요합니다"
        )
    }

    fun sendLaborData(isActive: Boolean, duration: String?, interval: String?) {
        executeWithAuth(
            operation = {
                val authHeader = tokenManager.getAuthorizationHeader()
                if (authHeader != null) {
                    Log.d(TAG, "Sending labor data - Active: $isActive, Duration: $duration, Interval: $interval")
                    val response = wearRepository.sendLaborData(isActive, duration, interval, authHeader)

                    if (response.isSuccessful) {
                        Log.d(TAG, "Labor data sent successfully")
                        val message = if (isActive) "진통 시작 기록 완료" else "진통 종료 기록 완료"
                        _uiState.value = _uiState.value.copy(
                            successMessage = message
                        )
                    } else {
                        Log.e(TAG, "Failed to send labor data: ${response.code()}")
                        _uiState.value = _uiState.value.copy(
                            errorMessage = "진통 기록 전송 실패"
                        )
                    }
                } else {
                    throw Exception("인증 헤더 생성 실패")
                }
            },
            errorMessage = "진통 기록을 위해 모바일 로그인이 필요합니다"
        )
    }

    // 에러 메시지 초기화
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    // 성공 메시지 초기화
    fun clearSuccess() {
        _uiState.value = _uiState.value.copy(successMessage = null)
    }

    // 토큰 만료 시간 계산 (시간 단위)
    fun getTokenAgeInHours(): Long? {
        val timestamp = tokenManager.tokenTimestamp.value
        return if (timestamp != null) {
            val currentTime = System.currentTimeMillis()
            (currentTime - timestamp) / (1000 * 60 * 60)
        } else null
    }

    // 토큰 만료까지 남은 시간 계산
    fun getTokenRemainingHours(): Long? {
        val ageInHours = getTokenAgeInHours()
        return if (ageInHours != null) {
            maxOf(0, 24 - ageInHours) // 24시간 만료 기준
        } else null
    }
}