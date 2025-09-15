package com.example.helloworld.userserver.member.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name="CoupleJoinResponse")
public record CoupleJoinResponse(
        @Schema(description="커플 ID", example="5") Long coupleId
) {}
