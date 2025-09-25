package com.ms.helloworld.service

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.google.gson.Gson
import com.ms.helloworld.dto.response.AiResponse
import com.ms.helloworld.dto.response.EmergencyAction
import com.ms.helloworld.dto.response.RecommendationCategory
import com.ms.helloworld.dto.response.SafeTemplate

// Android App 모듈에서 WearOS 메시지 수신
// 파일: com/ms/android/service/WearMessageListenerService.kt

class WearMessageListenerService : WearableListenerService() {

    private val TAG = "안드로이드_WearMessageListener"

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)

        Log.d(TAG, "=== 메시지 수신 ===")
        Log.d(TAG, "경로: ${messageEvent.path}")
        Log.d(TAG, "소스 노드: ${messageEvent.sourceNodeId}")

        when (messageEvent.path) {
            "/wear_health_data" -> {
                handleWearHealthData(messageEvent.data)
            }
            else -> {
                Log.d(TAG, "알 수 없는 메시지 경로: ${messageEvent.path}")
            }
        }
    }

    private fun handleWearHealthData(data: ByteArray) {
        try {
            Log.d(TAG, "=== WearOS 건강 데이터 처리 시작 ===")

            // JSON 데이터 파싱
            val jsonString = String(data, Charsets.UTF_8)
            Log.d(TAG, "수신된 JSON: $jsonString")

            val gson = Gson()
            val wearHealthData = gson.fromJson(jsonString, WearHealthData::class.java)

            Log.d(TAG, "=== 파싱된 데이터 ===")
            Log.d(TAG, "timestamp: ${wearHealthData.timestamp}")
            Log.d(TAG, "heartRate: ${wearHealthData.heartRate}")
            Log.d(TAG, "stressIndex: ${wearHealthData.stressIndex}")

            // AI 응답 상세 로그
            val aiResponse = wearHealthData.aiResponse
            Log.d(TAG, "=== AI 응답 분석 ===")
            Log.d(TAG, "모드: ${aiResponse.mode}")
            Log.d(TAG, "위험도: ${aiResponse.riskLevel}")
            Log.d(TAG, "이상 징후: ${aiResponse.anomaly}")

            when {
                aiResponse.isNormal() -> {
                    Log.d(TAG, ">>> 정상 상태 수신")
                    handleNormalState(aiResponse)
                }

                aiResponse.isRestrict() -> {
                    Log.d(TAG, ">>> 제한 모드 수신")
                    aiResponse.recommendation?.let { rec ->
                        Log.d(TAG, "세션 ID: ${rec.sessionId}")
                        Log.d(TAG, "추천 개수: ${rec.categories.size}")
                        rec.categories.forEach { category ->
                            Log.d(TAG, "  추천: ${category.category} (순위: ${category.rank})")
                        }
                    }
                    handleRestrictState(aiResponse)
                }

                aiResponse.isCooldown() -> {
                    Log.d(TAG, ">>> 쿨다운 모드 수신")
                    aiResponse.cooldown?.let { cooldown ->
                        Log.d(TAG, "쿨다운 남은 시간: ${cooldown.secsLeft}초")
                        Log.d(TAG, "종료 시간: ${cooldown.endsAt}")
                    }
                    handleCooldownState(aiResponse)
                }

                aiResponse.isEmergency() -> {
                    Log.e(TAG, ">>> 응급 상황 수신!")
                    aiResponse.action?.let { action ->
                        Log.e(TAG, "응급 액션 타입: ${action.type}")
                        Log.e(TAG, "쿨다운 시간: ${action.cooldownMin}분")
                    }

                    aiResponse.safeTemplates?.let { templates ->
                        Log.d(TAG, "안전 템플릿 개수: ${templates.size}")
                        templates.forEach { template ->
                            Log.d(TAG, "  템플릿: ${template.category} - ${template.title}")
                        }
                    }
                    handleEmergencyState(aiResponse)
                }
            }

            // UI 업데이트를 위해 ViewModel 또는 Repository에 전달
            updateUIWithWearData(wearHealthData)

        } catch (e: Exception) {
            Log.e(TAG, "WearOS 데이터 처리 실패", e)
        }
    }

    private fun handleNormalState(aiResponse: AiResponse) {
        Log.d(TAG, "정상 상태 처리 로직")
        // 정상 상태 UI 업데이트 로직
    }

    private fun handleRestrictState(aiResponse: AiResponse) {
        Log.d(TAG, "제한 모드 처리 로직")
        // 추천 활동 표시 로직
        aiResponse.recommendation?.let { rec ->
            // UI에 추천 활동 표시
            showRecommendations(rec.categories)
        }
    }

    private fun handleCooldownState(aiResponse: AiResponse) {
        Log.d(TAG, "쿨다운 처리 로직")
        // 쿨다운 타이머 UI 표시
        aiResponse.cooldown?.let { cooldown ->
            showCooldownTimer(cooldown.secsLeft, cooldown.endsAt)
        }
    }

    private fun handleEmergencyState(aiResponse: AiResponse) {
        Log.e(TAG, "응급 상황 처리 로직")
        // 응급 알림 표시
        // 자동 전화/SMS 발송 등
        showEmergencyAlert(aiResponse.action, aiResponse.safeTemplates)
    }

    private fun updateUIWithWearData(wearHealthData: WearHealthData) {
        // ViewModel이나 Repository를 통해 UI 업데이트
        // 예: MainViewModel에 데이터 전달
    }

    private fun showRecommendations(categories: List<RecommendationCategory>) {
        // 추천 활동 UI 표시 로직
    }

    private fun showCooldownTimer(secsLeft: Int, endsAt: String) {
        // 쿨다운 타이머 UI 표시 로직
    }

    private fun showEmergencyAlert(action: EmergencyAction?, templates: List<SafeTemplate>?) {
        // 응급 상황 알림 UI 표시 로직
    }
}

// 데이터 클래스 (WearOS와 동일하게 정의 필요)
data class WearHealthData(
    val timestamp: String,
    val heartRate: Int,
    val stressIndex: Int,
    val aiResponse: AiResponse
)