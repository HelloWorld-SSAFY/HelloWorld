package com.ms.wearos.dto.response

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
}

data class Recommendation(
    @SerializedName("session_id")
    val sessionId: String,

    @SerializedName("categories")
    val categories: List<RecommendationCategory>
)

data class RecommendationCategory(
    @SerializedName("category")
    val category: String, // "BREATHING", "YOGA", etc.

    @SerializedName("rank")
    val rank: Int,

    @SerializedName("reason")
    val reason: String? = null
)

data class CooldownInfo(
    @SerializedName("active")
    val active: Boolean,

    @SerializedName("ends_at")
    val endsAt: String, // ISO 8601 format

    @SerializedName("secs_left")
    val secsLeft: Int
)

data class EmergencyAction(
    @SerializedName("type")
    val type: String, // "EMERGENCY_CONTACT"

    @SerializedName("cooldown_min")
    val cooldownMin: Int
)

data class SafeTemplate(
    @SerializedName("category")
    val category: String, // "BREATHING", etc.

    @SerializedName("title")
    val title: String
)

// 상태별 타입 안전성을 위한 sealed class (선택사항)
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

// AiResponse를 각 모드별 타입으로 변환하는 확장 함수 (선택사항)
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