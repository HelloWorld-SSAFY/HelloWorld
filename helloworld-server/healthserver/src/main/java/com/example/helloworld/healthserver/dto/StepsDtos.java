package com.example.helloworld.healthserver.dto;


import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;


public final class StepsDtos {

    @Schema(name = "StepsCreateRequest")
    public record CreateRequest(
            @JsonProperty("date")
            @Schema(example = "2025-09-23T05:08:24.587Z")
            @JsonFormat(shape = JsonFormat.Shape.STRING) // ISO-8601 문자열 기대
            Instant date,

            @JsonProperty("steps")
            @Schema(example = "4200")
            Integer steps,

            @JsonProperty("latitude")
            @Schema(example = "32.1")
            Double latitude,

            @JsonProperty("longitude")
            @Schema(example = "31.0")
            Double longitude
    ) {}

    public record CreateResponse(
            @JsonProperty("steps_id") Long stepsId,
            Instant date,
            Integer steps
    ) {}

    public record StepResponse(
            List<Item> records // 0-12, 0-16, 0-24 (항상 3개)
    ) {
        public record Item(
                @JsonProperty("hour_range") String hourRange, // "00-12", "00-16", "00-24"
                @JsonProperty("avg_steps") Double avgSteps
        ) {}
    }

    @Schema(name = "StepsCreateWithAnomalyResponse",
            description = "걸음수 생성 및 AI 이상탐지 통합 응답")
    public record CreateWithAnomalyResponse(
            @JsonProperty("steps_id")
            @Schema(description = "생성된 걸음수 ID")
            Long stepsId,

            @JsonProperty("date")
            @Schema(description = "측정 시간")
            Instant date,

            @JsonProperty("steps")
            @Schema(description = "걸음수")
            Integer steps,

            @JsonProperty("ok")
            @Schema(description = "AI 서버 응답 성공 여부")
            boolean ok,

            @JsonProperty("anomaly")
            @Schema(description = "이상 징후 감지 여부")
            boolean anomaly,

            @JsonProperty("mode")
            @Schema(description = "이상 징후 모드", example = "normal/restrict/emergency")
            String mode
    ) {}
}