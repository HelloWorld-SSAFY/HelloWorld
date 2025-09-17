package com.example.helloworld.healthserver.dto.response;

import java.time.Instant;

public record FmCreateResponse(
        String record_id,
        Instant recorded_at
) {}