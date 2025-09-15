package com.example.helloworld.userserver.member.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

@Schema(name = "UserRegisterRequest", description = "회원+커플 공유 정보 통합 입력 요청")
public record MemberRegisterRequest(
        @NotBlank @Size(min = 2, max = 20)
        String nickname,

        @NotBlank @Pattern(regexp = "^(female|male)$")
        String gender,

        @NotNull @Min(0) @Max(120)
        Integer age,

        @JsonProperty("menstrual_date")
        LocalDate menstrualDate,

        @JsonProperty("is_childbirth")
        @NotNull
        Boolean isChildbirth,

        @Schema(description = "임신 주차(선택)", example = "12", nullable = true)
        Integer pregnancyWeek,

        @JsonProperty("due_date")
        @Schema(description = "출산 예정일 YYYY-MM-DD (선택)", example = "2026-02-01", nullable = true)
        LocalDate dueDate
) {}

