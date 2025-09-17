package com.example.helloworld.healthserver.dto.response;

import java.time.Instant;

public record CsCreateResponse(
        String session_id,
        Instant start_time,
        Instant end_time,
        Integer duration_sec,
        Integer interval_min
) {}
