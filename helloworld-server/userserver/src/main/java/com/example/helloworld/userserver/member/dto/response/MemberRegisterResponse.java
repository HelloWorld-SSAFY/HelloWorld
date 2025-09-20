package com.example.helloworld.userserver.member.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "UserRegisterResponse")
public record MemberRegisterResponse(
        @Schema(description = "등록/갱신 성공 여부", example = "true")
        boolean success,
        @Schema(description = "회원 ID", example = "17")
        Long memberId
) {
    public static MemberRegisterResponse ok(Long memberId) {
        return new MemberRegisterResponse(true, memberId);
    }
}
