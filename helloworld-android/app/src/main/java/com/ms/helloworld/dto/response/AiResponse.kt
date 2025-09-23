package com.ms.helloworld.dto.response

import androidx.compose.ui.graphics.Color

import com.google.gson.annotations.SerializedName

data class AiResponse(
    @SerializedName("ok")
    val ok: Boolean,

    @SerializedName("anomaly")
    val anomaly: Boolean? = null,

    @SerializedName("risk_level")
    val riskLevel: String? = null, // "low", "high", "critical"

    @SerializedName("mode")
    val mode: String, // "normal", "restrict", "cooldown", "emergency"

    @SerializedName("reasons")
    val reasons: List<String>? = null,

    @SerializedName("recommendation")
    val recommendation: Recommendation? = null,

    @SerializedName("new_session")
    val newSession: Boolean? = null,

    @SerializedName("cooldown_min")
    val cooldownMin: Int? = null,

    @SerializedName("source")
    val source: String? = null, // cooldown 모드에서 사용

    @SerializedName("cooldown")
    val cooldown: CooldownInfo? = null,

    @SerializedName("action")
    val action: EmergencyAction? = null,

    @SerializedName("safe_templates")
    val safeTemplates: List<SafeTemplate>? = null
) {
    // 편의 메서드들
    fun isNormal(): Boolean = mode == "normal"
    fun isRestrict(): Boolean = mode == "restrict"
    fun isCooldown(): Boolean = mode == "cooldown"
    fun isEmergency(): Boolean = mode == "emergency"

    fun hasAnomaly(): Boolean = anomaly == true
    fun isHighRisk(): Boolean = riskLevel == "high" || riskLevel == "critical"
    fun isCriticalRisk(): Boolean = riskLevel == "critical"
    fun isLowRisk(): Boolean = riskLevel == "low"
}

data class Recommendation(
    @SerializedName("session_id")
    val sessionId: String,

    @SerializedName("categories")
    val categories: List<RecommendationCategory>
)

data class RecommendationCategory(
    @SerializedName("category")
    val category: String, // "BREATHING", "YOGA", "MUSIC", "MEDITATION", etc.

    @SerializedName("rank")
    val rank: Int,

    @SerializedName("reason")
    val reason: String? = null,

    @SerializedName("title")
    val title: String? = null,

    @SerializedName("description")
    val description: String? = null
) {
    // 카테고리별 편의 메서드
    fun isBreathing(): Boolean = category == "BREATHING"
    fun isYoga(): Boolean = category == "YOGA"
    fun isMusic(): Boolean = category == "MUSIC"
    fun isMeditation(): Boolean = category == "MEDITATION"
    fun isWalk(): Boolean = category == "WALK"
}

