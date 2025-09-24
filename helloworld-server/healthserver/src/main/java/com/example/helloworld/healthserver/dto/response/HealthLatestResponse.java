package com.example.helloworld.healthserver.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(name = "HealthLatestResponse")
public record HealthLatestResponse(
        @JsonProperty("step") StepItem step,           // 없으면 null
        @JsonProperty("heartrate") HrItem heartrate    // 없으면 null
) {
    @Schema(name = "HealthLatestResponse.StepItem")
    public record StepItem(
            @JsonProperty("steps_id") Long stepsId,
            @JsonProperty("date") Instant date,
            @JsonProperty("steps") Integer steps,
            @JsonProperty("latitude") Double latitude,
            @JsonProperty("longitude") Double longitude
    ) {}

    @Schema(name = "HealthLatestResponse.HrItem")
    public record HrItem(
            @JsonProperty("health_id") Long healthId,
            @JsonProperty("date") Instant date,
            @JsonProperty("hr") Integer heartrate,
            @JsonProperty("stress") Double stress
    ) {}
}