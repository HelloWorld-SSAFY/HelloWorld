package com.example.helloworld.userserver.member.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;

public record CoupleResponse(
        @JsonProperty("couple_id") Long coupleId,
        @JsonProperty("user_a_id") Long userAId,
        @JsonProperty("user_b_id") Long userBId,
        Integer pregnancyWeek,
        @JsonProperty("due_date") LocalDate dueDate,
        @JsonProperty("menstrual_date") LocalDate menstrualDate,
        @JsonProperty("is_childbirth") Boolean isChildbirth
) {}
