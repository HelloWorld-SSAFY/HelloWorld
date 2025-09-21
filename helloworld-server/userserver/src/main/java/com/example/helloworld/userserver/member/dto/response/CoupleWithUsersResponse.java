package com.example.helloworld.userserver.member.dto.response;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(name = "CoupleWithUsersResponse")
public record CoupleWithUsersResponse(
        @Schema(description = "커플 블록") CoupleBlock couple,
        @Schema(description = "여성(사용자 A) 정보") UserBrief userA,
        @Schema(description = "남성(사용자 B) 정보 (없을 수 있음)")
        @JsonInclude(JsonInclude.Include.ALWAYS) UserBrief userB
) {

    @Schema(name = "CoupleBlock")
    public record CoupleBlock(
            @JsonProperty("couple_id") Long coupleId,
            @JsonProperty("user_a_id") Long userAId,
            @JsonProperty("user_b_id") Long userBId,
            @JsonProperty("pregnancyWeek") Integer pregnancyWeek,
            @JsonProperty("due_date") LocalDate dueDate,
            @JsonProperty("menstrual_date") LocalDate menstrualDate,
            @JsonProperty("is_childbirth") Boolean isChildbirth
    ) {}

    @Schema(name = "UserBrief")
    public record UserBrief(
            Long id,
            String nickname,
            @JsonProperty("image_url") String imageUrl,
            String gender // "FEMALE" | "MALE"
    ) {}
}
