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
            Double stress,
            Integer heartrate
    ) {}

    // 단건 조회 응답
    public record GetResponse(
            @JsonProperty("health_id") Long healthId,
            Instant date,
            Double stress,
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




    /**
     * 전체 커플의 일별 통계 응답
     */
    public record GlobalDailyStatsResponse(
            List<StatsRow> stats
    ) {}

    /**
     * 통계 데이터의 한 행을 나타내는 레코드
     */
    public record StatsRow(
            @JsonProperty("user_ref") String userRef,       // "c" + coupleId
            @JsonProperty("as_of") LocalDate asOf,          // 요청된 날짜 (YYYY-MM-DD)
            String metric,                                  // "hr" 또는 "stress"
            String stat,                                    // "avg" 또는 "stddev"
            @JsonProperty("v_0_4") Double v0_4,
            @JsonProperty("v_4_8") Double v4_8,
            @JsonProperty("v_8_12") Double v8_12,
            @JsonProperty("v_12_16") Double v12_16,
            @JsonProperty("v_16_20") Double v16_20,
            @JsonProperty("v_20_24") Double v20_24
    ) {}




}
