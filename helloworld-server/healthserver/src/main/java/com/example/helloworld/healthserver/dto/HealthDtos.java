// src/main/java/com/example/helloworld/healthserver/dto/HealthDtos.java
package com.example.helloworld.healthserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class HealthDtos {

    // 생성 요청 (전부 nullable 허용)
    public record CreateRequest(
            Instant date,
            Integer stress,
            Integer heartrate
    ) {}

    // 단건 조회 응답
    public record GetResponse(
            @JsonProperty("health_id") Long healthId,
            Instant date,
            Integer stress,
            Integer heartrate
    ) {}

//    // 목록 응답
//    public record ListResponse(List<Item> records) {
//        public record Item(
//                @JsonProperty("health_id") Long healthId,
//                Instant date,
//                Integer stress,
//                @JsonProperty("sleep_hours") Integer sleepHours,
//                Integer heartrate,
//                Integer steps,
//                @JsonProperty("is_danger") Boolean isDanger
//        ) {}
//    }

//    // 심박수 일별 통계 응답
//    public record HrDailyStatsResponse(List<Item> records) {
//        public record Item(
//                String day,                // "YYYY-MM-DD" (Asia/Seoul 기준)
//                @JsonProperty("avg_heartrate") Double avgHeartrate,
//                @JsonProperty("stddev_heartrate") Double stddevHeartrate,
//                @JsonProperty("count") Long count
//        ) {}
//    }

    public record BucketResponse(
            @JsonProperty("date") LocalDate date,   // 요청한 날짜
            List<Item> records                      // 항상 6개(0-4,4-8,...,20-24)
    ) {
        public record Item(
                @JsonProperty("hour_range") String hourRange,   // "00-04" 등
                @JsonProperty("avg_heartrate") Double avgHeartrate,
                @JsonProperty("stddev_heartrate") Double stddevHeartrate,
                @JsonProperty("count") Long count
        ) {}
    }

    public record StepResponse(
            List<Item> records // 0-12, 0-16, 0-24 (항상 3개)
    ) {
        public record Item(
                @JsonProperty("hour_range") String hourRange, // "00-12", "00-16", "00-24"
                @JsonProperty("avg_steps") Double avgSteps
        ) {}
    }
}
