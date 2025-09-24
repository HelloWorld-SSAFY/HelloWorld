package com.example.helloworld.healthserver.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record CsListResponse(
        List<CSItem> sessions
) {
    public record CSItem(
            Long id,
            LocalDate start_time,
            LocalDate  end_time,
            Integer duration_sec,
            Integer interval_min,
            boolean alert_sent
    ){}
}