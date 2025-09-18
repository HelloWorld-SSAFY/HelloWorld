package com.example.helloworld.userserver.member.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "UserRegisterResponse")
public record MemberRegisterResponse(
        @Schema(description = "등록/갱신 성공 여부", example = "true")
        boolean success,
        @Schema(description = "회원 ID", example = "17")
        Long memberId,
        @Schema(description = "커플 ID(커플이 있을 때만)", example = "5", nullable = true)
        Long coupleId
) {
    public static MemberRegisterResponse ok(Long memberId, Long coupleId) {
        return new MemberRegisterResponse(true, memberId, coupleId);
    }
}
