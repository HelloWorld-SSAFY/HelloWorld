package com.example.helloworld.healthserver.dto.response;

import java.time.Instant;
import java.util.List;

public record FmListResponse(
        List<Item> records
) {
    public record Item(
            Instant recorded_at, // 해당 '일'의 시작 시각(Asia/Seoul 기준 00:00)을 UTC로 변환한 값
            int total_count
    ){}
}