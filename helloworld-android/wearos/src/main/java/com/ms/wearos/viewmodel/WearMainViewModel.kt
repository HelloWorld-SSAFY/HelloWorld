package com.ms.wearos.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.Wearable
import com.google.gson.Gson
import com.ms.wearos.dto.request.FetalMovementRequest
import com.ms.wearos.dto.request.HealthDataRequest
import com.ms.wearos.dto.request.LaborDataRequest
import com.ms.wearos.dto.response.AiResponse
import com.ms.wearos.dto.response.EmergencyAction
import com.ms.wearos.dto.response.RecommendationCategory
import com.ms.wearos.dto.response.SafeTemplate
import com.ms.wearos.repository.AuthRepository
import com.ms.wearos.repository.WearApiRepository
import com.ms.wearos.util.WearTokenManager
import dagger.hilt.android.internal.Contexts.getApplication
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class WearMainUiState(
    val isAuthenticated: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val tokenAge: Long? = null,
    // AI 응답 관련 상태 추가
    val healthStatus: String = "대기 중",
    val riskLevel: String = "low",
    val recommendations: List<RecommendationCategory> = emptyList(),
    val sessionId: String? = null,
    val cooldownMinutes: Int? = null,
    val isInCooldown: Boolean = false,
    val cooldownSecsLeft: Int = 0,
    val cooldownEndsAt: String? = null,
    val isEmergency: Boolean = false,
    val emergencyAction: EmergencyAction? = null,
    val safeTemplates: List<SafeTemplate> = emptyList()
)

// WearOS 모듈 - 데이터 전송용 DTO
data class WearHealthData(
    val timestamp: String,
    val heartRate: Int,
    val stressIndex: Int,
    val aiResponse: AiResponse
)

private const val TAG = "싸피_WearMainViewModel"

