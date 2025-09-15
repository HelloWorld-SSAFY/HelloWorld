package com.example.helloworld.userserver.member.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(name = "InviteCodeIssueResponse")
public record InviteCodeIssueResponse(
        @Schema(description = "초대코드", example = "7X9F2QK4") String code,
        @Schema(description = "만료시각(UTC ISO8601)") Instant expiresAt
) {
}
