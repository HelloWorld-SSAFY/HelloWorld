package com.example.helloworld.userserver.member.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(name = "MemberProfileResponse")
public record MemberProfileResponse(
        @Schema(description = "회원 프로필") MemberBlock member,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "커플 정보(없으면 null)") CoupleBlock couple
) {
    @Schema(name = "MemberBlock")
    public record MemberBlock(
            Long id,
            @JsonProperty("google_email") String googleEmail,
            String nickname,
            String gender,               // "female" | "male"
            Integer age,
            @JsonProperty("menstrual_date") LocalDate menstrualDate,
            @JsonProperty("is_childbirth") Boolean isChildbirth,
            @JsonProperty("image_url") String imageUrl
    ) {}

    @Schema(name = "CoupleBlock")
    public record CoupleBlock(
            @JsonProperty("couple_id") Long coupleId,
            @JsonProperty("user_a_id") Long userAId,
            @JsonProperty("user_b_id") Long userBId,
            @JsonProperty("pregnancy_week") Integer pregnancyWeek,
            @JsonProperty("due_date") LocalDate dueDate     // YYYY-MM-DD로 변환해 내려줌
    ) {}

    @Schema(name = "UserBrief")
    public record UserBrief(
            Long id,
            String nickname,
            String gender,
            @JsonProperty("image_url") String imageUrl
    ) {
        public static UserBrief of(com.example.helloworld.userserver.member.entity.Member m) {
            if (m == null) return null;
            return new UserBrief(
                    m.getId(),
                    m.getNickname(),
                    m.getGender() != null ? m.getGender().name().toLowerCase() : null,
                    m.getImageUrl()
            );
        }
    }

    // 헬퍼: Timestamp -> LocalDate
    public static LocalDate toLocalDate(Timestamp ts) {
        if (ts == null) return null;
        LocalDateTime ldt = ts.toLocalDateTime();
        return ldt.toLocalDate();
    }

    @JsonInclude(JsonInclude.Include.NON_NULL) // null 필드는 무시(부분 업데이트)
    public static record MemberUpdateRequest(
            String nickname,
            Integer age,
            @JsonProperty("menstrual_date") LocalDate menstrualDate,
            @JsonProperty("is_childbirth") Boolean isChildbirth
    ) {}
}
