package com.example.helloworld.healthserver.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record CsCreateRequest(
        @NotNull @Schema(description = "수축 시작(UTC)") Instant start_time,
        @NotNull @Schema(description = "수축 종료(UTC)") Instant end_time
) {}