package com.example.helloworld.healthserver.client;

import com.example.helloworld.healthserver.config.AiServerFeignConfig;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;
import java.util.Map;

// application.yaml의 'ai.server.base-url' 속성을 사용합니다.
@FeignClient(name = "ai-server", url = "${ai.server.base-url}"
        , configuration = AiServerFeignConfig.class // 3. Feign Client에 전용 설정을 적용
)
public interface AiServerClient {

    AnomalyResponse checkTelemetry(
            @RequestHeader("X-App-Token") String appToken,           // ← 앱 토큰 값
            @RequestHeader("X-Internal-Couple-Id") Long coupleId,    // ← 커플 ID
            @RequestBody TelemetryRequest request
    );

    // --- DTOs ---

    /**
     * AI 서버로 보내는 요청 DTO (명세에 맞게 수정)
     */
    record TelemetryRequest(
            @JsonProperty("user_ref") String userRef, // "u" + userId
            @JsonProperty("ts") String timestamp,     // ISO 8601 format
            Metrics metrics
    ) {}

    record Metrics(
            @JsonProperty("hr") Integer heartrate,
            @JsonProperty("stress") Double stress
    ) {}

    /**
     * AI 서버로부터 받는 응답 DTO (명세에 맞게 수정)
     * 모든 필드를 포함하여 어떤 응답이든 받을 수 있도록 합니다.
     */
    record AnomalyResponse(
            boolean ok,
            boolean anomaly,
            @JsonProperty("risk_level") String riskLevel,
            String mode,
            List<String> reasons,
            Recommendation recommendation,
            Action action,
            Cooldown cooldown,
            @JsonProperty("safe_templates") List<Map<String, Object>> safeTemplates
    ) {}

    record Recommendation(
            @JsonProperty("session_id") String sessionId,
            List<Map<String, Object>> categories
    ) {}

    record Action(
            String type,
            @JsonProperty("cooldown_min") Integer cooldownMin
    ) {}

    record Cooldown(
            boolean active,
            @JsonProperty("ends_at") String endsAt,
            @JsonProperty("secs_left") Integer secsLeft
    ) {}
}
