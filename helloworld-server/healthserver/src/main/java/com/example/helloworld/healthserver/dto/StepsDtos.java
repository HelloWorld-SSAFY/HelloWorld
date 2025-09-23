package com.example.helloworld.healthserver.dto;


import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;


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
}