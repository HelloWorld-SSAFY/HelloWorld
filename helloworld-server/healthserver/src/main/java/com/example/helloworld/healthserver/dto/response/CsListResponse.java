package com.example.helloworld.healthserver.dto.response;

import java.time.Instant;
import java.util.List;

public record CsListResponse(
        List<CSItem> sessions
) {
    public record CSItem(
            Long id,
            Instant start_time,
            Instant end_time,
            Integer duration_sec,
            Integer interval_min,
            boolean alert_sent
    ){}
}