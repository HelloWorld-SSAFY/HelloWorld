package com.example.helloworld.userserver.member.dto.request;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name="CoupleCreateRequest", description="여성(userA) 본인이 커플 생성")
public record CoupleCreateRequest(
        @Schema(description = "임신 주차", example = "12")
        Integer pregnancyWeek,

        @JsonProperty("due_date")
        @Schema(description = "출산 예정일(yyyy-MM-dd)", example = "2026-02-01")
        LocalDate dueDate,

        @JsonProperty("menstrual_date")
        @Schema(description = "최근 생리 시작일(yyyy-MM-dd)", example = "2025-09-01")
        LocalDate menstrualDate,

        @JsonProperty("is_childbirth")
        @Schema(description = "출산 완료 여부", example = "false")
        Boolean isChildbirth
) {}