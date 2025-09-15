package com.example.helloworld.userserver.member.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CoupleUpdateRequest(
        Integer pregnancyWeek,
        @JsonProperty("due_date") LocalDate dueDate
) {}