data class CooldownInfo(
    @SerializedName("active")
    val active: Boolean,

    @SerializedName("ends_at")
    val endsAt: String, // ISO 8601 format

    @SerializedName("secs_left")
    val secsLeft: Int,

    @SerializedName("reason")
    val reason: String? = null
) {
    // 남은 시간을 분:초 형태로 반환
    fun getFormattedTimeLeft(): String {
        val minutes = secsLeft / 60
        val seconds = secsLeft % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    // 남은 시간을 분 단위로 반환
    fun getMinutesLeft(): Int = (secsLeft + 59) / 60 // 올림 처리
}

data class EmergencyAction(
    @SerializedName("type")
    val type: String, // "EMERGENCY_CONTACT", "HOSPITAL_CONTACT", etc.

    @SerializedName("cooldown_min")
    val cooldownMin: Int,

    @SerializedName("message")
    val message: String? = null,

    @SerializedName("contact_info")
    val contactInfo: String? = null
) {
    fun isEmergencyContact(): Boolean = type == "EMERGENCY_CONTACT"
    fun isHospitalContact(): Boolean = type == "HOSPITAL_CONTACT"
}

data class SafeTemplate(
    @SerializedName("category")
    val category: String, // "BREATHING", "MEDITATION", etc.

    @SerializedName("title")
    val title: String,

    @SerializedName("description")
    val description: String? = null,

    @SerializedName("duration_min")
    val durationMin: Int? = null,

    @SerializedName("instructions")
    val instructions: List<String>? = null
)

// 상태별 타입 안전성을 위한 sealed class
sealed class AiResponseMode {
    data class Normal(
        val ok: Boolean,
        val anomaly: Boolean,
        val riskLevel: String
    ) : AiResponseMode()

    data class Restrict(
        val ok: Boolean,
        val anomaly: Boolean,
        val riskLevel: String,
        val reasons: List<String>,
        val recommendation: Recommendation,
        val newSession: Boolean,
        val cooldownMin: Int
    ) : AiResponseMode()

    data class Cooldown(
        val ok: Boolean,
        val anomaly: Boolean,
        val riskLevel: String,
        val source: String,
        val cooldownInfo: CooldownInfo
    ) : AiResponseMode()

    data class Emergency(
        val ok: Boolean,
        val anomaly: Boolean,
        val riskLevel: String,
        val action: EmergencyAction,
        val safeTemplates: List<SafeTemplate>
    ) : AiResponseMode()
}

// AiResponse를 각 모드별 타입으로 변환하는 확장 함수
fun AiResponse.toTypedMode(): AiResponseMode? {
    return when (mode) {
        "normal" -> AiResponseMode.Normal(
            ok = ok,
            anomaly = anomaly ?: false,
            riskLevel = riskLevel ?: "low"
        )

        "restrict" -> {
            val rec = recommendation ?: return null
            AiResponseMode.Restrict(
                ok = ok,
                anomaly = anomaly ?: true,
                riskLevel = riskLevel ?: "high",
                reasons = reasons ?: emptyList(),
                recommendation = rec,
                newSession = newSession ?: false,
                cooldownMin = cooldownMin ?: 0
            )
        }

        "cooldown" -> {
            val cooldownInfo = cooldown ?: return null
            AiResponseMode.Cooldown(
                ok = ok,
                anomaly = anomaly ?: true,
                riskLevel = riskLevel ?: "high",
                source = source ?: "",
                cooldownInfo = cooldownInfo
            )
        }

        "emergency" -> {
            val emergencyAction = action ?: return null
            val templates = safeTemplates ?: emptyList()
            AiResponseMode.Emergency(
                ok = ok,
                anomaly = anomaly ?: true,
                riskLevel = riskLevel ?: "critical",
                action = emergencyAction,
                safeTemplates = templates
            )
        }

        else -> null
    }
}

// 예시 사용을 위한 샘플 데이터 생성 함수들
object AiResponseSamples {

    fun createNormalResponse(): AiResponse {
        return AiResponse(
            ok = true,
            anomaly = false,
            riskLevel = "low",
            mode = "normal"
        )
    }

    fun createRestrictResponse(): AiResponse {
        return AiResponse(
            ok = false,
            anomaly = true,
            riskLevel = "high",
            mode = "restrict",
            reasons = listOf("높은 스트레스 지수 감지", "비정상적인 심박수 패턴"),
            recommendation = Recommendation(
                sessionId = "session_123",
                categories = listOf(
                    RecommendationCategory(
                        category = "BREATHING",
                        rank = 1,
                        reason = "스트레스 완화",
                        title = "심호흡 운동",
                        description = "4-7-8 호흡법으로 마음을 진정시켜보세요"
                    ),
                    RecommendationCategory(
                        category = "MUSIC",
                        rank = 2,
                        reason = "마음 안정",
                        title = "클래식 음악",
                        description = "차분한 클래식 음악으로 휴식을 취해보세요"
                    )
                )
            ),
            newSession = true,
            cooldownMin = 30
        )
    }

    fun createCooldownResponse(): AiResponse {
        return AiResponse(
            ok = false,
            anomaly = true,
            riskLevel = "high",
            mode = "cooldown",
            source = "previous_session",
            cooldown = CooldownInfo(
                active = true,
                endsAt = "2024-01-15T14:30:00Z",
                secsLeft = 1800, // 30분
                reason = "이전 세션에서 높은 스트레스 감지"
            )
        )
    }

    fun createEmergencyResponse(): AiResponse {
        return AiResponse(
            ok = false,
            anomaly = true,
            riskLevel = "critical",
            mode = "emergency",
            action = EmergencyAction(
                type = "EMERGENCY_CONTACT",
                cooldownMin = 60,
                message = "즉시 의료진에게 연락하세요",
                contactInfo = "119"
            ),
            safeTemplates = listOf(
                SafeTemplate(
                    category = "BREATHING",
                    title = "응급 호흡법",
                    description = "천천히 깊게 숨을 쉬세요",
                    durationMin = 5,
                    instructions = listOf(
                        "편안한 자세로 앉으세요",
                        "4초 동안 천천히 숨을 들이마시세요",
                        "7초 동안 숨을 참으세요",
                        "8초 동안 천천히 내쉬세요"
                    )
                )
            )
        )
    }
}
// 스트레스 지수 레벨 enum
enum class StressLevel(val displayName: String, val color: Color) {
    STABLE("안정", Color(0xFF4CAF50)),    // 초록색
    CAUTION("주의", Color(0xFFFF9800)),   // 주황색
    WARNING("경고", Color(0xFFFF5722)),   // 빨간-주황색
    DANGER("위험", Color(0xFFD32F2F))     // 빨간색
}