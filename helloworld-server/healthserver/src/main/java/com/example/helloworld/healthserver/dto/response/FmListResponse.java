package com.example.helloworld.healthserver.dto.response;

import java.time.LocalDate;
import java.util.List;

public record FmListResponse(
        List<FmListItem> records
) {
    public record FmListItem(
            LocalDate recorded_at,
            int total_count
    ){}
}