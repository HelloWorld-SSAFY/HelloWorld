package com.example.helloworld.userserver.member.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name="CoupleUnlinkResponse")
public record CoupleUnlinkResponse(
        @Schema(description="커플 ID", example="5") Long coupleId,
        @Schema(description="연동해제 성공 여부", example="true") boolean success
) {}
