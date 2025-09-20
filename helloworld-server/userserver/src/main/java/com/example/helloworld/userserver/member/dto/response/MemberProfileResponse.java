package com.example.helloworld.userserver.member.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(name = "MemberProfileResponse")
public record MemberProfileResponse(
        @Schema(description = "회원 프로필")
        MemberBlock member
) {
    @Schema(name = "MemberBlock")
    public record MemberBlock(
            Long id,
            @JsonProperty("google_email") String googleEmail,
            String nickname,
            String gender,               // "female" | "male"
            Integer age,
            @JsonProperty("image_url") String imageUrl
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL) // 부분 수정용
    public static record MemberUpdateRequest(
            String nickname,
            Integer age
    ) {}

    }