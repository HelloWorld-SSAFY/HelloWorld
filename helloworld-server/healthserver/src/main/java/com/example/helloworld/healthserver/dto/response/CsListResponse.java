package com.example.helloworld.healthserver.dto.response;

import java.time.Instant;
import java.util.List;

public record CsListResponse(
        List<Item> sessions
) {
    public record Item(
            Long id,
            Instant start_time,
            Instant end_time,
            Integer duration_sec,
            Integer interval_min,
            boolean alert_sent
    ){}
}