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
}