@HiltViewModel
class WearMainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val wearRepository: WearApiRepository,
    private val authRepository: AuthRepository,
    private val tokenManager: WearTokenManager,
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

    /**
     * 폰에 토큰 요청 메시지 전송
     */
    fun requestTokenFromPhone(context: Context) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "폰에 토큰 요청 시작...")
                val messageClient = Wearable.getMessageClient(context)
                val nodeClient = Wearable.getNodeClient(context)

                val nodes = nodeClient.connectedNodes.await()
                Log.d(TAG, "연결된 폰 노드: ${nodes.size}개")

                if (nodes.isEmpty()) {
                    Log.w(TAG, "연결된 폰이 없습니다")
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "핸드폰과 연결되지 않았습니다"
                    )
                    return@launch
                }

                // 모든 연결된 노드에 토큰 요청 메시지 전송
                nodes.forEach { node ->
                    messageClient.sendMessage(
                        node.id,
                        "/request_token", // 토큰 요청 경로
                        ByteArray(0)
                    ).await()

                    Log.d(TAG, "폰(${node.displayName})에 토큰 요청 전송 완료")
                }

            } catch (e: Exception) {
                Log.e(TAG, "토큰 요청 실패", e)
                _uiState.value = _uiState.value.copy(
                    errorMessage = "토큰 요청 실패: ${e.message}"
                )
            }
        }
    }

    /**
     * 앱 시작시 자동으로 토큰 상태 확인 및 요청
     */
    fun initializeTokenState(context: Context) {
        viewModelScope.launch {
            // 현재 토큰 상태 확인
            val hasValidToken = tokenManager.isTokenValid()

            if (!hasValidToken) {
                Log.d(TAG, "유효한 토큰이 없음 - 폰에 토큰 요청")
                requestTokenFromPhone(context)

                // 토큰 요청 후 잠시 대기
                delay(2000)

                // 다시 토큰 상태 확인
                val tokenReceived = tokenManager.isTokenValid()
                if (tokenReceived) {
                    Log.d(TAG, "토큰 수신 완료")
                } else {
                    Log.w(TAG, "토큰 수신 실패 - 사용자에게 안내 필요")
                }
            } else {
                Log.d(TAG, "유효한 토큰 이미 있음")
            }
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

                if (response.isSuccessful && response.body() != null) {
                    val aiResponse = response.body()!!
                    Log.d("WearMainViewModel", "건강 데이터 전송 성공: 심박수=${healthData.heartrate}, 스트레스=${healthData.stress}")
                    Log.d("WearMainViewModel", "AI 응답: mode=${aiResponse.mode}, risk_level=${aiResponse.riskLevel}")

                    // AI 응답에 따른 처리
                    handleAiResponse(aiResponse)

                    // 안드로이드 앱으로 데이터 전송
                    sendAiResponseToAndroid(aiResponse, healthData)

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

    /**
     * AI 응답에 따른 상황별 처리
     */
    private fun handleAiResponse(aiResponse: AiResponse) {
        when {
            aiResponse.isNormal() -> {
                Log.d(TAG, "정상 상태: ${aiResponse.riskLevel}")
                _uiState.value = _uiState.value.copy(
                    healthStatus = "정상",
                    riskLevel = aiResponse.riskLevel ?: "low",
                    errorMessage = null
                )
            }

            aiResponse.isRestrict() -> {
                Log.d(TAG, "제한 모드: 추천 활동 제공")
                aiResponse.recommendation?.let { recommendation ->
                    Log.d(TAG, "세션 ID: ${recommendation.sessionId}")
                    recommendation.categories.forEach { category ->
                        Log.d(TAG, "추천: ${category.category} (순위: ${category.rank})")
                    }

                    _uiState.value = _uiState.value.copy(
                        healthStatus = "주의 필요",
                        riskLevel = aiResponse.riskLevel ?: "high",
                        recommendations = recommendation.categories,
                        sessionId = recommendation.sessionId,
                        cooldownMinutes = aiResponse.cooldownMin,
                        errorMessage = null
                    )
                }
            }

            aiResponse.isCooldown() -> {
                Log.d(TAG, "쿨다운 모드")
                aiResponse.cooldown?.let { cooldown ->
                    Log.d(TAG, "쿨다운 남은 시간: ${cooldown.secsLeft}초")
                    _uiState.value = _uiState.value.copy(
                        healthStatus = "쿨다운 중",
                        riskLevel = aiResponse.riskLevel ?: "high",
                        isInCooldown = true,
                        cooldownSecsLeft = cooldown.secsLeft,
                        cooldownEndsAt = cooldown.endsAt,
                        errorMessage = null
                    )
                }
            }

            aiResponse.isEmergency() -> {
                Log.e(TAG, "응급 상황 발생!")
                aiResponse.action?.let { action ->
                    Log.e(TAG, "응급 액션: ${action.type}")
                }

                _uiState.value = _uiState.value.copy(
                    healthStatus = "응급 상황",
                    riskLevel = aiResponse.riskLevel ?: "critical",
                    isEmergency = true,
                    emergencyAction = aiResponse.action,
                    safeTemplates = aiResponse.safeTemplates ?: emptyList(),
                    errorMessage = null
                )

            }

            else -> {
                Log.w(TAG, "알 수 없는 모드: ${aiResponse.mode}")
            }
        }
    }


    /**
     * 안드로이드 앱으로 AI 응답 데이터 전송
     */
    private fun sendAiResponseToAndroid(aiResponse: AiResponse, originalHealthData: HealthDataRequest) {
        viewModelScope.launch {
            try {
                val messageClient = Wearable.getMessageClient(context)
                val nodeClient = Wearable.getNodeClient(context)

                // 연결된 안드로이드 디바이스 찾기
                val nodes = nodeClient.connectedNodes.await()
                if (nodes.isEmpty()) {
                    Log.w(TAG, "연결된 안드로이드 디바이스 없음")
                    return@launch
                }

                // 전송할 데이터 구성
                val wearHealthData = WearHealthData(
                    timestamp = originalHealthData.date,
                    heartRate = originalHealthData.heartrate,
                    stressIndex = originalHealthData.stress,
                    aiResponse = aiResponse
                )

                // JSON으로 변환
                val gson = Gson()
                val jsonData = gson.toJson(wearHealthData)
                val dataBytes = jsonData.toByteArray()

                // 모든 연결된 노드에 데이터 전송
                nodes.forEach { node ->
                    messageClient.sendMessage(
                        node.id,
                        "/wear_health_data", // 메시지 경로
                        dataBytes
                    ).await()

                    Log.d(TAG, "안드로이드 앱으로 AI 응답 전송 완료: ${node.displayName}")
                    Log.d(TAG, "전송 데이터: mode=${aiResponse.mode}, risk=${aiResponse.riskLevel}")
                }

            } catch (e: Exception) {
                Log.e(TAG, "안드로이드 앱으로 데이터 전송 실패", e)
            }
        }
    }

    fun sendFetalMovementData(recordedAt: String) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)

                // FetalMovementRequest 객체 생성
                val fetalMovementRequest = FetalMovementRequest(recordedAt = recordedAt)

                // 태동 기록 전송
                val response = wearRepository.sendFetalMovement(fetalMovementRequest)

                if (response.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                    )
                    Log.d(TAG, "태동 기록 성공")
                } else {
                    val errorBody = response.errorBody()?.string()

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "태동 기록 실패 (${response.code()}): $errorBody"
                    )
                }

            } catch (e: Exception) {
                Log.e(TAG, "태동 기록 예외", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "태동 기록 중 오류: ${e.message}"
                )
            }
        }
    }

    fun sendLaborData(startTime: String, endTime: String) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)

                // LaborDataRequest 객체 생성
                val laborDataRequest = LaborDataRequest(
                    startTime = startTime,
                    endTime = endTime
                )

                Log.d(TAG, "진통 기록 전송 시도: startTime=$startTime, endTime=$endTime")

                // 진통 기록 전송
                val response = wearRepository.sendLaborData(laborDataRequest)

                if (response.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        successMessage = "진통이 기록되었습니다"
                    )
                    Log.d(TAG, "진통 기록 성공")
                } else {
                    val errorBody = response.errorBody()?.string()
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "진통 기록 실패 (${response.code()}): $errorBody"
                    )
                    Log.e(TAG, "진통 기록 실패: ${response.code()}")
                }

            } catch (e: Exception) {
                Log.e(TAG, "진통 기록 예외", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "진통 기록 중 오류: ${e.message}"
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