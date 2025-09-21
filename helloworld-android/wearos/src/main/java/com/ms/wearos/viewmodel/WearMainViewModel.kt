package com.ms.wearos.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ms.wearos.dto.request.HealthDataRequest
import com.ms.wearos.repository.AuthRepository
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

private const val TAG = "싸피_WearMainViewModel"

@HiltViewModel
class WearMainViewModel @Inject constructor(
    private val wearRepository: WearApiRepository,
    private val authRepository: AuthRepository,
    private val tokenManager: WearTokenManager
) : ViewModel() {

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

    // 커플 ID 조회 및 검증
    suspend fun getCoupleIdIfValid(): Int? {
        return try {
            Log.d(TAG, "Checking couple ID...")
            val userInfo = authRepository.getUserInfo()

            if (userInfo != null) {
                val coupleId = userInfo.couple?.couple_id
                Log.d(TAG, "Couple ID retrieved: $coupleId")

                if (coupleId != null && coupleId > 0) {
                    Log.d(TAG, "Valid couple ID found: $coupleId")
                    coupleId
                } else {
                    Log.w(TAG, "No valid couple ID found")
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "커플 연동이 필요합니다. 모바일 앱에서 커플 등록을 완료해주세요."
                    )
                    null
                }
            } else {
                Log.e(TAG, "Failed to get user info")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "사용자 정보 조회에 실패했습니다."
                )
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting couple ID", e)
            _uiState.value = _uiState.value.copy(
                errorMessage = "커플 정보 조회 중 오류: ${e.message}"
            )
            null
        }
    }

    /**
     * 심박수와 스트레스 지수를 함께 서버로 전송
     */
    fun sendHealthData(date: String, heartRate: Int, stress: Int) {
        viewModelScope.launch {
            try {
                val healthData = HealthDataRequest(date, heartRate, stress)

                Log.d("WearMainViewModel", "건강 데이터 전송 시도: $healthData")

                val response = wearRepository.sendHealthData(healthData)

                if (response.isSuccessful) {
                    Log.d("WearMainViewModel", "건강 데이터 전송 성공: 심박수=${healthData.heartrate}, 스트레스=$healthData.stress")
                } else {
                    Log.e("WearMainViewModel", "건강 데이터 전송 실패: ${response.code()}")
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "건강 데이터 전송 실패: ${response.code()}"
                    )
                }
            } catch (e: Exception) {
                Log.e("WearMainViewModel", "건강 데이터 전송 중 오류 발생", e)
                _uiState.value = _uiState.value.copy(
                    errorMessage = "건강 데이터 전송 오류: ${e.message}"
                )
            }
        }
    }

    // 에러 메시지 초기화
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    // 성공 메시지 초기화
    fun clearSuccess() {
        _uiState.value = _uiState.value.copy(successMessage = null)
    }
}