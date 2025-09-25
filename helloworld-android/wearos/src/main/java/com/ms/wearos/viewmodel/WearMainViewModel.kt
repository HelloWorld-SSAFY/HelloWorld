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
                Log.d(TAG, "폰에 토큰 요청 시작")
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
            val userInfo = authRepository.getUserInfo()

            if (userInfo != null) {
                val coupleId = userInfo.couple?.couple_id
                Log.d(TAG, "커플 아이디:  $coupleId")

                if (coupleId != null && coupleId > 0) {
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
                Log.w(TAG, "======== 건강 데이터 전송 시작 ========")

                val healthData = HealthDataRequest(date, stress, 200)
                Log.d(TAG, "전송 데이터: $healthData")

                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

                val response = wearRepository.sendHealthData(healthData)

                Log.d(TAG, "응답 코드: ${response.code()}")

                if (response.errorBody() != null) {
                    val errorString = response.errorBody()?.string()
                }

                if (response.isSuccessful) {
                    val aiResponse = response.body()

                    if (aiResponse != null) {
                        Log.d(TAG, "AI 응답 전체: $aiResponse")

                        // 모드별 필요한 필드만 로그 출력
                        when (aiResponse.mode) {
                            "normal" -> {
                                Log.w(TAG, "Normal 모드")
                                // normal 모드에서는 추가 필드가 거의 없음
                            }

                            "restrict" -> {
                                Log.w(TAG, "Restrict 모드")
                                Log.d(TAG, "reasons: ${aiResponse.reasons}")
                                Log.d(TAG, "newSession: ${aiResponse.newSession}")
                                Log.d(TAG, "cooldownMin: ${aiResponse.cooldownMin}")

                                aiResponse.recommendation?.let { rec ->
                                    Log.w(TAG, "====== 추천 ======")
                                    Log.d(TAG, "categories 개수: ${rec.categories.size}")
                                    rec.categories.forEachIndexed { index, category ->
                                        Log.d(TAG, "  Category[$index]: ${category.category}, rank=${category.rank}, reason=${category.reason}")
                                    }
                                } ?: Log.w(TAG, "⚠️ restrict 모드인데 recommendation이 null")
                            }

                            "cooldown" -> {
                                Log.w(TAG, "Cooldown 모드")
                                Log.d(TAG, "source: ${aiResponse.source}")

                                aiResponse.cooldown?.let { cooldown ->
                                    Log.d(TAG, "====== Cooldown Info ======")
                                    Log.d(TAG, "active: ${cooldown.active}")
                                    Log.d(TAG, "endsAt: ${cooldown.endsAt}")
                                    Log.d(TAG, "secsLeft: ${cooldown.secsLeft}")
                                } ?: Log.w(TAG, "⚠️ cooldown 모드인데 cooldown 정보가 null")
                            }

                            "emergency" -> {
                                Log.w(TAG, "Emergency 모드")

                                aiResponse.action?.let { action ->
                                    Log.d(TAG, "====== Emergency Action ======")
                                    Log.d(TAG, "type: ${action.type}")
                                    Log.d(TAG, "cooldownMin: ${action.cooldownMin}")
                                } ?: Log.w(TAG, "⚠️ emergency 모드인데 action이 null")

                                aiResponse.safeTemplates?.let { templates ->
                                    Log.d(TAG, "--- Safe Templates ---")
                                    Log.d(TAG, "templates 개수: ${templates.size}")
                                    templates.forEachIndexed { index, template ->
                                        Log.d(TAG, "  Template[$index]: category=${template.category}, title=${template.title}")
                                    }
                                } ?: Log.d(TAG, "safeTemplates: null (emergency에서는 있을 수도 없을 수도)")
                            }

                            else -> {
                                Log.w(TAG, "⚠️ 알 수 없는 모드에 대한 전체 필드 덤프:")
                                Log.w(TAG, "reasons: ${aiResponse.reasons}")
                                Log.w(TAG, "recommendation: ${aiResponse.recommendation}")
                                Log.w(TAG, "newSession: ${aiResponse.newSession}")
                                Log.w(TAG, "cooldownMin: ${aiResponse.cooldownMin}")
                                Log.w(TAG, "source: ${aiResponse.source}")
                                Log.w(TAG, "cooldown: ${aiResponse.cooldown}")
                                Log.w(TAG, "action: ${aiResponse.action}")
                                Log.w(TAG, "safeTemplates: ${aiResponse.safeTemplates}")
                            }
                        }

                        // 편의 메서드 결과 로그
                        Log.w(TAG, "=== 편의 메서드 결과 ===")
                        Log.d(TAG, "isNormal(): ${aiResponse.isNormal()}")
                        Log.d(TAG, "isRestrict(): ${aiResponse.isRestrict()}")
                        Log.d(TAG, "isCooldown(): ${aiResponse.isCooldown()}")
                        Log.d(TAG, "isEmergency(): ${aiResponse.isEmergency()}")
                        Log.d(TAG, "hasAnomaly(): ${aiResponse.hasAnomaly()}")
                        Log.d(TAG, "isHighRisk(): ${aiResponse.isHighRisk()}")
                        Log.d(TAG, "isCriticalRisk(): ${aiResponse.isCriticalRisk()}")

                        // 데이터 무결성 검증
                        validateAiResponseData(aiResponse)

                        // AI 응답 처리
                        handleAiResponse(aiResponse)

                        // 안드로이드 앱으로 데이터 전송
                        Log.d(TAG, "안드로이드 앱으로 데이터 전송 시작")
                        sendAiResponseToAndroid(aiResponse, healthData)

                    } else {
                        Log.e(TAG, "응답 바디가 null입니다")
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "서버 응답이 비어있습니다"
                        )
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e(TAG, "건강 데이터 전송 실패")
                    Log.e(TAG, "응답 코드: ${response.code()}")
                    Log.e(TAG, "에러 메시지: ${response.message()}")
                    Log.e(TAG, "에러 바디: $errorBody")

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "건강 데이터 전송 실패 (${response.code()}): ${response.message()}"
                    )
                }

            } catch (e: Exception) {
                Log.e(TAG, "=== 건강 데이터 전송 중 예외 발생 ===")
                Log.e(TAG, "예외 타입: ${e.javaClass.simpleName}")
                Log.e(TAG, "예외 메시지: ${e.message}")
                Log.e(TAG, "예외 원인: ${e.cause}")

                // 네트워크 관련 예외 상세 로그
                when (e) {
                    is java.net.SocketTimeoutException -> {
                        Log.e(TAG, "소켓 타임아웃 - 네트워크 연결 확인 필요")
                    }
                    is java.net.ConnectException -> {
                        Log.e(TAG, "연결 실패 - 서버 상태 확인 필요")
                    }
                    is java.net.UnknownHostException -> {
                        Log.e(TAG, "호스트를 찾을 수 없음 - DNS 또는 인터넷 연결 확인")
                    }
                    is retrofit2.HttpException -> {
                        Log.e(TAG, "HTTP 예외 - 응답 코드: ${e.code()}")
                    }
                    is com.google.gson.JsonSyntaxException -> {
                        Log.e(TAG, "JSON 파싱 예외 - 응답 형식이 예상과 다름")
                    }
                    else -> {
                        Log.e(TAG, "기타 예외")
                    }
                }

                Log.e(TAG, "스택 트레이스:", e)

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "건강 데이터 전송 오류: ${e.message}"
                )
            }
        }
    }

    /**
     * AI 응답에 따른 상황별 처리
     */
    private fun handleAiResponse(aiResponse: AiResponse) {
        Log.w(TAG, "====== AI 응답에 따른 상황별 처리 ======")

        when {
            aiResponse.isNormal() -> {
                Log.w(TAG, "Normal 모드 처리")
                Log.d(TAG, "riskLevel: ${aiResponse.riskLevel}")
                Log.d(TAG, "anomaly: ${aiResponse.anomaly}")

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    healthStatus = "정상",
                    riskLevel = aiResponse.riskLevel ?: "low",
                    recommendations = emptyList(),
                    sessionId = null,
                    cooldownMinutes = null,
                    isInCooldown = false,
                    cooldownSecsLeft = 0,
                    cooldownEndsAt = null,
                    isEmergency = false,
                    emergencyAction = null,
                    safeTemplates = emptyList(),
                    errorMessage = null
                )
            }

            aiResponse.isRestrict() -> {
                Log.w(TAG, "Restrict 모드 처리")
                Log.d(TAG, "reasons: ${aiResponse.reasons}")
                Log.d(TAG, "cooldownMin: ${aiResponse.cooldownMin}")
                Log.d(TAG, "newSession: ${aiResponse.newSession}")

                aiResponse.recommendation?.let { recommendation ->
                    Log.d(TAG, "추천 데이터 처리:")
                    Log.d(TAG, "  추천 카테고리 수: ${recommendation.categories.size}")

                    recommendation.categories.forEach { category ->
                        Log.d(TAG, "  추천 활동: ${category.category} (순위: ${category.rank}, 이유: ${category.reason})")
                    }

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        healthStatus = "주의 필요",
                        riskLevel = aiResponse.riskLevel ?: "high",
                        recommendations = recommendation.categories,
                        sessionId = recommendation.sessionId,
                        cooldownMinutes = aiResponse.cooldownMin,
                        isInCooldown = false,
                        isEmergency = false,
                        emergencyAction = null,
                        errorMessage = null
                    )

                } ?: run {
                    Log.w(TAG, "제한 모드인데 recommendation이 null입니다")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "추천 데이터를 받지 못했습니다"
                    )
                }
            }

            aiResponse.isCooldown() -> {
                Log.w(TAG, "Cooldown 모드 처리")
                Log.d(TAG, "source: ${aiResponse.source}")

                aiResponse.cooldown?.let { cooldown ->
                    Log.d(TAG, "쿨다운 정보:")
                    Log.d(TAG, " 활성 상태: ${cooldown.active}")
                    Log.d(TAG, " 종료 시간: ${cooldown.endsAt}")
                    Log.d(TAG, " 남은 시간(초): ${cooldown.secsLeft}")

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        healthStatus = "쿨다운 중",
                        riskLevel = aiResponse.riskLevel ?: "high",
                        isInCooldown = true,
                        cooldownSecsLeft = cooldown.secsLeft,
                        cooldownEndsAt = cooldown.endsAt,
                        isEmergency = false,
                        recommendations = emptyList(),
                        errorMessage = null
                    )

                } ?: run {
                    Log.w(TAG, "쿨다운 모드인데 cooldown 정보가 null입니다")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "쿨다운 정보를 받지 못했습니다"
                    )
                }
            }

            aiResponse.isEmergency() -> {
                Log.w(TAG, "Emergency 모드 처리")
                Log.e(TAG, "riskLevel: ${aiResponse.riskLevel}")

                aiResponse.action?.let { action ->
                    Log.e(TAG, "응급 액션 정보:")
                    Log.e(TAG, " 타입: ${action.type}")
                    Log.e(TAG, " 쿨다운 시간: ${action.cooldownMin}분")
                } ?: Log.w(TAG, "응급 상황인데 action이 null입니다")

                val templates = aiResponse.safeTemplates ?: emptyList()
                Log.d(TAG, "안전 템플릿 수: ${templates.size}")
                templates.forEach { template ->
                    Log.d(TAG, "  템플릿: ${template.category} - ${template.title}")
                }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    healthStatus = "응급 상황",
                    riskLevel = aiResponse.riskLevel ?: "critical",
                    isEmergency = true,
                    emergencyAction = aiResponse.action,
                    safeTemplates = templates,
                    isInCooldown = false,
                    recommendations = emptyList(),
                    errorMessage = null
                )
            }

            else -> {
                Log.w(TAG, ">>> 알 수 없는 모드 처리 <<<")
                Log.w(TAG, "알 수 없는 모드: ${aiResponse.mode}")
                Log.w(TAG, "전체 응답: $aiResponse")

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "알 수 없는 응답 모드: ${aiResponse.mode}"
                )
            }
        }
    }

    /**
     * AI 응답 데이터의 무결성을 검증하는 함수
     */
    private fun validateAiResponseData(aiResponse: AiResponse) {
        val issues = mutableListOf<String>()

        // 기본 필드 검증
        if (aiResponse.mode.isBlank()) {
            issues.add("mode 필드가 비어있음")
        }

        // 모드별 필수 필드 검증
        when (aiResponse.mode) {
            "restrict" -> {
                if (aiResponse.recommendation == null) {
                    issues.add("restrict 모드인데 recommendation이 null")
                } else {
                    if (aiResponse.recommendation.sessionId.isBlank()) {
                        issues.add("sessionId가 비어있음")
                    }
                    if (aiResponse.recommendation.categories.isEmpty()) {
                        issues.add("추천 카테고리가 비어있음")
                    }
                }
                if (aiResponse.cooldownMin == null || aiResponse.cooldownMin <= 0) {
                    issues.add("restrict 모드인데 cooldownMin이 유효하지 않음: ${aiResponse.cooldownMin}")
                }
            }

            "cooldown" -> {
                if (aiResponse.cooldown == null) {
                    issues.add("cooldown 모드인데 cooldown 정보가 null")
                } else {
                    if (aiResponse.cooldown.secsLeft <= 0) {
                        issues.add("쿨다운 남은 시간이 0 이하: ${aiResponse.cooldown.secsLeft}")
                    }
                    if (aiResponse.cooldown.endsAt.isBlank()) {
                        issues.add("쿨다운 종료 시간이 비어있음")
                    }
                }
                if (aiResponse.source.isNullOrBlank()) {
                    issues.add("cooldown 모드인데 source가 비어있음")
                }
            }

            "emergency" -> {
                if (aiResponse.action == null) {
                    issues.add("emergency 모드인데 action이 null")
                } else {
                    if (aiResponse.action.type.isBlank()) {
                        issues.add("emergency action type이 비어있음")
                    }
                }
                if (aiResponse.riskLevel != "critical") {
                    issues.add("emergency 모드인데 riskLevel이 critical이 아님: ${aiResponse.riskLevel}")
                }
            }

            "normal" -> {
                // normal 모드는 기본 필드만 있으면 됨
                if (aiResponse.anomaly == true) {
                    issues.add("normal 모드인데 anomaly가 true")
                }
            }
        }

        // 검증 결과 로그
        if (issues.isEmpty()) {
        } else {
            Log.w(TAG, "⚠️ 데이터 검증 실패:")
            issues.forEach { issue ->
                Log.w(TAG, "  - $issue")
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