package com.example.helloworld.healthserver.dto;


import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public final class StepsDtos {

    // 등록 요청
    @Schema(name = "StepsCreateRequest")
    public record CreateRequest(
            @Schema(example = "2025-09-23T05:08:24.587Z")
            Instant date,
            @Schema(example = "4200")
            Integer steps,
            @Schema(example = "32.1")
            Double latitude,
            @Schema(example = "31.0")
            Double longitude
    ) {}

    // 등록 응답
    public record CreateResponse(
            @JsonProperty("steps_id") Long stepsId,
            Instant date,
            Integer steps
    ) {}
}