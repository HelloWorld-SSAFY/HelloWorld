package com.example.helloworld.userserver.member.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "CoupleJoinRequest")
public record CoupleJoinRequest(
        @Schema(description = "초대코드", example = "7X9F2QK4") String code
) {
}
