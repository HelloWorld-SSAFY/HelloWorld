package com.example.helloworld.healthserver.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record FmCreateRequest(
        @Schema(description = "기록 시각(UTC). 비우면 서버 now()", example = "2025-09-02T15:00:00Z")
        Instant recorded_at,
        @Size(max = 500)
        String notes
) {}