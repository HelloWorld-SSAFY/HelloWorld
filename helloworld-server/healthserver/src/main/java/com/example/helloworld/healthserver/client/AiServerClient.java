package com.example.helloworld.healthserver.client;

import com.example.helloworld.healthserver.config.AiServerFeignConfig;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@FeignClient(
        name = "ai-server",
        url = "${ai.server.base-url}",
        configuration = AiServerFeignConfig.class
)
@RequestMapping("${ai.telemetry-path:/v1/telemetry}") // ← 경로 프로퍼티는 여기
public interface AiServerClient {

    @PostMapping
    AnomalyResponse checkTelemetry(
            @RequestHeader("X-Internal-Couple-Id") Long coupleId, // ← 이거만 파라미터로
            @RequestBody TelemetryRequest request
    );

    // --- DTOs ---
    record TelemetryRequest(
            @JsonProperty("user_ref") String userRef,
            @JsonProperty("ts") String timestamp,
            Metrics metrics
    ) {}

    record Metrics(
            @JsonProperty("hr") Integer heartrate,
            @JsonProperty("stress") Double stress
    ) {}

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
