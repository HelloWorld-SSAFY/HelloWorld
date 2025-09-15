package com.example.helloworld.userserver.member.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.NON_NULL) // null 필드는 무시(부분 업데이트)
public record MemberUpdateRequest(
        String nickname,
        Integer age,
        @JsonProperty("menstrual_date") LocalDate menstrualDate,
        @JsonProperty("is_childbirth") Boolean isChildbirth
) {}

