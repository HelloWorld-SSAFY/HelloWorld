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



public interface AiServerClient {

    @PostMapping("/v1/telemetry")
    AnomalyResponse checkTelemetry(
            @RequestHeader("X-Internal-Couple-Id") Long coupleId, // ← 이거만 파라미터로
            @RequestBody TelemetryRequest request
    );

    @PostMapping("/v1/steps-check")  // 하드코딩으로 변경
    StepsCheckResponse checkSteps(
            @RequestHeader("X-Internal-Couple-Id") Long coupleId,
            @RequestBody StepsCheckRequest request
    );



    // --- DTOs ---
    record TelemetryRequest(
            @JsonProperty("user_ref") String userRef,
            @JsonProperty("ts") String timestamp,
            Metrics metrics
    ) {}

    public record StepsCheckRequest(
            @JsonProperty("ts") String date,
            @JsonProperty("cum_steps") Integer steps,
            @JsonProperty("avg_steps") Integer avgSteps,
            @JsonProperty("lat") Double lat,
            @JsonProperty("lng") Double lng
    ) {}

    // ✔ 최소 응답 필드
    public record StepsCheckResponse(
            boolean ok,
            boolean anomaly,
            String mode
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
