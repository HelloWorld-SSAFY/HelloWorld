package com.example.helloworld.healthserver.dto.response;

import java.time.Instant;
import java.util.List;

public record CsListResponse(
        List<CSItem> sessions
) {
    public record CSItem(
            Long id,
            Instant start_time,      // 진통 시작 시간 (정확한 시각 필요)
            Instant end_time,        // 진통 종료 시간 (정확한 시각 필요)
            Integer duration_sec,
            Integer interval_min,
            boolean alert_sent
    ){}
}