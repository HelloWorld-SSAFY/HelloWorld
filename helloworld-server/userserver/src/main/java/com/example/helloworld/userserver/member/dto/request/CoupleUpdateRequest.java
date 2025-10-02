package com.example.helloworld.userserver.member.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name="CoupleUpdateRequest", description="커플 공유 정보 수정(null은 유지)")
public record CoupleUpdateRequest(
        @Schema(description = "임신 주차", example = "13")
        Integer pregnancyWeek,

        @JsonProperty("due_date")
        @Schema(description = "출산 예정일(yyyy-MM-dd)", example = "2026-02-08")
        LocalDate dueDate,

        @JsonProperty("menstrual_date")
        @Schema(description = "최근 생리 시작일(yyyy-MM-dd)", example = "2025-09-08")
        LocalDate menstrualDate,

        @JsonProperty("is_childbirth")
        @Schema(description = "출산 완료 여부", example = "false")
        Boolean isChildbirth
) {}
