package com.example.helloworld.userserver.member.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name="AvatarUrlResponse")
public record AvatarUrlResponse(
        @JsonProperty("image_url")
        @Schema(description="현재 프로필 이미지 URL(없으면 빈 문자열)")
        String imageUrl
) {}
