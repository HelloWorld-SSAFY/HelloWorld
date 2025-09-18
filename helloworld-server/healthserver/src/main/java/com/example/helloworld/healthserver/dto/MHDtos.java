package com.example.helloworld.healthserver.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class MHDtos {

    public record MhGetResponse(
            String record_date,           // YYYY-MM-DD
            BigDecimal weight,
            String blood_pressure,        // "120/80"
            Integer blood_sugar
    ) {}

    public record MhCreateRequest(
            BigDecimal weight,
            Integer max_blood_pressure,
            Integer min_blood_pressure,
            Integer blood_sugar
    ) {}

    public record MhUpdateRequest(
            BigDecimal weight,
            @Schema(example = "108/68", description = "NNN/NNN 형식. null이면 변경 없음")
            String blood_pressure,
            Integer blood_sugar
    ) {}

    public record MhUpdateResponse(
            String maternal_id,   // "mh_%d"
            boolean updated
    ) {}

    public record MhListResponse(
            List<Item> records
    ) {
        public record Item(
                Long maternal_id,
                String record_date,
                BigDecimal weight,
                String blood_pressure,
                Integer blood_sugar,
                OffsetDateTime created_at // UTC
        ) {}

        public record PageMeta(
                int number, int size, long total_elements, int total_pages, boolean has_next
        ) {}
    }
}
